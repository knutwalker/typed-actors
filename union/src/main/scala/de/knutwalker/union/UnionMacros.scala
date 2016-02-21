/*
 * Copyright 2015 – 2016 Paul Horn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.knutwalker.union

import scala.language.experimental.macros
import scala.reflect.macros.blackbox


class UnionMacros(val c: blackbox.Context) extends PatternDefs {
  import Union.Completeness
  import c.universe._

  case class MatchResult(cse: CaseDef, ut: c.Type, pt: AndPos[PatternType])

  final val AnyTpe     = typeOf[Any]
  final val NothingTpe = typeOf[Nothing]
  final val TheUnion   = typeOf[|[_, _]]
  final val TheNull    = Literal(Constant(null))
  def ReturnNull[A]: c.Expr[A] = c.Expr[A](TheNull)

  def isPartOfImpl[A, U <: Union](implicit A: c.WeakTypeTag[A], U: c.WeakTypeTag[U]): c.Expr[isPartOf[A, U]] = {
    val unionTypes = expandUnion(U.tpe, TheUnion)
    val partTypes = expandUnion(A.tpe, TheUnion)
    val msgs = containsOf(partTypes, unionTypes)
    msgs.foreach(fail)
    ReturnNull
  }

  def checkPF[U, T, SU, All <: Completeness, Sub <: Completeness](msg: c.Expr[PartialFunction[Any, T]])(implicit U: c.WeakTypeTag[U], T: c.WeakTypeTag[T], SU: c.WeakTypeTag[SU], All: c.WeakTypeTag[All], Sub: c.WeakTypeTag[Sub]): Tree = {
    val unionTypes = expandUnion(U.tpe, TheUnion)

    def checkPatternType(cse: CaseDef)(pt: AndPos[PatternType]) = {
      val matches = for (ut ← unionTypes if pt.x.matches(ut)) yield MatchResult(cse, ut, pt)
      if (matches.isEmpty) {
        c.error(pt.pos, s"Pattern involving [${pt.x.pt}] is not covered by union ${typeMsg(unionTypes)}.")
      }
      matches
    }

    val rawCases = msg.tree match {
      case q"{ case ..$cases }" ⇒ cases.toList
      case x                    ⇒ c.abort(x.pos, "Unioned must be used with a partial function literal syntax.")
    }

    val patterns = rawCases flatMap {
      case cse@CaseDef(pattern, guard, expr) ⇒ patternTypes(pattern).flatMap(checkPatternType(cse))
      case x                                 ⇒ c.error(x.pos.asInstanceOf[c.Position], unknownMsg); Nil
    }

    if (isEffectively[Completeness.Total](All.tpe)) {
      checkTotalCoverage(unionTypes, patterns)
    }

    if (isEffectively[Completeness.Total](Sub.tpe)) {
      if (SU.tpe =:= NothingTpe) {
        checkExhaustiveness(patterns.groupBy(_.ut), _ ⇒ true)
      } else {
        val subUnion = expandUnion(SU.tpe, TheUnion)
        val diff = subUnion.diff(unionTypes)
        if (diff.nonEmpty) {
          c.abort(
            c.enclosingPosition,
            s"Can't check exhaustiveness for ${typeMsg(diff)} as they do not belong to ${typeMsg(unionTypes)}"
          )
        }
        val shouldCheck = membership(subUnion)
        if (patterns.exists(p ⇒ shouldCheck(p.ut))) {
          checkExhaustiveness(patterns.groupBy(_.ut), shouldCheck)
        }
      }
    }

    msg.tree
  }

  private val unknownMsg       =
    "Pattern is not recognized. If you think this is a bug, please file an " +
    "issue at Github."
  private val nonExhaustiveMsg =
    "The patterns for %1$s are not exhaustive; It would fail on the following " +
    "%2$s. Note that this check may be a false negative. If that is the case, " +
    "workaround by adding a catch-all pattern like `case _: %1$s` to your " +
    "cases and please file an issue on Github."
  private val si7046           =
    "You are likely affected by SI-7046 <https://issues.scala-lang.org/browse/SI-7046> " +
    "or a related bug. This means, that the exhautiveness checks could not be executed " +
    "and as a result, the succeeding compilation can be a false positive. As a workaround, " +
    "try to move the definition of %1$s to a separate compile unit."

  private def isTypePartOf(tpe: Type, union: List[Type], variant: Boolean = true): Boolean =
    if (tpe =:= NothingTpe || tpe == NoType) true
    else union.exists(ut ⇒ (ut == NoType) || typeMatch(tpe, ut, variant))

  private def containsOf(left: List[Type], right: List[Type]): Option[String] = {
    val notInRight = left.filterNot(e ⇒ isTypePartOf(e, right))
    if (notInRight.isEmpty) None
    else {
      val tgts = typeMsg(right)
      if (notInRight.tail.isEmpty) Some(s"${notInRight.head} is not in $tgts.")
      else Some(notInRight.map(d ⇒ s"$d is not in $tgts.").mkString("\n", "\n", "\n"))
    }
  }

  private def checkTotalCoverage[Sub <: Completeness, All <: Completeness, T, U](unionTypes: List[c.universe.Type], patterns: List[MatchResult]): Unit = {
    val matched = patterns.map(_.ut)
    val unmatched = unionTypes.filterNot(membership(matched))
    if (unmatched.nonEmpty) {
      val prefix = plural(unmatched, s"${unmatched.head}", s"these types: ${typeMsg(unmatched)}")
      val msg = s"The partial function fails to match on $prefix."
      c.error(c.enclosingPosition, msg)
    }
  }

  private def checkExhaustiveness(allPatterns: Map[Type, List[MatchResult]], shouldCheck: Type ⇒ Boolean): Unit = {
    val typer = g.patmat.global.analyzer.newTyper(g.analyzer.rootContext(g.NoCompilationUnit, g.EmptyTree))
    val translator = new g.patmat.OptimizingMatchTranslator(typer)
    val copier = newStrictTreeCopier // https://youtu.be/gqSBM_kLJaI?t=21m35s
    val asg = (t: Tree) ⇒ t.asInstanceOf[g.Tree]

    def hasCatchAll(patterns: List[MatchResult]) =
      patterns.exists(_.pt.x.expr.forall(_ == NoType))

    def bug5464(pat: MatchResult): Tree = {
      // Workaround for <https://issues.scala-lang.org/browse/SI-5464>
      // And they said it can't be done. In their faces, ha!
      // Many thanks to @Chasmo90/@MartinSeeler for the hint
      c.internal.transform(pat.cse.pat)((t, tapi) ⇒ t match {
        case Bind(name, body) ⇒ copier.Bind(t, c.freshName(name), tapi.default(body))
        case otherwise        ⇒ tapi.default(t)
      })
    }

    def bug7755(base: Type): Symbol = {
      val baseSym = base.typeSymbol
      if (baseSym != NoSymbol) {
        // Workaround for <https://issues.scala-lang.org/browse/SI-7755>
        baseSym.typeSignature
      }
      baseSym
    }

    def bug7406(base: g.Type, knownSubtypes: List[g.Type], sym: Symbol, exprs: List[Type])(f: ⇒ List[String]) = {
      // Workaround for <https://issues.scala-lang.org/browse/SI-7046>, not fixingly
      // but at least we can report that there might be an occurence of this bug.
      if (knownSubtypes.isEmpty && sym.isClass && sym.asClass.isSealed) {
        if (!exprs.contains(NoType)) {
          c.echo(sym.pos, String.format(si7046, base))
        }
        Nil
      } else {
        f
      }
    }

    def bug5365(base: g.Type, pat: g.Tree, cse: CaseDef, sym: g.Symbol) = {
      // Workaround for <https://issues.scala-lang.org/browse/SI-5365>
      // deliberatly leave out guards as they disable exhaustiveness checks
      // at least until https://github.com/scala/scala/pull/4929
      // or something similar gets released.
      val gcse = g.CaseDef(pat, asg(cse.body))
      translator.translateCase(sym, base)(gcse)
    }

    def removeUnapply(pat: MatchResult) = pat.cse.pat.exists {
      case UnApply(_, _) ⇒ false
      case _             ⇒ true
    }

    def makeCaseTrees(pat: MatchResult, tree: g.Tree, base: g.Type, scrutSym: g.Symbol): List[translator.TreeMaker] = {
      val newPat = typer.typedPattern(g.resetAttrs(tree), base)
      if (newPat == EmptyTree) {
        c.error(pat.cse.pos, s"Failed to type ${pat.cse.pat} as $base. This a bug in typed-actors.")
        Nil
      } else {
        bug5365(base, newPat, pat.cse, scrutSym)
      }
    }

    def runExhaustivenessChecker(cases: List[List[translator.TreeMaker]], scrutSym: g.Symbol, patterns: List[MatchResult], base: g.Type, sym: Symbol) = {
      val counterExamples = translator.exhaustive(scrutSym, cases, base)
      if (counterExamples.isEmpty) improveExhaustiveness(patterns, base, sym) else counterExamples
    }

    def improveExhaustiveness(patterns: List[MatchResult], base: g.Type, sym: Symbol): List[String] = {
      // Scalas checker reports exhaustivity, but we can improve on some cases.
      // mostly for literals where we dont have a match all case.

      // Flatmapping all of expr is mostly wrong for recursive types
      // like ::, but we expect Scala to have covered these.
      // If not, well, good luck then.
      val exprs = patterns.flatMap(_.pt.x.expr)

      // Not sure how different this is from `knownDirectSubclasses` but at least
      // it is used in the `exhaustive` method above, so we use the same notion
      // of subtypes as the exhaustiveness checker.
      val knownSubtypes = translator.enumerateSubtypes(base, grouped = false).flatten
      bug7406(base, knownSubtypes, sym, exprs) {
        if (knownSubtypes.isEmpty) {
          if (!exprs.exists(e ⇒ typeMatch(e, base.asInstanceOf[Type], variant = false))) {
            s"$base not ${typeMsg(exprs)}" :: Nil
          } else {
            Nil
          }
        } else {
          knownSubtypes
            .withFilter(st ⇒ !isTypePartOf(st.asInstanceOf[Type], exprs, variant = false))
            .map(_.toString)
        }
      }
    }

    def checkExhaustiveness(base: Type, patterns: List[MatchResult]) = {
      val baseSym = bug7755(base)
      val tp = base.asInstanceOf[g.Type]
      val scrutSym = translator.freshSym(baseSym.pos.asInstanceOf[g.Position], tp)
      val cases = patterns.withFilter(removeUnapply)
        .map(pat ⇒ makeCaseTrees(pat, asg(bug5464(pat)), tp, scrutSym))
      val counterExamples = runExhaustivenessChecker(cases, scrutSym, patterns, tp, baseSym)
      if (counterExamples.nonEmpty) {
        val msg = plural(counterExamples,
          s"input: ${counterExamples.head}",
          s"inputs: ${counterExamples.mkString(", ")}")
        c.error(c.enclosingPosition, String.format(nonExhaustiveMsg, base, msg))
      }
    }

    allPatterns.foreach {case (base, patterns) ⇒
      if (shouldCheck(base) && !hasCatchAll(patterns)) {
        checkExhaustiveness(base, patterns)
      }
    }
  }

  private def patternTypes(pattern: c.Tree): List[AndPos[PatternType]] = pattern match {
    case PatternTypes(tpes) ⇒ tpes
    case x                  ⇒ c.abort(x.pos, unknownMsg)
  }

  private def plural(lst: List[_], ifOne: ⇒ String, ifMore: ⇒ String) =
    if (lst.nonEmpty && lst.tail.nonEmpty) ifMore else ifOne
}

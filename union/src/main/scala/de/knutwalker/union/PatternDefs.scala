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

import scala.Option.{ apply ⇒ some }
import scala.collection.immutable.List.{ apply ⇒ list, empty ⇒ nil }
import scala.language.experimental.macros
import scala.reflect.macros.blackbox


abstract class PatternDefs extends MacroDefs {
  val c: blackbox.Context
  val g = c.universe.asInstanceOf[tools.nsc.Global]
  import c.universe._


  object VarName {
    private[this] val gnme = termNames.asInstanceOf[g.TermNames]

    def unapply(n: Name): Boolean =
      n == termNames.WILDCARD || gnme.isVariableName(n.asInstanceOf[g.Name])

    def unapply(t: Tree): Boolean = t match {
      case x: Ident ⇒ !x.isBackquoted && unapply(x.name)
      case _        ⇒ false
    }
  }

  object CatchAll {
    private[this] val s = (Nil.::[AndPos[PatternType]] _).andThen(some)

    def unapply(pat: Tree): Option[List[AndPos[PatternType]]] = pat match {
      case Bind(VarName(), tree)   ⇒ unapply(tree)
      case Ident(VarName())        ⇒ s(PatternType.No at pat.pos)
      case Star(tree)              ⇒ unapply(tree)
      case Typed(CatchAll(_), tpt) ⇒ s(PatternType(tpt.tpe, NoType) at pat.pos)
      case EmptyTree               ⇒ s(PatternType.No at pat.pos)
      case Alternative(ps)         ⇒ Option.flatTraverse(ps)(unapply)
      case _                       ⇒ None
    }
  }

  object SyntheticCheckedTyped {
    private[this] val gnme = termNames.asInstanceOf[g.TermNames]
    def unapply(pat: Tree): Boolean = pat match {
      case Bind(n, UnApply(Apply(Select(_, gnme.unapply), List(Ident(gnme.SELECTOR_DUMMY))), List(Typed(Ident(gnme.WILDCARD), _)))) ⇒ true
      case _                                                                                                                        ⇒ false
    }
  }

  /**
   * get a more precise representation than just simply `tpe.finalResultType`
   * especially:
   * - tuples like `(_: String, _: Int)` will return `(String, Int)` instead of `(Any, Any)`
   * - wildcards will return `notype` instead of `Any` to keep the wildcard semantics
   * - alternatives like `"foo" | 42` will return `List(String, Int)` instead of `Any`
   */
  object PatternTypes {
    private[this] val s = (Nil.::[AndPos[PatternType]] _).andThen(some)

    def unapply(pat: Tree): Option[List[AndPos[PatternType]]] = pat match {
      case SyntheticCheckedTyped()   ⇒ s(PatternType(pat.tpe, NoType) at pat.pos)
      case CatchAll(pats)            ⇒ some(pats)
      case Bind(n, patt)             ⇒ unapply(patt)
      case Apply(tt: TypeTree, args) ⇒ resolveApply(tt.tpe, args, tt.pos)
      case Apply(f, args)            ⇒ resolveUnapply(pat.tpe, args, pat.pos)
      case UnApply(f, args)          ⇒ resolveUnapply(pat.tpe, args, pat.pos)
      case Star(tree)                ⇒ unapply(tree)
      case Alternative(ps)           ⇒ Option.flatTraverse(ps)(unapply)
      case Typed(expr, tpt)          ⇒ s(PatternType(tpt.tpe, unapply(expr).fold(nil[Type])(_.flatMap(_.x.expr))) at tpt.pos)
      case Select(_, _)              ⇒ s(PatternType(pat.tpe) at pat.pos)
      case Ident(_)                  ⇒ s(PatternType(pat.tpe) at pat.pos)
      case Literal(const)            ⇒ s(PatternType(c.internal.constantType(const)) at pat.pos)
      case _                         ⇒ None
    }

    private def resolveUnapply(tpe: c.Type, args: List[Tree], pos: c.Position): Option[List[AndPos[PatternType]]] = {
      val typeArgs = tpe.typeArgs
      if (typeArgs.isEmpty) {
        Option.flatTraverse(args)(unapply).map(_.map(_.x at pos))
      } else if (typeArgs.tail.isEmpty) {
        Option.flatTraverse(args)(unapply).map {subDefs ⇒
          val subs = subDefs.map(_.x)
          val applied = lubapply(tpe, List(lubPats(subs)))
          list(applied at pos)
        }
      } else {
        None
      }
    }

    private def lubapply(tpe: Type, subs: List[PatternType]): PatternType = {
      val ls = subs.map(_.pt)
      val pt = appliedType(tpe, ls)
      val expr = for (s ← subs; e ← s.expr) yield appliedType(tpe, e)
      PatternType(pt, expr)
    }

    private def lubs[A](xs: List[A])(f: A ⇒ c.Type): c.Type =
      lub(xs.withFilter(x ⇒ f(x) != NoType).map(f))

    private def resolveApply(tpe: c.Type, args: List[c.Tree], pos: c.Position): Option[List[AndPos[PatternType]]] =
      resolveType(tpe, args).map(x ⇒ list(x at pos))

    private def resolveType(tpe: c.Type, args: List[c.Tree]): Option[PatternType] = {
      val resultType = tpe.finalResultType
      val typeArgs = mapCaseAccessorsToTypeArgs(resultType)
      if (typeArgs.isEmpty) {
        Some(PatternType(resultType))
      } else {
        Option.sequence(args.mapWithIndex(concretiseArgument(typeArgs, resultType))).map {subArgs ⇒
          val concreteTypeArgs = resultType.typeArgs.indices.map(lubTypeArgs(subArgs)).toList
          lubapply(resultType.typeConstructor, concreteTypeArgs)
        }
      }
    }

    private def lubTypeArgs(subArgs: List[TypeArg])(typeArgIndex: Int): PatternType = {
      subArgs.toList.collect {
        case TypeArg(_, _, `typeArgIndex`, _, _, Some(p)) ⇒ p
      } match {
        case x :: Nil ⇒ x
        case Nil      ⇒ PatternType.No
        case xs       ⇒ lubPats(xs)
      }
    }

    private def lubPats(xs: List[PatternType]): PatternType =
      PatternType(lubs(xs)(_.pt), xs.flatMap(_.expr))


    private def concretiseArgument(typeArgs: List[TypeArg], resultType: c.Type)(pattern: c.Tree, applyIndex: Int): Option[TypeArg] = {
      def findProper(associated: TypeArg) = {
        unapply(pattern) match {
          case Some(Nil)                                ⇒ Some(PatternType.No)
          case Some(x :: Nil) if associated.directMatch ⇒ Some(x.x)
          case Some(x :: Nil)                           ⇒
            val aligned = alignArgument(x.x.pt, associated.field, associated.arg, pattern.pos)
            val alignedExpr = x.x.expr.map(alignArgument(_, associated.field, associated.arg, pattern.pos))
            Some(PatternType(aligned, alignedExpr))
          case None                                     ⇒ Some(PatternType.No)
          case Some(xs)                                 ⇒ None
        }
      }
      for {
        associated ← typeArgs.find(_.posInApply == applyIndex)
        proper ← findProper(associated)
      } yield associated.concrete(proper)
    }

    private def alignArgument(tpe: Type, to: Type, ptr: TypeArgPointer, pos: Position): Type = {
      if (tpe == NoType) NoType
      else {
        val aligned = alignTypeTo(tpe, to)
        applyTypeArg(aligned, ptr, pos)
      }
    }
  }
}

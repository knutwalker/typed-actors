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

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

abstract class MacroDefs {
  val c: blackbox.Context
  import c.universe._


  case class AndPos[A](x: A, pos: Position)

  /**
   * Represents a pattern match.
   * `pt` is the type the pattern matches agains.
   * `expr` is the type of the pattern expression itself.
   */
  case class PatternType(pt: Type, expr: List[Type]) {
    def matches(ut: Type): Boolean = typeMatch(pt, ut)
  }
  object PatternType {
    def apply(pt: Type): PatternType =
      apply(pt, pt :: Nil)
    def apply(pt: Type, expr: Type): PatternType =
      apply(pt, expr :: Nil)
  }

  /**
   * Represents a cursor into a type argument of a given type.
   */
  case class TypeArgPointer(original: Type, argument: Type, cursor: List[Int])


  /**
   * Represents a type argument
   */
  case class TypeArg(field: Type, arg: TypeArgPointer, posInTypeArgs: Int, posInApply: Int, directMatch: Boolean, concreteType: Option[PatternType]) {
    override def toString: String = s"Type($field${if (directMatch) "" else s"(${arg.argument})"}, typeArg=$posInTypeArgs, apply=$posInApply${concreteType.fold("")(c ⇒ ", concrete=" + c)})"
    def concrete(tpe: PatternType) = copy(concreteType = Some(tpe))
  }
  object TypeArg {
    def direct(field: Type, posInTypeArgs: Int, posInApply: Int) =
      TypeArg(field, TypeArgPointer(field, field, Nil), posInTypeArgs, posInApply, directMatch = true, None)

    def wrapped(field: Type, pointer: TypeArgPointer, posInTypeArgs: Int, posInApply: Int) =
      TypeArg(field, pointer, posInTypeArgs, posInApply, directMatch = false, None)
  }

  final val NilType = List.empty[Type]


  /**
   * Aborts macro execution with the given error messages
   */
  def fail(msg: String): Nothing =
    c.abort(c.enclosingPosition, msg)

  /**
   * Produce compile error but does not abort macro execution
   */
  def err(msg: String): Unit =
    c.error(c.enclosingPosition, msg)

  /**
   * Get a distinct set of types as defined by =:=
   */
  def distinct(list: List[Type]): List[Type] =
    distinct0(list, Set.empty, ListBuffer.empty, _ =:= _)

  /**
   * Get a list of all fields for a type
   */
  def fieldsOf(tpe: Type): List[(TermName, Type)] = {
    val tSym = tpe.typeSymbol
    if (tSym.isClass && isAnonOrRefinement(tSym)) Nil
    else tpe.decls.collect({
      case sym: TermSymbol if isCaseAccessorLike(sym) ⇒
        (sym.name, sym.typeSignatureIn(tpe).finalResultType)
    })(collection.breakOut)
  }

  /**
   * Turns `A with B with C` into List(A, B, C)
   */
  def unwindWith(tpe: Type): List[Type] = tpe match {
    case RefinedType(parents, _) ⇒ parents.flatMap(p ⇒ unwindWith(p))
    case _                       ⇒ tpe :: Nil
  }

  /**
   * Unwinds `with`s and returns the last subtype
   */
  def finalType(tpe: Type): Type =
    unwindWith(tpe).reverse.head

  /**
   * Checks if a type is at the end of a 'with' chain.
   * e.g. isEffectively(String, Int with String) == true
   */
  def isEffectively[T](tpe: Type)(implicit T: TypeTag[T]) =
    finalType(tpe) =:= T.tpe

  /**
   * Turns `A | B | C` into List(A, B, C)
   */
  def expandUnion(t: Type, unionTpe: Type): List[Type] =
    expandUnion0(t :: Nil, unionTpe, ListBuffer.empty)

  def expandUnionWith(t: Type, unionTpe: Type): List[Type] =
    distinct(unwindWith(t).flatMap(ut ⇒ expandUnion(ut, unionTpe)))

  /**
   * Gets a cursor representation of the types type arguments.
   * e.g. `Either[A, B]` yields two results
   * - `TypeArg(Either[A, B], A, List(0))`
   * - `TypeArg(Either[A, B], B, List(1))`
   */
  def extractTypeArgs(orig: Type): List[TypeArgPointer] =
    extractTypeArgs0((orig, -1) :: Nil, orig, Nil, ListBuffer.empty)

  /**
   * Applies a `TypeArg` onto a concrete type. The target type must match the shape
   * but might have different types than the original of the type arg.
   * e.g. `Either[String, Int] + TypeArg(Either[A, B], B, List(1))` yields `Int`
   */
  def applyTypeArg(target: Type, arg: TypeArgPointer, pos: Position): Type =
    applyTypeArg0(target, arg.cursor).getOrElse(c.abort(pos, s"could not align the type $target from ${arg.original} to ${arg.argument}."))

  def setTypeArg(target: Type, shape: Type, arg: TypeArgPointer, pos: Position): Type =
    setTypeArg0(target, shape, arg.cursor, Some(_)).getOrElse(c.abort(pos, s"could not set the type $target from ${arg.original} to ${arg.argument}"))

  /**
   * Aligns a subtype to be in the shape of the target type with possible
   * concrete types instead of abstract ones if the subtype is defined as such.
   * e.g. `alignTypeTo(Nil, List[A]) -> List[Nothing]`
   */
  def alignTypeTo(current: Type, target: Type): Type =
    current.baseType(target.typeConstructor.typeSymbol)

  /**
   * Identifies all arguments and their type arguments from a case class like type.
   * e.g. (roughly): List[B] -> List(TypeArg(B))
   */
  def mapCaseAccessorsToTypeArgs(tpe: Type, generify: Boolean = true): List[TypeArg] = {
    val typeArity = tpe.typeArgs.size
    if (typeArity > 0) {
      if (generify) {
        val genericResultType = tpe.etaExpand
        val genericTypeArgs = genericResultType.typeParams.map(_.asType.toType)
        resolveFields(genericResultType, genericTypeArgs)
      } else {
        resolveFields(tpe, tpe.typeArgs)
      }
    } else {
      Nil
    }
  }

  private def resolveFields(tpe: Type, args: List[Type]): List[TypeArg] = {
    val maccs = fieldsOf(tpe)
    maccs.flatMapWithIndex(resolveTypeArguments(args))
  }

  /**
   * Checks if a type is directly contained in a list of target types.
   */
  def membership(types: List[Type]): (Type) ⇒ Boolean =
    tpe ⇒ containsTypeWith(tpe, types, _ =:= _)

  /**
   * Checks if a type is contained in a list of target types by using a
   * custom sense of subtype constraints.
   */
  def containsType(tpe: Type, types: List[Type]): Boolean =
    containsTypeWith(tpe, types, (a, b) ⇒ typeMatch(a, b))

  /**
   * Checks if a type is contained in a list of target types by means of the
   * provided comparison function.
   */
  def containsTypeWith(tpe: Type, types: List[Type], check: (Type, Type) ⇒ Boolean): Boolean =
    if (tpe == NoType) true else containsTpe0(tpe, types, check)

  /**
   * Checks if two types match by a custom sense of subtype constraints
   */
  def typeMatch(matchType: Type, targetType: Type, variant: Boolean = true): Boolean = {
    if (targetType =:= matchType) true
    else if (variant && (matchType <:< targetType)) true
    else if (matchType == NoType) true
    else if (matchType.typeArgs.nonEmpty && targetType.typeArgs.nonEmpty && (matchType.typeConstructor <:< targetType.typeConstructor)) {
      (matchType.typeArgs, targetType.typeArgs).zipped.forall((m, t) ⇒ typeMatch(m, t, variant))
    } else if (variant) {
      val alignMatchToUnion = alignTypeTo(matchType, targetType)
      (alignMatchToUnion != NoType) && typeMatch(alignMatchToUnion, targetType, variant)
    } else {
      false
    }
  }

  private def isAnonOrRefinement(sym: Symbol): Boolean = {
    val nameStr = sym.name.toString
    nameStr.contains("$anon") || nameStr == "<refinement>"
  }

  private def isCaseAccessorLike(sym: TermSymbol): Boolean =
    sym.isPublic && (if(sym.owner.asClass.isCaseClass) sym.isCaseAccessor else sym.isAccessor)

  @tailrec
  private def distinct0(list: List[Type], has: Set[Type], result: ListBuffer[Type], eq: (Type, Type) ⇒ Boolean): List[Type] = list match {
    case Nil ⇒ result.result()
    case tpe :: rest ⇒
      if (has.exists(eq(tpe, _))) distinct0(rest, has, result, eq)
      else distinct0(rest, has + tpe, result += tpe, eq)
  }

  @tailrec
  private def expandUnion0(types: List[Type], unionTpe: Type, result: ListBuffer[Type]): List[Type] = types match {
    case Nil       ⇒ result.result()
    case t :: rest ⇒
      val dt = t.dealias
      if (dt <:< unionTpe) expandUnion0(dt.typeArgs ::: rest, unionTpe, result)
      else expandUnion0(rest, unionTpe, result += dt)
  }

  /*
    // TODO: repeated?
    // val isRepeated = dt.resultType.typeSymbol == definitions.RepeatedParamClass
    //  val proper = if (!isRepeated) dt else dt.typeArgs.head
   */
  @tailrec
  private def extractTypeArgs0(types: List[(Type, Int)], orig: Type, cursor: List[Int], result: ListBuffer[TypeArgPointer]): List[TypeArgPointer] = types match {
    case Nil              ⇒ result.result()
    case (t, idx) :: rest ⇒
      val dt = t.dealias
      val path = if (idx == -1) cursor else idx :: cursor
      if (dt.typeArgs.nonEmpty) extractTypeArgs0(dt.typeArgs.zipWithIndex ::: rest, orig, path, result)
      else extractTypeArgs0(rest, orig, cursor, result += TypeArgPointer(orig, dt, path.reverse))
  }

  @tailrec
  private def applyTypeArg0(target: Type, cursor: List[Int]): Option[Type] = cursor match {
    case x :: rest ⇒
      val targetArg = target.dealias.typeArgs.drop(x).headOption
      targetArg match {
        case None     ⇒ None
        case Some(ta) ⇒ applyTypeArg0(ta, rest)
      }
    case Nil       ⇒ Some(target)
  }

  @tailrec
  private def setTypeArg0(target: Type, shape: Type, cursor: List[Int], cont: Type ⇒ Option[Type]): Option[Type] = cursor match {
    case x :: rest ⇒
      val shp = shape.dealias
      val targetArg = shp.typeArgs.drop(x).headOption
      targetArg match {
        case None     ⇒ None
        case Some(ta) ⇒ setTypeArg0(target, ta, rest, t ⇒ cont(t) match {
          case None       ⇒ None
          case Some(newT) ⇒ Some(appliedType(shp.typeConstructor, shp.typeArgs.updated(x, newT)))
        })
      }
    case Nil       ⇒ cont(target)
  }

  @tailrec
  private def containsTpe0(tpe: Type, types: List[Type], check: (Type, Type) ⇒ Boolean): Boolean = types match {
    case Nil       ⇒ false
    case t :: rest ⇒ if (check(tpe, t)) true else containsTpe0(tpe, rest, check)
  }

  def caseAccessors(tpe: Type): List[MethodSymbol] = tpe.decls.toList.collect {
    case m: MethodSymbol if m.isCaseAccessor ⇒ m
  }

  def resolveTypeArguments(typeArgs: List[Type])(method: (TermName, Type), applyIndex: Int): List[TypeArg] = {
    val field = method._2
    val argIndex = typeArgs.indexWhere(_ =:= field)
    if (argIndex >= 0) TypeArg.direct(field, argIndex, applyIndex) :: Nil
    else resolveWrappedTypeArguments(field, applyIndex, typeArgs)
  }

  private def resolveWrappedTypeArguments(field: Type, applyIndex: Int, typeArgs: List[Type]): List[TypeArg] = {
    val pointers = extractTypeArgs(field)
    pointers.flatMap {pointer ⇒
      val typeArgPosition = typeArgs.indexWhere(_ =:= pointer.argument)
      Some(typeArgPosition).filter(_ >= 0).map {typeArgPosition ⇒
        TypeArg.wrapped(field, pointer, typeArgPosition, applyIndex)
      }
    }
  }
}

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

package de.knutwalker

import scala.annotation.implicitNotFound
import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom

package object union {
  implicit final class ListFirstOps[A](private val list: List[A]) extends AnyVal {
    def first: Either[List[A], Option[A]] =
      first(x ⇒ x)

    def first[B](conv: A ⇒ B): Either[List[B], Option[B]] = list match {
      case x :: Nil  ⇒ Right(Some(conv(x)))
      case Nil       ⇒ Right(None)
      case otherwise ⇒ Left(otherwise.map(conv))
    }
  }

  implicit final class Tuple2Ops[A, B](private val tuple: (A, B)) extends AnyVal {
    def map[C](f: B ⇒ C): (A, C) =
      (tuple._1, f(tuple._2))
  }

  implicit final class TraversableOps[A, Repr](private val wrapped: TraversableLike[A, Repr]) extends AnyVal {
    def mapWithIndex[B, To](f: (A, Int) ⇒ B)(implicit cbf: CanBuildFrom[Repr, B, To]): To =
      (wrapped, zeros).zipped.map(f)(cbf)

    def flatMapWithIndex[B, To](f: (A, Int) ⇒ TraversableOnce[B])(implicit cbf: CanBuildFrom[Repr, B, To]): To =
      (wrapped, zeros).zipped.flatMap(f)(cbf)
  }

  private[this] val zeros = new Iterable[Int] {
    def iterator: Iterator[Int] = Iterator.from(0)
    override def toString(): String = "0..."
  }
}

/**
 * Union type implementation follows. These are type classes
 * and provers to implement the type-level constraints for the union types.
 */
package union {

  @implicitNotFound("Cannot prove that ${A} is a union type.")
  sealed trait IsUnion[-A] {
    type Out <: Union
  }

  object IsUnion {
    type Aux[-A0, U0 <: Union] = IsUnion[A0] {type Out = U0}
    implicit def isUnion[A <: Union]: Aux[A, A] =
      new IsUnion[A] {
        type Out = A
      }
  }

  @implicitNotFound("${A} is not part of ${U}.")
  sealed trait isPartOf[A, U <: Union]
  object isPartOf {
    implicit def materialize[A, U <: Union]: A isPartOf U = macro UnionMacros.isPartOfImpl[A, U]
  }

  @implicitNotFound("${U} does not contain some of ${T}.")
  sealed trait containsSomeOf[U <: Union, T <: Union]
  object containsSomeOf{
    implicit def materialize[U <: Union, T <: Union]: U containsSomeOf T = macro UnionMacros.containsSomeOfImpl[U, T]
  }

  @implicitNotFound("${U} does not contain all of ${T}.")
  sealed trait containsAllOf[U <: Union, T <: Union]
  object containsAllOf {
    implicit def materialize[U <: Union, T <: Union]: U containsAllOf T = macro UnionMacros.containsAllOfImpl[U, T]
  }

  // @annotation.implicitAmbiguous("${A} must not be <: ${B}")
  sealed trait isNotA[A, B]
  object isNotA {
    implicit def nsub[A, B]: A isNotA B = null
    // $COVERAGE-OFF$Code only exists to prove non-equality and is expected to never execute
    implicit def nsubAmbig1[A, B >: A]: A isNotA B = sys.error("Unexpected invocation")
    implicit def nsubAmbig2[A, B >: A]: A isNotA B = sys.error("Unexpected invocation")
    // $COVERAGE-ON$
  }
}

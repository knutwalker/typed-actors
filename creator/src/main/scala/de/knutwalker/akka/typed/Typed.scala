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

package de.knutwalker.akka.typed

import _root_.akka.actor.{ ActorRefFactory, Actor }
import _root_.shapeless.{ Generic, ProductArgs }

import scala.reflect.ClassTag

/**
 * A shapeless-powered, typed variant of the actor/props creation.
 * This is an alternative over constructors, that use the `Class[A], Any*`
 * overload to create actors.
 *
 * You must use `TypedActor`s and these actors must be `case class`es for
 * this to work.
 *
 * Example:
 * {{{
 * case class ExampleActor(foo: Option[String]) extends TypedActor.Of[String] {
 *   def typedReceive: TypedReceive = ???
 * }
 *
 * // runtime error (IllegalArgumentException: no matching constructor found):
 * val runtimeError: ActorRef[String] =
 *   ActorOf(PropsOf[String](classOf[ExampleActor1], "wrong type"))
 *
 * // compiletime error:
 * //   found   : String("wrong type") :: HNil
 * //   required: Option[String] :: HNil
 * val compiletimeError: ActorRef[String] =
 *   Typed[ExampleActor2].create("wrong type")
 * }}}
 *
 * === Usage ===
 *
 * Use apply with the `TypedActor` as the type parameter and then call either
 * `create` or `props` with the appropriate constructor parameters to create
 * either an [[ActorRef]] or a [[Props]].
 */
object Typed {

  def apply[T <: TypedActor](implicit gen: Generic[T], ct: ClassTag[T]) =
    new MkProps[T#Message, T, gen.Repr]()(gen, ct)

  final class MkProps[A, T <: Actor, L](implicit gen: Generic.Aux[T, L], ct: ClassTag[T]) extends ProductArgs {

    def propsProduct(args: L): Props[A] =
      Props[A](UntypedProps[T](gen.from(args)))

    def createProduct(args: L)(implicit ref: ActorRefFactory): ActorRef[A] =
      ActorOf(propsProduct(args))
  }
}

/*
 * Copyright 2015 Paul Horn
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

object Typed {

  def apply[T <: TypedActor](implicit gen: Generic[T], ct: ClassTag[T]) =
    new MkProps[T#Message, T, gen.Repr]()(gen, ct)

  final class MkProps[A, T <: Actor, L](implicit gen: Generic.Aux[T, L], ct: ClassTag[T]) extends ProductArgs {

    def propsProduct(args: L): Props[A] =
      Props[A](akka.actor.Props[T](gen.from(args)))

    def createProduct(args: L)(implicit ref: ActorRefFactory): ActorRef[A] =
      ActorOf(propsProduct(args))
  }
}

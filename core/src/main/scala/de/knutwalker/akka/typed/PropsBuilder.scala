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

import _root_.akka.actor.Actor

import scala.reflect.ClassTag

/**
 * Type-curried creation of `Props[A]` to aid the type inference.
 *
 * @see [[de.knutwalker.akka.typed.PropsOf]]
 * @tparam A the message type this actor is receiving
 */
final class PropsBuilder[A] private[typed] {

  /**
   * Creates a new typed Props that uses the default constructor of the given
   * actor type to create new instances of this actor.
   *
   * Wrapper for `akka.actor.Props[T]`.
   *
   * @tparam T the actor type
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def apply[T <: Actor : ClassTag]: Props[A] =
    Props[A, T]

  /**
   * Creates a new typed Props that uses the given creator function to create
   * instances of this actor.
   *
   * CAVEAT: Required mailbox type cannot be detected when using anonymous
   * mixin composition when creating the instance. For example, the following
   * will not detect the need for `DequeBasedMessageQueueSemantics` as defined
   * in `Stash`:
   *
   * {{{
   * 'Props(new Actor with Stash { ... })
   * }}}
   *
   * Instead you must create a named class that mixin the trait, e.g.
   * `class MyActor extends Actor with Stash`.
   *
   * Wrapper for `akka.actor.Props[T](=> T)`.
   *
   * @param creator the thunk that create the new instance of this actor
   * @tparam T the actor type
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def apply[T <: Actor : ClassTag](creator: ⇒ T): Props[A] =
    Props[A, T](creator)

  /**
   * Creates a new typed Props that uses the given class and constructor
   * arguments to create instances of this actor.
   *
   * Wrapper for `akka.actor.Props[T](Class[T], Any*)`.
   *
   * @param clazz the class of this actor
   * @param args the constructor argumentes of this actor
   * @tparam T the actor type
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def apply[T <: Actor](clazz: Class[T], args: Any*): Props[A] =
    Props[A, T](clazz, args: _*)
}

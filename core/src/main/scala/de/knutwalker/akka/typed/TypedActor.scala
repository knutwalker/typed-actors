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
import _root_.akka.event.LoggingReceive
import de.knutwalker.akka.typed.TypedActor.TypedReceiver

/**
 * TypedActor base trait that should be extended by to create a typed Actor.
 * This actor is designed to only receive one type of message in its lifetime.
 * Typically, this is some ADT/sealed trait that defines the protocol for this actor.
 *
 * The message type is defined by the abstract type member `Message`.
 * The object [[TypedActor$]] defines a helper trait to provide the message type
 * via a type paramter instead of a type member. These two are equivalent:
 *
 * {{{
 *   class ExampleActor extends TypeActor {
 *     type Message = ExampleProtocol
 *     // ...
 *   }
 *
 *   class ExampleActor extends TypeActor.Of[ExampleProtocol] {
 *     // ...
 *   }
 * }}}
 *
 * The `TypedActor` is just a regular actor, but if offers some methods that help
 * you stay within the defined typed contract.
 * [[TypedActor#typedSelf]] is the alternative to [[akka.actor.Actor.self]]
 * to get the typed ActorRef for this actor.
 * [[TypedActor#typedBecome]] is the alternative to `context.become` to check
 * the receiving type while changing behavior.
 * You can get exhausitveness checking for your receive block if you use
 * [[TypedActor#Total]] as a wrapper around your receive block.
 *
 * {{{
 *   // error: match may not be exhaustive. It would fail on the following inputs: None
 *   class ExampleActor extends TypedActor {
 *     type Message = Option[String]
 *     def typedReceive: TypedReceive = Total {
 *       case Some("foo") ⇒
 *     }
 *   }
 * }}}
 *
 * @see [[akka.actor.Actor]] for more about Actors in general.
 */
trait TypedActor extends Actor {
  type Message
  type TypedReceive = PartialFunction[Message, Unit]

  /** Typed variant of [[self]]. */
  final val typedSelf: ActorRef[Message] =
    tag(self)

  /** Typed variant of `context.become`. */
  final def typedBecome(f: TypedReceive): Unit =
    context become mkReceive(f)

  /**
   * Wraps a total receiver function and returns it as a [[TypedReceive]].
   * Use this to get exhaustiveness checking for your receive block.
   *
   * {{{
   *   // error: match may not be exhaustive. It would fail on the following inputs: None
   *   class ExampleActor extends TypedActor {
   *     type Message = Option[String]
   *     def typedReceive: TypedReceive = Total {
   *       case Some("foo") ⇒
   *     }
   *   }
   * }}}
   */
  final def Total(f: Message ⇒ Unit): TypedReceive =
    PartialFunction(f)

  /**
   * `TypedActor`s delegate to [[typedReceive]].
   * @see [[akka.actor.Actor#receive]]
   */
  final def receive: Receive =
    mkReceive(typedReceive)

  /**
   * Defines the actors behavior. Unlike [[akka.actor.Actor#receive]], this one
   * is typed in its first parameter.
   */
  @deprecated("use typedReceive", "1.2.0")
  def receiveMsg: TypedReceive =
    typedReceive

  /**
   * Defines the actors behavior. Unlike [[akka.actor.Actor#receive]], this one
   * is typed in its first parameter.
   */
  def typedReceive: TypedReceive

  private def mkReceive(f: TypedReceive): Receive =
    LoggingReceive(new TypedReceiver(f))
}
object TypedActor {
  /**
   * A convenience trait to provide the message type via type parameters.
   *
   * * {{{
   *   class ExampleActor extends TypeActor {
   *     type Message = ExampleProtocol
   *     // ...
   *   }
   *
   *   class ExampleActor extends TypeActor.Of[ExampleProtocol] {
   *     // ...
   *   }
   * }}}
   *
   * @tparam A the message type this actor is receiving
   */
  trait Of[A] extends TypedActor {final type Message = A}

  private class TypedReceiver[A](f: PartialFunction[A, Unit]) extends PartialFunction[Any, Unit] {
    def isDefinedAt(x: Any): Boolean = try {
      f.isDefinedAt(x.asInstanceOf[A])
    } catch {
      case _: ClassCastException ⇒ false
    }

    def apply(x: Any): Unit =
      f(x.asInstanceOf[A])
  }
}

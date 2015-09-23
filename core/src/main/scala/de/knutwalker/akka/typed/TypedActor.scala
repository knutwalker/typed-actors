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
import akka.actor.Actor.Receive
import de.knutwalker.akka.typed.TypedActor.{ Downcast, TypedReceiver }

import scala.reflect.ClassTag

/**
 * TypedActor base trait that should be extended by to create a typed Actor.
 * This actor is designed to only receive one type of message in its lifetime.
 * Typically, this is some ADT/sealed trait that defines the protocol for this actor.
 *
 * The message type is defined by extending [[TypedActor.Of]]:
 *
 * {{{
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
 * If you must go back to untyped land, use the [[TypedActor#Untyped]] wrapper.
 *
 * @see [[akka.actor.Actor]] for more about Actors in general.
 */
sealed trait TypedActor extends Actor {
  type Message
  type TypedReceive = PartialFunction[Message, Unit]
  implicit def _ct: ClassTag[Message]

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
   *   class ExampleActor extends TypedActor.Of[Option[String]] {
   *     def typedReceive: TypedReceive = Total {
   *       case Some("foo") ⇒
   *     }
   *   }
   * }}}
   */
  final def Total(f: Message ⇒ Unit): TypedReceive =
    new Downcast[Message](_ct.runtimeClass.asInstanceOf[Class[Message]])(f)

  /**
   * Wraps an untyped receiver and returns it as a [[TypedReceive]].
   * Use this to match for messages that are outside of your protocol, e.g. [[akka.actor.Terminated]].
   *
   * {{{
   *   class ExampleActor extends TypedActor.Of[ExampleMessage] {
   *     def typedReceive: TypedReceive = Untyped {
   *       case Terminated(ref) => println(s"$$ref terminated")
   *     }
   *   }
   * }}}
   */
  final def Untyped(f: Receive): TypedReceive =
    f // .asInstanceOf[TypedReceive]

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
   * Abstract class to extend from in order to get a [[TypedActor]].
   * If you want to have the message type provided as a type parameter,
   * you have to add a context bound for [[scala.reflect.ClassTag]].
   *
   * {{{
   *   class ExampleActor extends TypeActor.Of[ExampleProtocol] {
   *     // ...
   *   }
   * }}}
   *
   * @tparam A the message type this actor is receiving
   */
  abstract class Of[A](implicit val _ct: ClassTag[A]) extends TypedActor {
    final type Message = A
  }

  /**
   * Creates a new typed actor from a total function, forfeiting the
   * functionality of changing behavior.
   *
   * @param f the actors behavior
   * @tparam A the message type
   */
  def apply[A: ClassTag](f: A ⇒ Unit): Props[A] =
    PropsFor(new TypedActor.Of[A] {def typedReceive = Total(f)})

  private class Downcast[A](cls: Class[A])(f: A ⇒ Unit) extends Receive {
    def isDefinedAt(x: Any): Boolean = cls.isInstance(x)
    def apply(v1: Any): Unit = f(cls.cast(v1))
  }

  private class TypedReceiver[A](f: PartialFunction[A, Unit]) extends Receive {
    private[this] val receive: Receive =
      f.asInstanceOf[Receive]

    def isDefinedAt(x: Any): Boolean = try {
      receive.isDefinedAt(x)
    } catch {
      case _: ClassCastException ⇒ false
    }

    def apply(x: Any): Unit =
      receive(x)
  }
}

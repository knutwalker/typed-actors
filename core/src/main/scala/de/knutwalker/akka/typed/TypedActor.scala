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

import de.knutwalker.union.{Union ⇒ UnionT}

import _root_.akka.actor.Actor
import _root_.akka.event.LoggingReceive
import akka.actor.Actor.Receive
import de.knutwalker.akka.typed.TypedActor.TypedReceiver

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
trait TypedActor extends Actor {
  type Message
  final type TypedReceive = PartialFunction[Message, Unit]

  /** Typed variant of [[self]]. */
  final val typedSelf: ActorRef[Message] =
    tag(self)

  /** Typed variant of `context.become`. */
  final def typedBecome(f: TypedReceive): Unit =
    context become untypedFromTyped(f)

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
  final val Total = UnionT.total[Message].total

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
    * Builds final receive out of an untyped receive while checking that the
    * matched parts belong to the provided message type, especially Unions.
   * This mirrors a [[TypedReceive]], i.e. you must not cover all cases.
    *
    * {{{
    *   class ExampleActor extends TypedActor.Of[Foo | Bar | Baz] {
    *     def typedReceive: TypedReceive = Union {
    *       case Foo() => println("foo")
    *       case Bar() => println("bar")
    *     }
    *   }
    * }}}
    */
  final val Union = UnionT[Message]

  /**
    * Builds final receive out of an untyped receive while checking that the
    * matched parts belong to the provided message type **and** that all possible
    * subcases are covered. Note, that it doesn't check that for each case
    * every possible case is covered.
    * This mirrors a [[Total]] receive, i.e. you must provide all cases.
    *
    * {{{
    *   class ExampleActor extends TypedActor.Of[Foo | Bar | Baz] {
    *     def typedReceive: TypedReceive = TotalUnion {
    *       case Foo() => println("foo")
    *       case Bar() => println("bar")
    *       case Bax() => println("bax")
    *     }
    *   }
    * }}}
    */
  final val TotalUnion = UnionT.total[Message]

  /**
   * `TypedActor`s delegate to [[typedReceive]].
   * @see [[akka.actor.Actor#receive]]
   */
  def receive: Receive =
    untypedFromTyped(typedReceive)

  /**
   * Defines the actors behavior. Unlike [[akka.actor.Actor#receive]], this one
   * is typed in its first parameter.
   */
  def typedReceive: TypedReceive

  /**
   * Wraps a typed receive and returns it as an untyped receive.
   * Use this only if you have to mix with other traits that override receive,
   * where you need to repeat the implementation of this typed actors default receive method.
   */
  final protected def untypedFromTyped(f: TypedReceive): Receive =
    LoggingReceive(new TypedReceiver(f))
}
object TypedActor {
  /**
   * Abstract class to extend from in order to get a [[TypedActor]].
   *
   * {{{
   *   class ExampleActor extends TypeActor.Of[ExampleProtocol] {
   *     // ...
   *   }
   * }}}
   *
   * @tparam A the message type this actor is receiving
   */
  abstract class Of[A] extends TypedActor {
    final type Message = A
  }

  /**
   * Creates a new typed actor from a total function, forfeiting the
   * functionality of changing behavior.
   *
   * @param f the actors behavior
   * @tparam A the message type
   */
  def apply[A](f: A ⇒ Unit)(implicit ct: ClassTag[A]): Props[A] =
    PropsFor(new TypedActor.Of[A] {
      val typedReceive: TypedReceive =
        new Downcast[A](this, ct.runtimeClass.asInstanceOf[Class[A]])(f)
    })

  private class Downcast[A](actor: Actor, cls: Class[A])(f: A ⇒ Unit) extends Receive {
    def isDefinedAt(x: Any): Boolean = cls.isInstance(x)
    def apply(v1: Any): Unit = try f(cls.cast(v1)) catch {
      case _: MatchError ⇒ actor.unhandled(v1)
    }
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

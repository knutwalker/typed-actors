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
import de.knutwalker.akka.typed.TypedActor.{ TypedBecomeOnAux, MkTotalUnionReceiveEmpty, MkPartialUnionReceive, Downcast, TypedReceiver }

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

  /** `context.become` for a given subtype if this actor is of a union type. */
  final def unionBecome(implicit ev: IsUnion[Message]): TypedBecomeOnAux[ev.Out] =
    new TypedBecomeOnAux[ev.Out](this)

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
  final def Total(f: Message ⇒ Unit)(implicit ct: ClassTag[Message]): TypedReceive =
    new Downcast[Message](this, ct.runtimeClass.asInstanceOf[Class[Message]])(f)

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
    * Builds final receive out of sub-receives if this TypedActor is for a Union message type.
    * This mirrors a [[TypedReceive]], i.e. you must not cover all cases.
    *
    * {{{
    *   class ExampleActor extends TypedActor.Of[Foo | Bar | Baz] {
    *     def typedReceive: TypedReceive = Union
    *       .on[Foo]{ case Foo() => println("foo") }
    *       .on[Bar]{ case Bar() => println("bar") }
    *       .apply
    *     }
    *   }
    * }}}
    */
  final def Union(implicit ev: IsUnion[Message]): MkPartialUnionReceive[ev.Out, MkPartialUnionReceive.Empty] =
    new MkPartialUnionReceive(this, None)

  /**
    * Builds final receive out of sub-receives if this TypedActor is for a Union message type.
    * This mirrors a [[Total]] receive, i.e. you must provide all cases.
    *
    * {{{
    *   class ExampleActor extends TypedActor.Of[Foo | Bar | Baz] {
    *     def typedReceive: TypedReceive = TotalUnion
    *       .on[Foo]{ case Foo() => println("foo") }
    *       .on[Bar]{ case Bar() => println("bar") }
    *       .on[Bax]{ case Bax() => println("bax") }
    *       .apply
    *     }
    *   }
    * }}}
    */
  final def TotalUnion(implicit ev: IsUnion[Message]): MkTotalUnionReceiveEmpty[ev.Out] =
    new MkTotalUnionReceiveEmpty(None)

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
  def apply[A: ClassTag](f: A ⇒ Unit): Props[A] =
    PropsFor(new TypedActor.Of[A] {def typedReceive = Total(f)})

  /**
   * Builder API for creating total union matchers.
   *
   * @see [[TypedActor.Union]]
   * @param finalPf the resulting receive function.
   * @tparam U the union type that provided the possible sub cases
   * @tparam S phantom type to ensure at least one case is provided
   */
  final class MkPartialUnionReceive[U <: Union, S <: MkPartialUnionReceive.State] private[TypedActor] (self: Actor, val finalPf: Option[PartialFunction[U, Unit]]) {

    /** Adds a case to the final receive function */
    def on[A](f: PartialFunction[A, Unit])(implicit ev: A isPartOf U): MkPartialUnionReceive[U, MkPartialUnionReceive.NonEmpty] = {
      val pf = new TypedReceiver[A](f).asInstanceOf[PartialFunction[U, Unit]]
      new MkPartialUnionReceive[U, MkPartialUnionReceive.NonEmpty](self, Some(finalPf.fold(pf)(_ orElse pf)))
    }
    def total[A](f: A => Unit)(implicit ev: A isPartOf U, ct: ClassTag[A]): MkPartialUnionReceive[U, MkPartialUnionReceive.NonEmpty] =
      on[A](new Downcast[A](self, ct.runtimeClass.asInstanceOf[Class[A]])(f))

    /** Returns the final receive function */
    def apply(implicit ev: S =:= MkPartialUnionReceive.NonEmpty): PartialFunction[U, Unit] =
      finalPf.get
  }
  object MkPartialUnionReceive {
    sealed trait State extends Any
    sealed trait Empty extends State
    sealed trait NonEmpty extends State

    implicit def buildReceive[U <: Union, S <: State](mk: MkPartialUnionReceive[U, S])(implicit ev: S =:= NonEmpty): PartialFunction[U, Unit] =
      mk.apply
  }

  /**
   * Builder API for creating total union matchers.
   * This is step 1 when no sub case was given.
   *
   * @see [[TypedActor.TotalUnion]]
   * @tparam U the union type that provided the possible sub cases
   */
  final class MkTotalUnionReceiveEmpty[U <: Union] private[TypedActor] (val ignore: Option[Nothing]) extends AnyVal {

    /** Adds a case to the final receive function */
    def on[A](f: PartialFunction[A, Unit])(implicit ev: A isPartOf U): MkTotalUnionReceiveHalfEmpty[U, A] =
      new MkTotalUnionReceiveHalfEmpty[U, A](new TypedReceiver[A](f).asInstanceOf[PartialFunction[U, Unit]])
    def total[A](f: A => Unit)(implicit ev: A isPartOf U): MkTotalUnionReceiveHalfEmpty[U, A] = on(PartialFunction(f))
  }

  /**
   * Builder API for creating total union matchers.
   * This is step 2 when the first sub case was given.
   * Cannot be an `AnyVal` as this would expose the final receive function.
   *
   * @see [[TypedActor.TotalUnion]]
   * @param finalPf the resulting receive function.
   * @tparam U the union type that provided the possible sub cases
   * @tparam B the type of the first sub case
   */
  final class MkTotalUnionReceiveHalfEmpty[U <: Union, B](finalPf: PartialFunction[U, Unit]) {

    /** Adds a case to the final receive function */
    def on[A](f: PartialFunction[A, Unit])(implicit ev: A isPartOf U): MkTotalUnionReceive[U, B | A] = {
      val pf = new TypedReceiver[A](f).asInstanceOf[PartialFunction[U, Unit]]
      new MkTotalUnionReceive[U, B | A](finalPf orElse pf)
    }
    def total[A](f: A => Unit)(implicit ev: A isPartOf U): MkTotalUnionReceive[U, B | A] = on[A](PartialFunction(f))
  }

  /**
   * Builder API for creating total union matchers.
   * This is the final step, when a covered union is given.
   * Cannot be an `AnyVal` as this would expose the final receive function.
   *
   * @see [[TypedActor.TotalUnion]]
   * @param finalPf the resulting receive function.
   * @tparam U the union type that provided the possible sub cases
   * @tparam T the union type of all the alrady given cases
   */
  final class MkTotalUnionReceive[U <: Union, T <: Union] private[TypedActor] (finalPf: PartialFunction[U, Unit]) {

    /** Adds a case to the final receive function */
    def on[A](f: PartialFunction[A, Unit])(implicit ev: A isPartOf U): MkTotalUnionReceive[U, T | A] = {
      val pf = new TypedReceiver[A](f).asInstanceOf[PartialFunction[U, Unit]]
      new MkTotalUnionReceive[U, T | A](finalPf orElse pf)
    }
    def total[A](f: A => Unit)(implicit ev: A isPartOf U): MkTotalUnionReceive[U, T | A] = on(PartialFunction(f))

    /** Returns the final receive function */
    def apply(implicit ev: T containsAllOf U): PartialFunction[U, Unit] =
      finalPf
  }
  object MkTotalUnionReceive {
    implicit def buildReceive[U <: Union, T <: Union](mk: MkTotalUnionReceive[U, T])(implicit ev: T containsAllOf U): PartialFunction[U, Unit] =
      mk.apply
  }

  /** Helper to define a new become behavior for a union sub type */
  final class TypedBecomeOnAux[U <: Union] private[TypedActor] (val self: Actor) extends AnyVal {

    /** become this new partial behavior */
    def on[A](f: PartialFunction[A, Unit])(implicit ev: A isPartOf U): Unit =
      self.context become LoggingReceive(new TypedReceiver(f))(self.context)

    /** become this new total behavior */
    def total[A](f: A ⇒ Unit)(implicit ev: A isPartOf U, ct: ClassTag[A]): Unit =
      on[A](new Downcast[A](self, ct.runtimeClass.asInstanceOf[Class[A]])(f))
  }

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

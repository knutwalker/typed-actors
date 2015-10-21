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

package de.knutwalker.akka

import _root_.akka.actor.{ Actor, ActorContext, ActorPath, ActorRefFactory, Deploy }
import _root_.akka.routing.RouterConfig
import akka.typedactors.AskSupport
import akka.util.Timeout

import scala.annotation.implicitNotFound
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * Compile-time wrapper for akka.actor.ActorRef to allow for same degree of
 * type-safety on actors without requiring too much changes to the underlying
 * system.
 *
 * Example:
 * {{{
 * import akka.actor._
 * import de.knutwalker.akka.typed._
 *
 * case class Ping(replyTo: ActorRef[Pong])
 * case class Pong(replyTo: ActorRef[Ping])
 *
 * implicit val system = ActorSystem()
 *
 * val ping: ActorRef[Ping] = ActorOf(PropsOf[Ping](new Actor {
 *   def receive: Receive = {
 *     case Ping(replyTo) ⇒ replyTo ! Pong(self.typed)
 *   }
 * }))
 * val pong: ActorRef[Pong] = ActorOf(PropsOf[Pong](new Actor {
 *   def receive: Receive = {
 *     case Pong(replyTo) ⇒ replyTo ! Ping(self.typed)
 *   }
 * }))
 *
 * ping ! Ping(pong)
 *
 *
 * system.shutdown()
 * }}}
 */
package object typed {

  type UntypedActorRef = akka.actor.ActorRef
  type UntypedProps = akka.actor.Props
  val UntypedProps = akka.actor.Props

  type ActorRef[A] = Tagged[UntypedActorRef, A]
  type Props[A] = Tagged[UntypedProps, A]

  /**
   * Creates a new typed Props that uses the default constructor of the given
   * actor type to create new instances of this actor.
   *
   * Wrapper for `akka.actor.Props[T]`
   *
   * @tparam A the message type this actor is receiving
   * @tparam T the actor type
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def Props[A, T <: Actor : ClassTag]: Props[A] =
    Props[A](akka.actor.Props[T])

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
   * Props(new Actor with Stash { ... })
   * }}}
   *
   * Instead you must create a named class that mixin the trait, e.g.
   *
   * {{{
   * class MyActor extends Actor with Stash
   * }}}
   *
   * Wrapper for `akka.actor.Props[T](=> T)`.
   *
   * @param creator the thunk that create the new instance of this actor
   * @tparam A the message type this actor is receiving
   * @tparam T the actor type
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def Props[A, T <: Actor : ClassTag](creator: ⇒ T): Props[A] =
    Props[A](akka.actor.Props[T](creator))

  /**
   * Creates a new typed Props that uses the given class and constructor
   * arguments to create instances of this actor.
   *
   * Wrapper for `akka.actor.Props[T](Class[T], Any*)`.
   *
   * @param clazz the class of this actor
   * @param args the constructor argumentes of this actor
   * @tparam A the message type this actor is receiving
   * @tparam T the actor type
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def Props[A, T <: Actor](clazz: Class[T], args: Any*): Props[A] =
    Props[A](akka.actor.Props(clazz, args: _*))

  /**
   * Creates a new typed Props that uses the given [[akka.actor.Props]] to
   * create instances of this actor.
   *
   * @param p the Props to use
   * @tparam A the message type this actor is receiving
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def Props[A](p: UntypedProps): Props[A] =
    tag(p)

  /**
   * Creates a new typed Props that uses the default constructor of the given
   * actor to create new instances of this typed actor.
   *
   * The message type is derived from the given actor.
   *
   * @tparam T the typed actor
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def PropsFor[T <: TypedActor : ClassTag]: Props[T#Message] =
    Props[T#Message, T]

  /**
   * Creates a new typed Props that uses the given creator function to create
   * instances of this typed actor.
   *
   * The message type is derived from the given actor.
   *
   * @see `Props` for some caveat about this constructor.
   * @param creator the thunk that create the new instance of this typed actor
   * @tparam T the typed actor
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def PropsFor[T <: TypedActor : ClassTag](creator: ⇒ T): Props[T#Message] =
    Props[T#Message, T](creator)

  /**
   * Creates a new typed Props that uses the given class and constructor
   * arguments to create instances of this typed actor.
   *
   * The message type is derived from the given actor.
   *
   * @param clazz the class of this typed actor
   * @param args the constructor argumentes of this actor
   * @tparam T the typed actor
   * @return a typed Props to create `ActorRef[A]`s for this actor
   */
  def PropsFor[T <: TypedActor](clazz: Class[T], args: Any*): Props[T#Message] =
    Props[T#Message, T](clazz, args: _*)

  /**
   * Type-curried creation of `Props[A]` to aid type inference.
   *
   * @example
   * {{{
   *  // message gets inferred as Nothing
   *  Props(new MyActor): Props[Nothing]
   *
   *
   *  // too verbose
   *  Props[MyMessage, MyActor](new MyActor): Props[MyMessage]
   *
   *
   *  // guided inference
   *  PropsOf[MyMessage](new MyActor): Props[MyMessage]
   * }}}
   * @tparam A the message type this actor is receiving
   * @return a class that created typed Props using one of the above methods
   */
  def PropsOf[A]: PropsBuilder[A] =
    new PropsBuilder[A](None)

  /**
   * Creates a new typed actor with the given name as a child of the
   * implicit [[akka.actor.ActorRefFactory]].
   *
   * @param p see [[Props]] for details on how to obtain a `Props` object
   * @param name the name of the actor.
   *             must not be null, empty or start with “$”.
   *             If the given name is already in use, an
   *             `InvalidActorNameException` is thrown.
   * @param factory the factory to create the actor. Within an actor itself,
   *                this is its `context`. For a toplevel actor, you need to
   *                put the `ActorSystem` into the implicit scope.
   * @throws akka.actor.InvalidActorNameException if the given name is
   *                                              invalid or already in use
   * @throws akka.ConfigurationException if deployment, dispatcher or
   *                                     mailbox configuration is wrong
   * @tparam A the message type this actor is receiving
   * @return the typed ActorRef[A] for this actor
   */
  def ActorOf[A](p: Props[A], name: String)(implicit factory: ActorRefFactory): ActorRef[A] =
    tag(factory.actorOf(untag(p), name))

  /**
   * Creates a new typed actor as a child of the implicit
   * [[akka.actor.ActorRefFactory]] and give it an automatically generated name.
   *
   * @param p see [[Props]] for details on how to obtain a `Props` object
   * @param factory the factory to create the actor. Within an actor itself,
   *                this is its `context`. For a toplevel actor, you need to
   *                put the `ActorSystem` into the implicit scope.
   * @throws akka.ConfigurationException if deployment, dispatcher or
   *                                     mailbox configuration is wrong
   * @tparam A the message type this actor is receiving
   * @return the typed ActorRef[A] for this actor
   */
  def ActorOf[A](p: Props[A])(implicit factory: ActorRefFactory): ActorRef[A] =
    tag(factory.actorOf(untag(p)))


  implicit final class PropsOps[A](val props: Props[A]) extends AnyVal {

    /** @see [[akka.actor.Props#dispatcher]] */
    def dispatcher: String =
      untyped.dispatcher

    /** @see [[akka.actor.Props#mailbox]] */
    def mailbox: String =
      untyped.mailbox

    /** @see [[akka.actor.Props#routerConfig]] */
    def routerConfig: RouterConfig =
      untyped.routerConfig

    /** @see [[akka.actor.Props#withDispatcher]] */
    def withDispatcher(d: String): Props[A] =
      tag(untyped.withDispatcher(d))

    /** @see [[akka.actor.Props#withMailbox]] */
    def withMailbox(m: String): Props[A] =
      tag(untyped.withMailbox(m))

    /** @see [[akka.actor.Props#withRouter]] */
    def withRouter(r: RouterConfig): Props[A] =
      tag(untyped.withRouter(r))

    /** @see [[akka.actor.Props#withDeploy]] */
    def withDeploy(d: Deploy): Props[A] =
      tag(untyped.withDeploy(d))

    /** @see [[akka.actor.Props#actorClass]] */
    def actorClass(): Class[_ <: Actor] =
      untyped.actorClass()

    /**
     * @return this typed Props as an untyped [[akka.actor.Props]].
     *         The returned instance is `eq` to `this`.
     */
    def untyped: UntypedProps =
      untag(props)

    def or[B]: Props[A | B] =
      retag(props)
  }

  implicit final class ActorRefOps[A](val ref: ActorRef[A]) extends AnyVal {

    /**
     * Sends a typed message asynchronously.
     *
     * @see [[akka.actor.ActorRef#tell]]
     */
    def !(msg: A)(implicit sender: UntypedActorRef = Actor.noSender): Unit =
      untyped ! msg

    /**
     * Ask a typed question asynchronously.
     * This signature enforces the `replyTo` pattern for keeping type safety.
     *
     * Instead of sending a message of `Any` and replying to an untyped `sender()`,
     * you supply a function that, given a typed sender, will return the message.
     * This is typically done with a second parameter list of a case class.
     *
     * {{{
     * case class MyMessage(payload: String)(val replyTo: ActorRef[MyResponse])
     *
     * class MyActor extends Actor {
     *   def receive = {
     *     case m@MyMessage(payload) => m.replyTo ! MyResponse(payload)
     *   }
     * }
     * }}}
     */
    def ?[B](f: ActorRef[B] ⇒ A)(implicit timeout: Timeout, ctA: ClassTag[A], sender: UntypedActorRef = Actor.noSender): Future[B] =
      AskSupport.ask[A, B](ref, f, timeout, ctA, sender)

    /** @see [[akka.actor.ActorRef#path]] */
    def path: ActorPath =
      untyped.path

    /** @see [[akka.actor.ActorRef#forward]] */
    def forward(msg: A)(implicit context: ActorContext): Unit =
      untyped.forward(msg)

    /**
     * Sends any message asynchronously, e.g. [[akka.actor.PoisonPill]].
     * This is the same as using tell on a untyped actor.
     *
     * @see [[akka.actor.ActorRef#tell]]
     */
    def unsafeTell(msg: Any)(implicit sender: UntypedActorRef = Actor.noSender): Unit =
      untyped ! msg

    /**
     * Returns this typed ActorRef as an untyped [[akka.actor.ActorRef]].
     * The returned instance is `eq` to `this`.
     */
    def untyped: UntypedActorRef =
      untag(ref)

    def or[B]: ActorRef[A | B] =
      retag(ref)
  }

  implicit final class ActorRefUnionedOps[U <: Union](val ref: ActorRef[U]) extends AnyVal {
    def ![A](msg: A)(implicit ev: A isPartOf U, sender: UntypedActorRef = Actor.noSender): Unit =
      untag(ref) ! msg

    def ?[A, B](f: ActorRef[B] ⇒ A)(implicit ev: A isPartOf U, timeout: Timeout, ctA: ClassTag[A], sender: UntypedActorRef = Actor.noSender): Future[B] =
      AskSupport.ask[A, B](retag(ref), f, timeout, ctA, sender)
  }

  implicit final class UntypedPropsOps(val untyped: UntypedProps) extends AnyVal {

    /**
     * Returns this Props as a typed [[Props]].
     * The returned instance is `eq` to `this`.
     * @tparam A the message type this actor will be receiving
     * @return the typed Props
     */
    def typed[A]: Props[A] =
      tag(untyped)
  }

  implicit final class UntypedActorRefOps(val untyped: UntypedActorRef) extends AnyVal {

    /**
     * Returns this Props as a typed [[ActorRef]].
     * The returned instance is `eq` to `this`.
     * @tparam A the message type this actor is receiving
     * @return the typed ActorRef
     */
    def typed[A]: ActorRef[A] =
      tag(untyped)
  }

  private type Tagged[A, T] = {
    type Message = T
    type Self = A
  }

  @inline private[typed] def tag[A, T](a: A): Tagged[A, T] =
    a.asInstanceOf[Tagged[A, T]]

  @inline private[typed] def untag[A, T](t: Tagged[A, T]): A =
    t.asInstanceOf[A]

  @inline private[this] def retag[A, B, T](a: Tagged[T, A]): Tagged[T, B] =
    a.asInstanceOf[Tagged[T, B]]
}

package typed {

  sealed trait Union
  sealed trait |[+A, +B] extends Union

  @implicitNotFound("Cannot send message of type ${A} since it is not a member of ${U}.")
  sealed trait isPartOf[A, U <: Union]
  object isPartOf {

    implicit def leftPart[A, ∅](implicit ev: A isNotA Union): isPartOf[A, A | ∅] =
      null

    implicit def rightPart[∅, B](implicit ev: B isNotA Union): isPartOf[B, ∅ | B] =
      null

    implicit def tailPart[H, A, T <: Union](implicit partOfTl: A isPartOf T): isPartOf[A, T | H] =
      null
  }

  sealed trait isNotA[A, B]
  object isNotA {
    implicit def nsub[A, B]: A isNotA B = null
    // $COVERAGE-OFF$
    implicit def nsubAmbig1[A, B >: A]: A isNotA B = sys.error("Unexpected invocation")
    implicit def nsubAmbig2[A, B >: A]: A isNotA B = sys.error("Unexpected invocation")
    // $COVERAGE-ON$
  }
}

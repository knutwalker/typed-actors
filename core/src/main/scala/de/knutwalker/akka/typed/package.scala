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

import _root_.akka.actor.{ Actor, ActorContext, ActorPath, ActorRefFactory }

import reflect.ClassTag

package object typed {

  type UntypedActorRef = akka.actor.ActorRef
  type UntypedProps = akka.actor.Props

  type ActorRef[A] = Tagged[UntypedActorRef, A]
  type Props[A] = Tagged[UntypedProps, A]

  def Props[A, T <: Actor : ClassTag]: Props[A] =
    Props[A](akka.actor.Props[T])

  def Props[A, T <: Actor : ClassTag](creator: ⇒ T): Props[A] =
    Props[A](akka.actor.Props[T](creator))

  def Props[A, T <: Actor](clazz: Class[T], args: Any*): Props[A] =
    Props[A](akka.actor.Props(clazz, args: _*))

  def Props[A](p: UntypedProps): Props[A] =
    tag(p)

  def ActorOf[A](p: Props[A], name: String)(implicit factory: ActorRefFactory): ActorRef[A] =
    tag(factory.actorOf(untag(p), name))

  def ActorOf[A](p: Props[A])(implicit factory: ActorRefFactory): ActorRef[A] =
    tag(factory.actorOf(untag(p)))

  implicit final class ActorRefOps[A](val ref: ActorRef[A]) extends AnyVal {

    def !(msg: A)(implicit sender: UntypedActorRef = Actor.noSender): Unit =
      untyped ! msg

    def path: ActorPath =
      untyped.path

    def forward[B >: A](msg: B)(implicit context: ActorContext): Unit =
      untyped.forward(msg)

    def unsafeTell(msg: Any)(implicit sender: UntypedActorRef = Actor.noSender): Unit =
      untyped ! msg

    private def untyped: UntypedActorRef =
      untag(ref)
  }

  private type Tagged[A, T] = {
    type Tag = T
    type Self = A
  }

  @inline private[typed] def tag[A, T](a: A): Tagged[A, T] =
    a.asInstanceOf[Tagged[A, T]]

  @inline private[typed] def untag[A, T](t: Tagged[A, T]): A =
    t.asInstanceOf[A]
}

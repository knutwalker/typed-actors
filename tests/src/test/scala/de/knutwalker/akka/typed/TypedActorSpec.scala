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

import akka.actor.{ UnhandledMessage, Inbox, ActorSystem }
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import scala.annotation.tailrec
import scala.concurrent.TimeoutException
import scala.concurrent.duration.Duration

import java.util.concurrent.TimeUnit

object TypedActorSpec extends Specification with AfterAll {
  sequential

  sealed trait MyFoo
  case object Foo extends MyFoo
  case object Bar extends MyFoo
  case object Qux

  implicit val system = ActorSystem("foo")
  val inbox = createInbox(system)
  system.eventStream.subscribe(inbox.getRef(), classOf[UnhandledMessage])

  // https://github.com/akka/akka/issues/15409
  @tailrec
  def createInbox(sys: ActorSystem): Inbox = {
    try Inbox.create(system) catch {
      case cee: ClassCastException ⇒ createInbox(sys)
    }
  }

  "The TypedActor" should {
    "have typed partial receive" >> {
      class MyActor extends TypedActor.Of[MyFoo] {
        def typedReceive = {
          case Foo => inbox.getRef() ! "received a foo"
        }
      }
      val ref = ActorOf(PropsFor(new MyActor))

      ref ! Foo
      expectMsg("received a foo")

      ref ! Bar
      expectUnhandled(Bar, ref)

      ref.untyped ! Qux
      expectUnhandled(Qux, ref)

      ref.untyped ! Nil
      expectUnhandled(Nil, ref)
    }

    "have typed total receive" >> {
      class MyActor extends TypedActor.Of[MyFoo] {
        def typedReceive = Total {
          case Foo => inbox.getRef() ! "received a foo"
          case Bar => inbox.getRef() ! "received a bar"
        }
      }
      val ref = ActorOf(PropsFor(new MyActor))

      ref ! Foo
      expectMsg("received a foo")

      ref ! Bar
      expectMsg("received a bar")

      ref.untyped ! Qux
      expectUnhandled(Qux, ref)

      ref.untyped ! Nil
      expectUnhandled(Nil, ref)
    }

    "have untyped partial receive" >> {
      class MyActor extends TypedActor.Of[MyFoo] {
        def typedReceive = Untyped {
          case Foo => inbox.getRef() ! "received a foo"
          case Bar => inbox.getRef() ! "received a bar"
          case Qux => inbox.getRef() ! "receive a qux"
        }
      }
      val ref = ActorOf(PropsFor(new MyActor))

      ref ! Foo
      expectMsg("received a foo")

      ref ! Bar
      expectMsg("received a bar")

      ref.untyped ! Qux
      expectMsg("receive a qux")

      ref.untyped ! Nil
      expectUnhandled(Nil, ref)
    }

    "have a quick apply method for total functions" >> {
      val ref = ActorOf(TypedActor[MyFoo] {
        case Foo => inbox.getRef() ! "received a foo"
        case Bar => inbox.getRef() ! "received a bar"
      })

      ref ! Foo
      expectMsg("received a foo")

      ref ! Bar
      expectMsg("received a bar")

      ref.untyped ! Qux
      expectUnhandled(Qux, ref)

      ref.untyped ! Nil
      expectUnhandled(Nil, ref)
    }

    "have a typed become" >> {
      class MyActor extends TypedActor.Of[MyFoo] {
        def typedReceive = {
          case Foo ⇒ typedBecome {
            case Bar ⇒ inbox.getRef() ! "received a bar"
          }
        }
      }
      val ref = ActorOf(PropsFor(new MyActor))

      ref ! Foo
      expectUnhandled(Foo, ref) must throwA[TimeoutException]

      ref ! Bar
      expectMsg("received a bar")

      ref.untyped ! Qux
      expectUnhandled(Qux, ref)

      ref.untyped ! Nil
      expectUnhandled(Nil, ref)
    }
  }

  def expectUnhandled(message: Any, ref: ActorRef[_]) =
    inbox.receive(Duration(10, TimeUnit.MILLISECONDS)) === UnhandledMessage(message, system.deadLetters, ref.untyped)

  def expectMsg(expected: Any) =
    inbox.receive(Duration(10, TimeUnit.MILLISECONDS)) === expected



  def afterAll(): Unit = Shutdown(system)
}

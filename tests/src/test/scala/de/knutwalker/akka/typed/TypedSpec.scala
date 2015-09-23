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

import akka.actor.{ Terminated, PoisonPill, ActorSystem, Inbox }
import akka.util.Timeout
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute._
import org.specs2.execute.Typecheck._
import org.specs2.matcher.Matcher
import org.specs2.matcher.TypecheckMatchers._
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import scala.annotation.tailrec
import scala.concurrent.TimeoutException
import scala.concurrent.duration._

object TypedSpec extends Specification with AfterAll {
  sequential

  sealed trait TestMessage
  case class Foo(msg: String) extends TestMessage
  case object Bar extends TestMessage
  case class Baz(msg: String)(val replyTo: ActorRef[SomeOtherMessage]) extends TestMessage

  case class SomeOtherMessage(msg: String)

  implicit val system = ActorSystem("test")
  val inbox    = createInbox(system)
  val inboxRef = inbox.getRef()

  // https://github.com/akka/akka/issues/15409
  @tailrec
  def createInbox(sys: ActorSystem): Inbox = {
    try Inbox.create(system) catch {
      case cee: ClassCastException ⇒ createInbox(sys)
    }
  }

  case class MyActor(name: String) extends TypedActor.Of[TestMessage] {
    def typedReceive: TypedReceive = {
      case Foo(msg)   ⇒ inboxRef ! Foo(s"$name: $msg")
      case Bar        ⇒ inboxRef ! Bar
      case m@Baz(msg) ⇒ m.replyTo ! SomeOtherMessage(s"$name: $msg")
    }
  }

  "A compile-time wrapper around actors" should {
    val ref: ActorRef[TestMessage] = Typed[MyActor].create("Bernd")

    "only accept the defined message" >> {
      ref ! Foo("foo")
      inbox.receive(1.second) === Foo("Bernd: foo")
    }

    "fail to compile if the wrong message type is sent" >> {
      typecheck {
        """ ref ! "some other message" """
      } must not succeed
    }

    "have the same runtime representation as regular actors" >> {
      ref must beAnInstanceOf[akka.actor.ActorRef]
      ref.untyped must beTheSameAs (ref.asInstanceOf[akka.actor.ActorRef])
    }
  }

  "ask support of typed actors" should {
    implicit val timeout: Timeout = 100.millis

    "ask the question and the return a future" >> { implicit ee: ExecutionEnv ⇒
      val ref = ActorOf(TypedActor[Baz](m ⇒ m.replyTo ! SomeOtherMessage(m.msg)))
      (ref ? Baz("foo")) must be_==(SomeOtherMessage("foo")).await
    }

    "respect the timeout" >> { implicit ee: ExecutionEnv ⇒
      val ref = ActorOf(TypedActor[Baz](_ ⇒ ()))
      val expectedMessage = s"Ask timed out on [$ref] after [100 ms]"
      patchedAwait(ref)(be_===(expectedMessage))
    }

    "fail with an invalid timeout" >> { implicit ee: ExecutionEnv ⇒
      val ref = ActorOf(TypedActor[Baz](m ⇒ m.replyTo ! SomeOtherMessage(m.msg)))
      implicit val timeout: Timeout = -100.millis
      (ref ? Baz("foo")) must throwAn[IllegalArgumentException].like {
        case e ⇒ e.getMessage must startWith("Timeout length must not be negative, question not sent")
      }.await
    }

    "fail when the target is already terminated" >> { implicit ee: ExecutionEnv ⇒
      val ref = kill(ActorOf(TypedActor[Baz](m ⇒ m.replyTo ! SomeOtherMessage(m.msg))))
      val expectedMessage = s"Recipient[$ref] had already been terminated."
      patchedAwait(ref)(startWith(expectedMessage))
    }
  }

  def patchedAwait(ref: ActorRef[Baz])(m: ⇒ Matcher[String])(implicit timeout: Timeout): Result = {
    // cant use .await matcher since s.c.Await.result throws a TimeoutException
    // and akka AskTimeout is <: TimeoutException as well
    // see https://github.com/etorreborre/specs2/pull/417
    try {
      scala.concurrent.Await.result(ref ? Baz("foo"), 1.second)
      Success()
    } catch {
      // check if exception is a direct instance of TimeoutException (await timeout)
      // and not of a subclass (akka timeout)
      case te: TimeoutException if te.getClass == classOf[TimeoutException] ⇒
        Failure(s"Timeout after 1 second")
      case other: Throwable ⇒
        createExpectable(other.getMessage).applyMatcher(m).toResult
    }
  }

  def kill(ref: ActorRef[_]): ref.type = {
    val untyped = ref.untyped
    inbox.watch(untyped)
    untyped ! PoisonPill
    val Terminated(`untyped`) = inbox.receive(100.millis)
    ref
  }

  def afterAll(): Unit = system.shutdown()
}

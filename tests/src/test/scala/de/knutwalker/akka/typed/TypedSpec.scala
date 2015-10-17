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

import akka.actor.{ Actor, Deploy, Terminated, PoisonPill, ActorSystem, Inbox }
import akka.pattern.AskTimeoutException
import akka.routing.NoRouter
import akka.util.Timeout
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute._
import org.specs2.execute.Typecheck._
import org.specs2.matcher.Matcher
import org.specs2.matcher.TypecheckMatchers._
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import scala.annotation.tailrec
import scala.concurrent.{ Await, TimeoutException }
import scala.concurrent.duration._

import java.util.regex.Pattern

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

  class TestActor extends TypedActor.Of[TestMessage] {
    def typedReceive: TypedReceive = Total(_ ⇒ ())
  }

  class AnotherActor extends Actor {
    def receive = {
      case Foo(msg)              ⇒ self.typed[SomeOtherMessage].forward(SomeOtherMessage(msg))
      case SomeOtherMessage(msg) ⇒ inboxRef ! SomeOtherMessage(msg)
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
      ref must beTheSameAs (ref.untyped.typed[TestMessage])
    }
  }

  "ask support of typed actors" should {
    implicit val timeout: Timeout = 100.millis

    "ask the question and the return a future" >> { implicit ee: ExecutionEnv ⇒
      val ref = ActorOf(TypedActor[Baz](m ⇒ m.replyTo ! SomeOtherMessage(m.msg)))
      (ref ? Baz("foo")) must be_==(SomeOtherMessage("foo")).await
    }

    "respect the timeout" >> { implicit ee: ExecutionEnv ⇒
      val ref = ActorOf(TypedActor[Baz](_ ⇒ ()), "discarder")
      val expectedMessage = TimeoutMessage(ref)
      val matchMessage = s"^${Pattern.quote(expectedMessage)}$$"
      (ref ? Baz("foo")) must throwAn[AskTimeoutException](message = matchMessage).await
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
      val matchMessage = s"^${Pattern.quote(expectedMessage)}.*$$"
      (ref ? Baz("foo")) must throwAn[AskTimeoutException](message = matchMessage).await
    }
  }

  "further ops and syntax of typed actors" >> {
    val ref = ActorOf(Props[Foo, AnotherActor])

    "forward" >> {
      ref ! Foo("foo")
      inbox.receive(1.second) === SomeOtherMessage("foo")
    }

    "unsafeTell" >> {
      ref.unsafeTell(SomeOtherMessage("foo"))
      inbox.receive(1.second) === SomeOtherMessage("foo")
    }

    "path" >> {
      ref.path must beTheSameAs (ref.untyped.path)
    }
  }

  "Props builders" should {

    "simple props apply for zero-arg constructors" >> {

      val byProps = Props[TestMessage, TestActor]
      val byPropsFor = PropsFor[TestActor]
      val byPropsOf = PropsOf[TestMessage][TestActor]
      val byAkka = UntypedProps[TestActor]

      byProps must be_===(byPropsOf)
      byProps must be_===(byPropsFor)
      byProps must be_==(byAkka)
    }

    "props apply with closure" >> {

      def inner(creator: ⇒ MyActor) = {
        val byProps = Props[TestMessage, MyActor](creator)
        val byPropsFor = PropsFor[MyActor](creator)
        val byPropsOf = PropsOf[TestMessage](creator)
        val byAkka = UntypedProps(creator)

        byProps must be_===(byPropsOf)
        byProps must be_===(byPropsFor)
        byProps must be_==(byAkka)
      }

      inner(new MyActor("Bernd"))
    }

    "reflection based props apply" >> {

      val byProps = Props[TestMessage, MyActor](classOf[MyActor], "Bernd")
      val byPropsFor = PropsFor[MyActor](classOf[MyActor], "Bernd")
      val byPropsOf = PropsOf[TestMessage](classOf[MyActor], "Bernd")
      val byAkka = UntypedProps(classOf[MyActor], "Bernd")

      byProps must be_===(byPropsOf)
      byProps must be_===(byPropsFor)
      byProps must be_==(byAkka)
    }
  }

  "Ops syntax for typed props" should {
    val props: Props[TestMessage] = Typed[MyActor].props("Bernd")
    val untyped = props.untyped

    "dispatcher" >> {
      props.dispatcher must beTheSameAs (untyped.dispatcher)
    }

    "mailbox" >> {
      props.mailbox must beTheSameAs (untyped.mailbox)
    }

    "routerConfig" >> {
      props.routerConfig must beTheSameAs (untyped.routerConfig)
    }

    "actorClass()" >> {
      props.actorClass() must beTheSameAs (untyped.actorClass())
    }

    "withDispatcher()" >> {
      props.withDispatcher("foo").untyped must be_=== (untyped.withDispatcher("foo"))
      props.withDispatcher("foo") must be_=== (untyped.withDispatcher("foo").typed)
    }

    "withMailbox()" >> {
      props.withMailbox("foo").untyped must be_=== (untyped.withMailbox("foo"))
      props.withMailbox("foo") must be_=== (untyped.withMailbox("foo").typed)
    }

    "withRouter()" >> {
      props.withRouter(NoRouter).untyped must be_=== (untyped.withRouter(NoRouter))
      props.withRouter(NoRouter) must be_=== (untyped.withRouter(NoRouter).typed)
    }

    "withDeploy()" >> {
      props.withDeploy(Deploy("foo")).untyped must be_=== (untyped.withDeploy(Deploy("foo")))
      props.withDeploy(Deploy("foo")) must be_=== (untyped.withDeploy(Deploy("foo")).typed)
    }
  }

  def kill(ref: ActorRef[_]): ref.type = {
    val untyped = ref.untyped
    inbox.watch(untyped)
    untyped ! PoisonPill
    val Terminated(`untyped`) = inbox.receive(100.millis)
    ref
  }

  def afterAll(): Unit = Shutdown(system)
}

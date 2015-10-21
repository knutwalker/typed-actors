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

import akka.actor.ActorSystem
import akka.util.Timeout
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute._
import org.specs2.execute.Typecheck._
import org.specs2.matcher.TypecheckMatchers._
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import scala.concurrent.duration._


object UnionSpec extends Specification with AfterAll {
  sequential

  case class Foo(msg: String)
  case object Bar
  case class Baz(msg: String)(val replyTo: ActorRef[SomeOtherMessage])

  case class SomeOtherMessage(msg: String)

  implicit val system = ActorSystem("test")
  val inbox    = CreateInbox()
  val inboxRef = inbox.getRef()

  case class MyActor(name: String) extends TypedActor.Of[Foo] {
    def typedReceive = Untyped {
      case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
      case Bar      ⇒ inboxRef ! Bar
      case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg)
    }
  }

  "Type unions on actors" should {
    val fooProps: Props[Foo] = PropsFor(new MyActor("Bernd"))
    val fooOrBarProps: Props[Foo | Bar.type] = fooProps.or[Bar.type]
    val fooOrBarRef: ActorRef[Foo | Bar.type] = ActorOf(fooOrBarProps)
    val ref: ActorRef[Foo | Bar.type | Baz] = fooOrBarRef.or[Baz]

    "accept a foo message" >> {
      ref ! Foo("foo")
      inbox.receive(1.second) === Foo("Bernd: foo")
    }

    "accept a bar message" >> {
      ref ! Bar
      inbox.receive(1.second) === Bar
    }

    "accept a baz message" >> {
      ref ! Baz("baz")(inboxRef.typed)
      inbox.receive(1.second) === SomeOtherMessage("baz")
    }

    "fail to compile if the wrong message type is sent" >> {
      typecheck {
        """ ref ! SomeOtherMessage("some other message") """
      } must not succeed
    }

    "support ask" >> { implicit ee: ExecutionEnv ⇒
      implicit val timeout: Timeout = 100.millis
      (ref ? Baz("foo")) must be_==(SomeOtherMessage("foo")).await
    }
  }

  def afterAll(): Unit = Shutdown(system)
}

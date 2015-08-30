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

import akka.actor.{ ActorSystem, Inbox }
import org.specs2.execute._
import org.specs2.execute.Typecheck._
import org.specs2.matcher.TypecheckMatchers._
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import scala.concurrent.duration._

object TypedSpec extends Specification with AfterAll {

  sealed trait TestMessage
  case class Foo(msg: String) extends TestMessage
  case object Bar extends TestMessage

  implicit val system = ActorSystem("test")
  val inbox    = Inbox.create(system)
  val inboxRef = inbox.getRef()

  case class MyActor(name: String) extends TypedActor.Of[TestMessage] {
    def receiveMsg(msg: TestMessage): Unit = msg match {
      case Foo(m1) ⇒ inboxRef ! Foo(s"$name: $m1")
      case Bar     ⇒ inboxRef ! Bar
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
    }
  }

  def afterAll(): Unit = system.shutdown()
}

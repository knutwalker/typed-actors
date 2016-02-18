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

import de.knutwalker.TripleArrow
import de.knutwalker.union._

import akka.actor.{ UnhandledMessage, ActorSystem }
import akka.util.Timeout
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import shapeless.test.illTyped

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit


object UnionSpec extends Specification with AfterAll with TripleArrow {
  sequential

  case class Foo(msg: String)
  case object Bar
  case class Baz(msg: String)(val replyTo: ActorRef[SomeOtherMessage])

  case class SomeOtherMessage(msg: String)

  implicit val system = ActorSystem("test")
  val inbox    = CreateInbox()
  val inboxRef = inbox.getRef()
  system.eventStream.subscribe(inboxRef, classOf[UnhandledMessage])

  "Type unions on actors" should {
    class MyActor(name: String) extends TypedActor.Of[Foo] {
      def typedReceive = Untyped {
        case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
        case Bar      ⇒ inboxRef ! Bar
        case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg)
      }
    }

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

    "fail to compile if the wrong message type is sent" >>> {
      illTyped(
        """ ref ! SomeOtherMessage("some other message") """,
        "de.knutwalker.akka.typed.UnionSpec.SomeOtherMessage is not in \\{de.knutwalker.akka.typed.UnionSpec.Foo \\| de.knutwalker.akka.typed.UnionSpec.Bar.type \\| de.knutwalker.akka.typed.UnionSpec.Baz\\}.")
    }

    "support ask" >> { implicit ee: ExecutionEnv ⇒
      implicit val timeout: Timeout = (100 * ee.timeFactor).millis
      (ref ? Baz("foo")) must be_==(SomeOtherMessage("foo")).await
    }

    "support retagging to a different type" >> {

      "if it is a subtype of the defined union" >> {
        ref.only[Foo] ! Foo("foo")
        inbox.receive(1.second) === Foo("Bernd: foo")
      }

      "which disables all other subcases" >>> {
        illTyped(
          """ ref.only[Foo] ! Bar """,
          "(?s).*type mismatch.*found.*Bar.type.*required.*Foo.*")
      }

      "fails if the type is unrelated" >>> {
        illTyped(
          """ ref.only[SomeOtherMessage] ! Bar """,
          "de.knutwalker.akka.typed.UnionSpec.SomeOtherMessage is not in \\{de.knutwalker.akka.typed.UnionSpec.Foo \\| de.knutwalker.akka.typed.UnionSpec.Bar.type \\| de.knutwalker.akka.typed.UnionSpec.Baz\\}.")
      }
    }
  }

  "A unioned TypedActor" should {
    "the Union helper" should {

      class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
        def typedReceive: TypedReceive = Union {
          case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
          case Bar      ⇒ inboxRef ! Bar
          case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg)
        }
      }

      val props: Props[Foo | Bar.type | Baz] = PropsFor(new MyActor("Bernd"))
      val ref: ActorRef[Foo | Bar.type | Baz] = ActorOf(props)

      "allow partial definition" >>> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          def typedReceive: TypedReceive = Union {
            case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
          }
        }
      }

      "allow using total handler" >>> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          def typedReceive: TypedReceive = Union.total[Foo]{
            case foo: Foo ⇒ inboxRef ! Foo(s"$name: ${foo.msg}")
          }
        }
      }

      "allow using two total handlers" >>> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          def typedReceive: TypedReceive = Union.total[Foo | Bar.type]{
            case foo: Foo ⇒ inboxRef ! Foo(s"$name: ${foo.msg}")
            case Bar      ⇒ inboxRef ! Bar
          }
        }
      }

      "only allow defined parts" >>> {
        illTyped(
          """
          class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
            def typedReceive: TypedReceive = Union {
              case msg: String ⇒ inboxRef ! Foo(s"$name: $msg")
            }
          }
          """,
          "Pattern involving \\[String\\] is not covered by union \\{de.knutwalker.akka.typed.UnionSpec.Foo \\| de.knutwalker.akka.typed.UnionSpec.Bar.type \\| de.knutwalker.akka.typed.UnionSpec.Baz\\}."
        )
      }

      "not require apply" >> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          val typedReceive: TypedReceive = Union {
            case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
          }
        }
        ActorOf(PropsFor(new MyActor("Bernd"))) must not beNull
      }

      "not infer apply when not all requirement are met" >>> {
        illTyped(
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = Union
            }
          """,
          "(?s).*required: .*TypedReceive.*"
        )
      }

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

      "fail to compile if the wrong message type is sent" >>> {
        illTyped(
          """ ref ! SomeOtherMessage("some other message") """,
          "de.knutwalker.akka.typed.UnionSpec.SomeOtherMessage is not in \\{de.knutwalker.akka.typed.UnionSpec.Foo \\| de.knutwalker.akka.typed.UnionSpec.Bar.type \\| de.knutwalker.akka.typed.UnionSpec.Baz\\}.")
      }

      "support ask" >> { implicit ee: ExecutionEnv ⇒
        implicit val timeout: Timeout = (100 * ee.timeFactor).millis
        (ref ? Baz("foo")) must be_==(SomeOtherMessage("foo")).await
      }

      "fail to compile when total handler is not exhaustive" >>> {
        illTyped(
          """
          class MyActor extends TypedActor.Of[Option[Foo] | Bar.type] {
            def typedReceive: TypedReceive = Union.total[Option[Foo]] {
              case Some(Foo("foo")) => ()
            }
          }
          """,
          "The patterns for Option\\[de.knutwalker.akka.typed.UnionSpec.Foo\\] are not exhaustive; It would fail on the following inputs: None, Some\\(_\\)..*"
        )
      }

      "not fail when the total doesn't match everything" >> {implicit ee: ExecutionEnv ⇒
        class MyActor extends TypedActor.Of[Foo | Bar.type] {
          def typedReceive: TypedReceive = Union.total[Foo] {
            case Foo("foo") ⇒ inboxRef ! Foo("foo")
          }
        }
        val ref = ActorOf(PropsFor(new MyActor))

        ref ! Foo("foo")
        expectMsg(Foo("foo"))

        ref ! Foo("baz")
        expectUnhandled(Foo("baz"), ref)
      }
    }

    "the TotalUnion helper" should {

      class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
        def typedReceive: TypedReceive = TotalUnion {
          case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
          case Bar      ⇒ inboxRef ! Bar
          case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg)
        }
      }

      val props: Props[Foo | Bar.type | Baz] = PropsFor(new MyActor("Bernd"))
      val ref: ActorRef[Foo | Bar.type | Baz] = ActorOf(props)

      "allow total definition" >>> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          def typedReceive: TypedReceive = TotalUnion {
            case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
            case Bar      ⇒ inboxRef ! Bar
            case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg)
          }
        }
      }

      "allow one total handler" >>> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          def typedReceive: TypedReceive = TotalUnion.total[Bar.type] {
            case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
            case Bar      ⇒ inboxRef ! Bar
            case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg)
          }
        }
      }

      "allow all total handlers" >>> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          def typedReceive: TypedReceive = TotalUnion.total {
            case Foo(msg) ⇒ inboxRef ! Foo(s"$name: foo.msg")
            case Bar      ⇒ inboxRef ! Bar
            case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg)
          }
        }
      }

      "fail with just one part" >>> {
        illTyped(
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion {
                case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
              }
            }
          """,
          "The partial function fails to match on these types: \\{de.knutwalker.akka.typed.UnionSpec.Bar.type \\| de.knutwalker.akka.typed.UnionSpec.Baz\\}."
        )
      }

      "require all definitions" >>> {
        illTyped(
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion {
                case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
                case Bar ⇒ inboxRef ! Bar
              }
            }
          """,
          "The partial function fails to match on de.knutwalker.akka.typed.UnionSpec.Baz."
        )
      }

      "only allow defined parts" >>> {
        illTyped(
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion {
                case msg: String ⇒ inboxRef ! Foo(s"$name: $msg")
              }
            }
          """,
          "Pattern involving \\[String\\] is not covered by union \\{de.knutwalker.akka.typed.UnionSpec.Foo \\| de.knutwalker.akka.typed.UnionSpec.Bar.type \\| de.knutwalker.akka.typed.UnionSpec.Baz\\}."
        )
      }

      "not require apply" >> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          val typedReceive: TypedReceive = TotalUnion {
            case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg")
            case Bar      ⇒ inboxRef ! Bar
            case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg)
          }
        }
        ActorOf(PropsFor(new MyActor("Bernd"))) must not beNull
      }

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

      "fail to compile if the wrong message type is sent" >>> {
        illTyped(
          """ ref ! SomeOtherMessage("some other message") """,
          "de.knutwalker.akka.typed.UnionSpec.SomeOtherMessage is not in \\{de.knutwalker.akka.typed.UnionSpec.Foo \\| de.knutwalker.akka.typed.UnionSpec.Bar.type \\| de.knutwalker.akka.typed.UnionSpec.Baz\\}.")
      }

      "support ask" >> { implicit ee: ExecutionEnv ⇒
        implicit val timeout: Timeout = (100 * ee.timeFactor).millis
        (ref ? Baz("foo")) must be_==(SomeOtherMessage("foo")).await
      }
    }

    "unionBecome for subcase of union types" should {

      class MyActor extends TypedActor.Of[Foo | Bar.type | Baz] {
        def typedReceive: TypedReceive = Union {
          case Foo(msg) ⇒
            inboxRef ! s"foo: $msg"
            typedBecome(Union {
              case Bar ⇒
                inboxRef ! Bar
                typedBecome(Union {
                  case baz: Baz ⇒ baz.replyTo ! SomeOtherMessage(baz.msg)
                })
            })
        }
      }
      val ref = ActorOf(PropsFor(new MyActor))

      "allow to change behavior" >> {implicit ee: ExecutionEnv ⇒
        val bazMsg = Baz("baz")(inboxRef.typed)

        ref ! Bar
        expectUnhandled(Bar, ref)

        ref ! bazMsg
        expectUnhandled(bazMsg, ref)

        ref ! Foo("foo")
        expectMsg("foo: foo")

        // first become

        ref ! Foo("foo")
        expectUnhandled(Foo("foo"), ref)

        ref ! bazMsg
        expectUnhandled(bazMsg, ref)

        ref ! Bar
        expectMsg(Bar)

        // second become

        ref ! Foo("foo")
        expectUnhandled(Foo("foo"), ref)

        ref ! Bar
        expectUnhandled(Bar, ref)

        ref ! bazMsg
        expectMsg(SomeOtherMessage("baz"))
      }

      "fail to compile when message is not part of the union" >>> {
        illTyped(
          """
            class MyActor extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = Union {
                case Foo(msg) ⇒ typedBecome(Union {
                  case x: SomeOtherMessage => ()
                })
              }
             }
          """,
          ".*Pattern involving \\[de.knutwalker.akka.typed.UnionSpec.SomeOtherMessage\\] is not covered by union \\{de.knutwalker.akka.typed.UnionSpec.Foo \\| de.knutwalker.akka.typed.UnionSpec.Bar.type \\| de.knutwalker.akka.typed.UnionSpec.Baz\\}..*"
        )
      }

      "fail to compile when total handler is not exhaustive" >>> {
        illTyped(
          """
            class MyActor extends TypedActor.Of[Option[Foo] | Bar.type] {
              def typedReceive: TypedReceive = Union {
                case Bar ⇒ typedBecome(Union.total[Option[Foo]] {
                  case Some(Foo("foo")) => ()
                })
              }
            }
          """,
          ".*The patterns for Option\\[de.knutwalker.akka.typed.UnionSpec.Foo\\] are not exhaustive; It would fail on the following inputs: None, Some\\(_\\)..*"
        )
      }

      "not fail when the unoinBecome.total doesnt match everything" >> {implicit ee: ExecutionEnv ⇒
        class MyActor extends TypedActor.Of[Foo | Bar.type] {
          def typedReceive: TypedReceive = Union {
            case Bar ⇒
              inboxRef ! Bar
              typedBecome(Union {
                case Foo("foo") ⇒ inboxRef ! Foo("foo")
              })
          }
        }
        val ref = ActorOf(PropsFor(new MyActor))

        ref ! Bar
        expectMsg(Bar)

        ref ! Foo("baz")
        expectUnhandled(Foo("baz"), ref)
      }
    }
  }

  def expectUnhandled(message: Any, ref: ActorRef[_])(implicit ee: ExecutionEnv) = {
    val receiveTimeout = Duration(100L * ee.timeFactor.toLong, TimeUnit.MILLISECONDS)
    inbox.receive(receiveTimeout) === UnhandledMessage(message, system.deadLetters, ref.untyped)
  }

  def expectMsg(expected: Any)(implicit ee: ExecutionEnv) = {
    val receiveTimeout = Duration(100L * ee.timeFactor.toLong, TimeUnit.MILLISECONDS)
    inbox.receive(receiveTimeout) === expected
  }

  def afterAll(): Unit = Shutdown(system)
}

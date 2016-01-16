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

import akka.actor.{ UnhandledMessage, ActorSystem }
import akka.util.Timeout
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute._
import org.specs2.execute.Typecheck._
import org.specs2.matcher.TypecheckMatchers._
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.matching.Regex
import java.util.concurrent.TimeUnit


object UnionSpec extends Specification with AfterAll {
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

    "fail to compile if the wrong message type is sent" >> {
      typecheck {
        """ ref ! SomeOtherMessage("some other message") """
      } must not succeed
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

      "which disables all other subcases" >> {
        typecheck {
          """ ref.only[Foo] ! Bar """
        } must not succeed
      }

      "fails if the type is unrelated" >> {
        typecheck {
          """ ref.only[SomeOtherMessage] ! Bar """
        } must not succeed
      }
    }
  }

  "A unioned TypedActor" should {
    "the Union helper" should {

      class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
        def typedReceive: TypedReceive = Union
        .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
        .on[Bar.type]{ case Bar ⇒ inboxRef ! Bar }
        .on[Baz]{ case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg) }
        .apply
      }

      val props: Props[Foo | Bar.type | Baz] = PropsFor(new MyActor("Bernd"))
      val ref: ActorRef[Foo | Bar.type | Baz] = ActorOf(props)

      "allow partial definition" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = Union
                .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
                .apply
            }
          """
        } must succeed
      }

      "allow using total handler" >> {
        val ct = implicitly[ClassTag[Foo]]
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = Union
                .total[Foo]{ foo ⇒ inboxRef ! Foo(s"$name: $foo.msg") }(implicitly, ct)
                .apply
            }
          """
        } must succeed
      }

      "allow using two total handlers" >> {
        val ctFoo = implicitly[ClassTag[Foo]]
        val ctBar = implicitly[ClassTag[Bar.type]]
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = Union
                .total[Foo]{ foo ⇒ inboxRef ! Foo(s"$name: $foo.msg") }(implicitly, ctFoo)
                .total[Bar.type] ( inboxRef ! _ )(implicitly, ctBar)
                .apply
            }
          """
        } must succeed
      }

      "require at least one sub path" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = Union
                .apply
            }
          """
        } must failWith(Regex.quote("Cannot prove that de.knutwalker.akka.typed.TypedActor.MkPartialUnionReceive.Empty =:= de.knutwalker.akka.typed.TypedActor.MkPartialUnionReceive.NonEmpty."))
      }

      "only allow defined parts" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = Union
                .on[String]{ case msg ⇒ inboxRef ! Foo(s"$name: $msg") }
                .apply
            }
          """
        } must failWith(Regex.quote("Cannot prove that message of type String is a member of de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.UnionSpec.Foo,de.knutwalker.akka.typed.UnionSpec.Bar.type],de.knutwalker.akka.typed.UnionSpec.Baz]."))
      }

      "not require apply" >> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          val typedReceive: TypedReceive = Union
            .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
        }
        ActorOf(PropsFor(new MyActor("Bernd"))) must not beNull
      }

      "not infer apply when not all requirement are met" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = Union
            }
          """
        } must failWith("required: .*TypedReceive")
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

      "fail to compile if the wrong message type is sent" >> {
        typecheck {
          """ ref ! SomeOtherMessage("some other message") """
        } must not succeed
      }

      "support ask" >> { implicit ee: ExecutionEnv ⇒
        implicit val timeout: Timeout = (100 * ee.timeFactor).millis
        (ref ? Baz("foo")) must be_==(SomeOtherMessage("foo")).await
      }

      "fail to compile when total handler is not exhaustive" >> {
        val ct = implicitly[ClassTag[Option[Foo]]]
        typecheck {
          """
            class MyActor extends TypedActor.Of[Option[Foo] | Bar.type] {
              def typedReceive: TypedReceive = Union
                .total[Option[Foo]] {
                  case Some(Foo("foo")) => ()
                }
            }
          """
        } must failWith(Regex.quote("match may not be exhaustive.\nIt would fail on the following inputs: None, Some(_)"))
      }.pendingUntilFixed("succeeds... possibly missing fatal warnings or similar")

      "not fail when the total doesn't match everything" >> {implicit ee: ExecutionEnv ⇒
        class MyActor extends TypedActor.Of[Foo | Bar.type] {
          def typedReceive: TypedReceive = Union.total[Foo] {
            case Foo("foo") ⇒ inboxRef ! Foo("foo")
          }.apply
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
        def typedReceive: TypedReceive = TotalUnion
        .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
        .on[Bar.type]{ case Bar ⇒ inboxRef ! Bar }
        .on[Baz]{ case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg) }
        .apply
      }

      val props: Props[Foo | Bar.type | Baz] = PropsFor(new MyActor("Bernd"))
      val ref: ActorRef[Foo | Bar.type | Baz] = ActorOf(props)

      "allow total definition" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion
                .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
                .on[Bar.type]{ case Bar ⇒ inboxRef ! Bar }
                .on[Baz]{ case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg) }
                .apply
            }
          """
        } must succeed
      }

      "allow one total handler" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion
                .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
                .total[Bar.type]{ inboxRef ! _ }
                .on[Baz]{ case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg) }
                .apply
            }
          """
        } must succeed
      }

      "allow all total handlers" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion
                .total[Foo]{ case Foo(msg) => inboxRef ! Foo(s"$name: foo.msg") }
                .total[Bar.type]( inboxRef ! _ )
                .total[Baz]{ m => m.replyTo ! SomeOtherMessage(m.msg) }
                .apply
            }
          """
        } must succeed
      }

      "fail without any parts" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion
                .apply
            }
          """
        } must failWith(Regex.quote("value apply is not a member of de.knutwalker.akka.typed.TypedActor.MkTotalUnionReceiveEmpty[de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.UnionSpec.Foo,de.knutwalker.akka.typed.UnionSpec.Bar.type],de.knutwalker.akka.typed.UnionSpec.Baz]]"))
      }

      "fail with just one part" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion
                .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
                .apply
            }
          """
        } must failWith(Regex.quote("value apply is not a member of de.knutwalker.akka.typed.TypedActor.MkTotalUnionReceiveHalfEmpty[de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.UnionSpec.Foo,de.knutwalker.akka.typed.UnionSpec.Bar.type],de.knutwalker.akka.typed.UnionSpec.Baz],de.knutwalker.akka.typed.UnionSpec.Foo]"))
      }

      "require all definitions" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion
                .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
                .on[Bar.type]{ case Bar ⇒ inboxRef ! Bar }
                .apply
            }
          """
        } must failWith(Regex.quote("Cannot prove that de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.UnionSpec.Foo,de.knutwalker.akka.typed.UnionSpec.Bar.type] contains the same members as de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.UnionSpec.Foo,de.knutwalker.akka.typed.UnionSpec.Bar.type],de.knutwalker.akka.typed.UnionSpec.Baz]."))
      }

      "only allow defined parts" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion
                .on[String]{ case msg ⇒ inboxRef ! Foo(s"$name: $msg") }
                .apply
            }
          """
        } must failWith(Regex.quote("Cannot prove that message of type String is a member of de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.UnionSpec.Foo,de.knutwalker.akka.typed.UnionSpec.Bar.type],de.knutwalker.akka.typed.UnionSpec.Baz]."))
      }

      "not require apply" >> {
        class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
          val typedReceive: TypedReceive = TotalUnion
            .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
            .on[Bar.type]{ case Bar ⇒ inboxRef ! Bar }
            .on[Baz]{ case m: Baz   ⇒ m.replyTo ! SomeOtherMessage(m.msg) }
        }
        ActorOf(PropsFor(new MyActor("Bernd"))) must not beNull
      }

      "not infer apply when not all requirement are met" >> {
        typecheck {
          """
            class MyActor(name: String) extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = TotalUnion
                .on[Foo]{ case Foo(msg) ⇒ inboxRef ! Foo(s"$name: $msg") }
                .on[Bar.type]{ case Bar ⇒ inboxRef ! Bar }
            }
          """
        } must failWith("required: .*TypedReceive")
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

      "fail to compile if the wrong message type is sent" >> {
        typecheck {
          """ ref ! SomeOtherMessage("some other message") """
        } must not succeed
      }

      "support ask" >> { implicit ee: ExecutionEnv ⇒
        implicit val timeout: Timeout = (100 * ee.timeFactor).millis
        (ref ? Baz("foo")) must be_==(SomeOtherMessage("foo")).await
      }
    }

    "unionBecome for subcase of union types" should {

      class MyActor extends TypedActor.Of[Foo | Bar.type | Baz] {
        def typedReceive: TypedReceive = Union.on[Foo]{
          case Foo(msg) ⇒
            inboxRef ! s"foo: $msg"
            unionBecome.on[Bar.type] {
              case Bar ⇒
                inboxRef ! Bar
                unionBecome.total[Baz] {
                  baz ⇒ baz.replyTo ! SomeOtherMessage(baz.msg)
                }
            }
        }.apply
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

      "fail to compile when message is not part of the union" >> {
        typecheck {
          """
            class MyActor extends TypedActor.Of[Foo | Bar.type | Baz] {
              def typedReceive: TypedReceive = Union.on[Foo]{
                case Foo(msg) ⇒ unionBecome.on[SomeOtherMessage] {
                  case x => ()
                }
              }.apply
             }
          """
        } must failWith(Regex.quote("Cannot prove that message of type de.knutwalker.akka.typed.UnionSpec.SomeOtherMessage is a member of de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.UnionSpec.Foo,de.knutwalker.akka.typed.UnionSpec.Bar.type],de.knutwalker.akka.typed.UnionSpec.Baz]."))
      }

      "fail to compile when total handler is not exhaustive" >> {
        val ct = implicitly[ClassTag[Option[Foo]]]
        typecheck {
          """
            class MyActor extends TypedActor.Of[Option[Foo] | Bar.type] {
              def typedReceive: TypedReceive = Union.on[Bar.type]{
                case Bar ⇒ unionBecome.total[Option[Foo]]({
                  case Some(Foo("foo")) => ()
                })(implicitly, ct)
              }.apply
             }
          """
        } must failWith(Regex.quote("match may not be exhaustive.\nIt would fail on the following inputs: None, Some(_)"))
      }.pendingUntilFixed("succeeds... possibly missing fatal warnings or similar")

      "not fail when the unoinBecome.total doesnt match everything" >> {implicit ee: ExecutionEnv ⇒
        class MyActor extends TypedActor.Of[Foo | Bar.type] {
          def typedReceive: TypedReceive = Union.on[Bar.type] {
            case Bar ⇒
              inboxRef ! Bar
              unionBecome.total[Foo] {
                case Foo("foo") ⇒ inboxRef ! Foo("foo")
              }
          }.apply
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

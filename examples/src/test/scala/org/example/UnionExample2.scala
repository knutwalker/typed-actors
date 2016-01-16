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

package org.example

import akka.actor.ActorSystem
import de.knutwalker.akka.typed._


object UnionExample2 extends App {

  case class Foo()
  case class Bar()
  case class Baz()

  class UnionedActor extends TypedActor.Of[Foo | Bar | Baz] {
    def typedReceive: TypedReceive = Union
      .on[Foo] { case Foo() ⇒ println("foo") }
      .on[Bar] { case Bar() ⇒ println("bar") }
      .on[Baz] { case Baz() ⇒ println("baz") }
      .apply
  }

  implicit val system = ActorSystem()

  val props: Props[Foo | Bar | Baz]    = PropsFor(new UnionedActor)
  val ref  : ActorRef[Foo | Bar | Baz] = ActorOf(props, "union")

  ref ! Foo()
  ref ! Bar()
  ref ! Baz()

  // [error] UnionExample2.scala:48:
  // Cannot prove that message of type org.example.UnionExample2.Foo.type is a member of org.example.UnionExample2.ref.Message.
  // ref ! Foo

  Shutdown(system)
}

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

package org.example

import akka.actor.{ Actor, ActorSystem }
import de.knutwalker.akka.typed._


object UnionExample extends App {

  case class Foo()
  case class Bar()
  case class Baz()

  class UnionedActor extends Actor {
    def receive = {
      case x ⇒ println(x)
    }
  }

  implicit val system = ActorSystem()

  val props1: Props[Foo]                = Props(new UnionedActor)
  val props0: Props[Foo | Bar]          = props1.or[Bar]
  val ref0  : ActorRef[Foo | Bar]       = ActorOf(props0, "union")
  val ref   : ActorRef[Foo | Bar | Baz] = ref0.or[Baz]

  ref ! Foo()
  ref ! Bar()
  ref ! Baz()

  // [error] UnionExample.scala:49:
  // Cannot prove that message of type org.example.UnionExample.Foo.type is a member of org.example.UnionExample.ref.Message.
  // ref ! Foo

  Shutdown(system)
}

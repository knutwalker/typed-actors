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

import akka.util.Timeout

import scala.concurrent.{ Future, Await }


object AdapterExample extends App {

  case class Msg(payload: String)(val replyTo: ActorRef[Resp])
  case class Resp(answer: String)

  case class Msg2(payload: String)(val replyTo: ActorRef[Resp2])
  case class Resp2(answer: Resp)

  implicit val system = ActorSystem()
  import system.dispatcher
  import scala.concurrent.duration._
  implicit val timeout: Timeout = 5.seconds


  val msg : ActorRef[Msg]  = ActorOf(PropsFor(new TypedActor.Of[Msg] {
    val typedReceive: TypedReceive = {
      case m ⇒ m.replyTo ! Resp(m.payload)
    }
  }))
  val msg2: ActorRef[Msg2] = ActorOf(PropsFor(new TypedActor.Of[Msg2] {
    val typedReceive: TypedReceive = {
      case m ⇒ msg ! Msg(m.payload)(m.replyTo.contramap(Resp2))
    }
  }))

  val f = msg2 ? Msg2("foo")
  val g = msg2 ? Msg2("bar")

  val List(rf, rg) = Await.result(Future.sequence(List(f, g)), Duration.Inf)
  println(s"rf = $rf, rg = $rg")

  Shutdown(system)
}

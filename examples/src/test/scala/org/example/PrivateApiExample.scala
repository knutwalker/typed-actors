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

package org.example

import de.knutwalker.akka.typed._
import de.knutwalker.union._

import akka.actor.{ ActorLogging, ActorSystem }

import com.typesafe.config.ConfigFactory

import scala.annotation.tailrec
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException

object PrivateApiExample extends App {

  case class Response(payload: String)
  case class PublicProtocol(replyTo: ActorRef[Response])
  case class InternalMessage(signal: String)

  implicit val system = ActorSystem("example", config = Some(ConfigFactory.parseString(
    """
    akka.actor.debug.receive = on
    akka.loglevel = DEBUG
    """)))
  val inbox = CreateInbox()

  class ProtocolActor extends TypedActor.Of[PublicProtocol | InternalMessage] with ActorLogging {

    private[this] var payload = Option.empty[String]

    override def preStart(): Unit = {
      typedSelf ! InternalMessage("blubb")
      super.preStart()
    }

    val typedReceive: TypedReceive = TotalUnion {
      case PublicProtocol(reply) if payload.isDefined ⇒ reply ! Response(payload.get)
      case InternalMessage(signal)                    ⇒ payload = Some(signal)
    }
  }

  val ref = ActorOf(PropsFor[ProtocolActor].only[PublicProtocol])

  @tailrec def send(iterations: Int = 1): (Int, String) = {
    ref ! PublicProtocol(inbox.getRef().typed)
    try {
      val Response(payload) = inbox.receive(1.micro)
      (iterations, payload)
    } catch {
      case _: TimeoutException ⇒
        send(iterations + 1)
    }
  }

  val (iterations, payload) = send()
  println(s"payload = $payload after $iterations iterations")

  Shutdown(system)
}

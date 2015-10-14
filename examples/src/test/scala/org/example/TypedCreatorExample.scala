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

import akka.actor.{ ActorLogging, ActorSystem }
import de.knutwalker.akka.typed._

object TypedCreatorExample extends App {

  case class Ping(replyTo: ActorRef[Pong])
  case class Pong(replyTo: ActorRef[Ping])

  implicit val system = ActorSystem()
  case class PingActor() extends TypedActor.Of[Ping] with ActorLogging {
    private[this] var count = 0

    def typedReceive: TypedReceive = Total {
      case Ping(replyTo) ⇒
        count += 1
        replyTo ! Pong(typedSelf)
    }

    override def postStop(): Unit = {
      log.info(s"pings: $count")
    }
  }

  case class PongActor() extends TypedActor.Of[Pong] with ActorLogging {
    private[this] var count = 0

    def typedReceive: TypedReceive = Total {
      case Pong(replyTo) ⇒
        count += 1
        replyTo ! Ping(typedSelf)
    }

    override def postStop(): Unit = {
      log.info(s"pongs: $count")
    }
  }

  val ping = Typed[PingActor].create()
  val pong = Typed[PongActor].create()

  ping ! Ping(pong)

  Thread.sleep(1000)

  Shutdown(system)
}

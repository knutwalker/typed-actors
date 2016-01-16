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

import akka.actor.{ ActorLogging, ActorSystem }
import akka.persistence.{ SnapshotOffer, PersistentActor }
import de.knutwalker.akka.typed._

import scala.concurrent.duration._

object PersistenceExample extends App {

  case class Ping(replyTo: ActorRef[Pong])
  case class Pong(replyTo: ActorRef[Ping])

  case class Evt(data: String)

  case class ExampleState(events: List[String] = Nil) {
    def updated(evt: Evt): ExampleState = copy(evt.data :: events)
    def size: Int = events.length
    override def toString: String = events.reverse.toString
  }

  class TypedPersistentPingActor extends TypedActor with PersistentActor with ActorLogging {
    type Message = Ping

    def persistenceId: String = "typed-persistent-ping-id"

    var state = ExampleState()

    def updateState(event: Evt): Unit =
      state = state.updated(event)

    def numEvents =
      state.size

    val receiveRecover: Receive = {
      case evt: Evt                                 => updateState(evt)
      case SnapshotOffer(_, snapshot: ExampleState) => state = snapshot
    }

    val typedReceive: TypedReceive = {
      case Ping(replyTo) ⇒
        persist(Evt(s"$numEvents"))(updateState)
        persist(Evt(s"${numEvents + 1}")) { event =>
          updateState(event)
          replyTo ! Pong(typedSelf)
        }
    }

    val receiveCommand: Receive =
      untypedFromTyped(typedReceive).orElse {
        case "snap"  => saveSnapshot(state)
        case "print" => println(state)
      }

    override def receive: Receive =
      receiveCommand

    override def postStop(): Unit = {
      log.info(s"state = $state")
      super.postStop()
    }
  }

  class TypedPongActor extends TypedActor.Of[Pong] with ActorLogging {
    private[this] var count = 0
    val typedReceive: TypedReceive = {
      case Pong(replyTo) ⇒
        count += 1
        replyTo ! Ping(typedSelf)
    }

    override def postStop(): Unit = {
      log.info(s"pings: $count")
      super.postStop()
    }

    override def preStart(): Unit = {
      import context.dispatcher
      super.preStart()
      context.system.scheduler.scheduleOnce(600.millis)(Shutdown(system))
      ()
    }
  }



  implicit val system = ActorSystem()
  val ping = ActorOf(PropsFor[TypedPersistentPingActor], "ping")
  val pong = ActorOf(PropsFor[TypedPongActor], "pong")

  ping ! Ping(pong)
}

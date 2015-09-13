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

import akka.actor.Actor
import akka.event.LoggingReceive

trait TypedActor extends Actor with Product with Serializable {
  type Message
  type TypedReceive = PartialFunction[Message, Unit]

  final val typedSelf: ActorRef[Message] =
    tag(self)

  final def typedBecome(f: TypedReceive): Unit =
    context become mkReceive(f)

  final def Total(f: Message ⇒ Unit): TypedReceive =
    PartialFunction(f)

  final def receive: Receive =
    mkReceive(receiveMsg)

  def receiveMsg: TypedReceive

  private def mkReceive(f: TypedReceive): Receive = LoggingReceive {
    case x if f.isDefinedAt(x.asInstanceOf[Message]) ⇒ f(x.asInstanceOf[Message])
  }

}
object TypedActor {
  trait Of[A] extends TypedActor {type Message = A}
}

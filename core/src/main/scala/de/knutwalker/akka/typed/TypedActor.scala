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

trait TypedActor extends Actor with Product with Serializable {
  type Message

  final val typedSelf: ActorRef[Message] =
    tag(self)

  final def typedBecome(f: Message => Unit) =
    context become {
      case x => f(x.asInstanceOf[Message])
    }

  final def receive: Receive = {
    case x ⇒ receiveMsg(x.asInstanceOf[Message])
  }

  def receiveMsg(msg: Message): Unit
}
object TypedActor {
  trait Of[A] extends TypedActor {type Message = A}
}

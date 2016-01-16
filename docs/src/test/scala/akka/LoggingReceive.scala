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

package akka

import akka.actor.Actor.Receive
import akka.actor.ActorContext

/**
 * Synchronous copy of [[akka.event.LoggingReceive]].
 * Just for tutorials.
 */
object LoggingReceive {

  def apply(r: Receive)(implicit context: ActorContext): Receive = r match {
    case _: LoggingReceive ⇒ r
    case _                 ⇒ if (context.system.settings.AddLoggingReceive) new LoggingReceive(r) else r
  }
}

class LoggingReceive(r: Receive)(implicit context: ActorContext) extends Receive {
  def isDefinedAt(o: Any): Boolean = {
    val handled = r.isDefinedAt(o)
    val msg = s"received ${if (handled) "handled" else "unhandled"} message $o"
    println(s"[DEBUG] $msg")
    handled
  }
  def apply(o: Any): Unit = r(o)
}

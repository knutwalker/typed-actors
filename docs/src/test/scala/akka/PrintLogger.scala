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

package akka

import akka.actor._
import akka.event.Logging.{ LogEvent, Debug, Error, Info, InitializeLogger, LoggerInitialized, Warning }

/**
 * Used for tut as a simple logger that only shows the message and the loglevel.
 * Not meant to be used in actual production code.
 */
class PrintLogger extends Actor {
  override def receive: Receive = {
    case InitializeLogger(_) ⇒ sender() ! LoggerInitialized
    case event: LogEvent if event.message.toString.startsWith("shutting down:") ⇒
    case event: Error        ⇒ println(s"[ERROR] ${event.message }")
    case event: Warning      ⇒ println(s"[WARN ] ${event.message }")
    case event: Info         ⇒ println(s"[INFO ] ${event.message }")
    case event: Debug        ⇒ println(s"[DEBUG] ${event.message }")
  }
}

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

package de.knutwalker.akka.typed

import akka.actor.{ Inbox, ActorSystem }

import scala.annotation.tailrec

/**
 * Workaround akka bug as long as 2.3 is supported
 * https://github.com/akka/akka/issues/15409
 */
object CreateInbox {

  def apply()(implicit system: ActorSystem): Inbox =
    createInbox(system)

  @tailrec
  private def createInbox(sys: ActorSystem): Inbox = {
    try Inbox.create(sys) catch {
      case cee: ClassCastException ⇒ createInbox(sys)
    }
  }
}

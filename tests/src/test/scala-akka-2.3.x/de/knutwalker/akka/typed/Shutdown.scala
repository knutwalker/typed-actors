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

import akka.actor.ActorSystem
import akka.util.Timeout

import scala.reflect.ClassTag

object Shutdown {
  def apply(system: ActorSystem): Unit =
    system.shutdown()
}

object TimeoutMessage {
  def apply[A](ref: ActorRef[A])(implicit ct: ClassTag[A], timeout: Timeout): String = {
    s"Ask timed out on [$ref] after [${timeout.duration.toMillis} ms]"
  }
}

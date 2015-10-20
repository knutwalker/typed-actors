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

package akka.typedactors

import akka.actor.InternalActorRef
import akka.pattern.PromiseActorRef
import akka.util.Timeout
import de.knutwalker.akka.typed.UntypedActorRef

import scala.reflect.ClassTag

private[typedactors] object PromiseRef {
  def apply[A](ref: InternalActorRef, target: UntypedActorRef, sender: UntypedActorRef, timeout: Timeout, ctA: ClassTag[A]): PromiseActorRef = {
    PromiseActorRef(ref.provider, timeout, targetName = target.toString())
  }
}

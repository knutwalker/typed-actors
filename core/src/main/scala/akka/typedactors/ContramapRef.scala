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

package akka.typedactors

import de.knutwalker.akka.typed._

import akka.actor.{ ActorCell, ActorContext, FunctionRef, Terminated }

object ContramapRef {

  /**
   * Internal API for creating efficient one-off adapters.
   * Do NOT call this method directly.
   */
  def apply[A, B](target: UntypedActorRef, context: ActorContext)(f: B ⇒ A): ActorRef[B] = {
    val cell = context.asInstanceOf[ActorCell]
    var adapter: FunctionRef = null
    val ref = cell.addFunctionRef {(sdr, msg) ⇒
      msg match {
        case Terminated(`target`) ⇒
          val removed = cell.removeFunctionRef(adapter)
          if (!removed) adapter.stop()
        case _                    ⇒
          val m = try f(msg.asInstanceOf[B]) catch {
            case _: ClassCastException ⇒ msg
            case _: MatchError         ⇒ msg
          }
          target.!(m)(sdr)
      }
    }
    adapter = ref
    ref.watch(target)
    ref.typed[B]
  }

  def stop(context: ActorContext, child: UntypedActorRef): Unit = child match {
    case f: FunctionRef ⇒
      val removed = try {
        context.asInstanceOf[ActorCell].removeFunctionRef(f)
      } catch {
        case _: IllegalArgumentException ⇒ false
      }
      if (!removed) f.stop()
    case _              ⇒ context stop child
  }
}

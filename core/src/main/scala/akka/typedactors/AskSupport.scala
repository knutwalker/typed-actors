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
import akka.pattern.{ AskTimeoutException, PromiseActorRef }
import akka.util.Timeout
import de.knutwalker.akka.typed._

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * Support the ask pattern.
 * This must live in the `akka.typedactors` package since it requires usage
 * of `private[akka]` methods/classes.
 *
 * This is considered a PRIVATE API.
 * @see [[de.knutwalker.akka.typed.ActorRefOps.?]]
 */
object AskSupport {
  def ask[A, B](ref: ActorRef[A], f: ActorRef[B] ⇒ A, timeout: Timeout, ctA: ClassTag[A], sender: UntypedActorRef): Future[B] =
    internalAsk[A, B](ref.untyped, timeout, f.asInstanceOf[UntypedActorRef ⇒ Any], sender, ctA)

  private def internalAsk[A, B](_ref: UntypedActorRef, timeout: Timeout, f: UntypedActorRef ⇒ Any, sender: UntypedActorRef, ctA: ClassTag[A]): Future[B] = _ref match {
    case r: InternalActorRef if r.isTerminated ⇒
      val msg = f(r.provider.deadLetters)
      _ref.tell(msg, sender)
      Future.failed[B](new AskTimeoutException(s"Recipient[${_ref}] had already been terminated. Sender[$sender] sent the message of type '${msg.getClass.getName}'."))
    case r: InternalActorRef ⇒
      if (timeout.duration.length <= 0) {
        Future.failed[B](new IllegalArgumentException(s"Timeout length must not be negative, question not sent to [${_ref}]. Sender[$sender] sent the message of type '${ctA.runtimeClass.getName}'."))
      } else {
        val ref = PromiseActorRef(r.provider, timeout, targetName = _ref.toString())
        val msg = f(ref)
        _ref.tell(msg, ref)
        ref.result.future.asInstanceOf[Future[B]]
      }
  }
}

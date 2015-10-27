---
layout: page
title: Implementation Notes
tut: 07
---

```tut:invisible
import akka.actor._
import de.knutwalker.akka.typed._
implicit val system = ActorSystem("foo", config=Some(com.typesafe.config.ConfigFactory.parseString("""
akka.loglevel=DEBUG
akka.stdout-loglevel=OFF
akka.loggers=["akka.PrintLogger"]
akka.actor.debug.receive=off
""")))
```

Typed Actors are implemented as a type tag, a structural type refinement.
This is very similar to [`scalaz.@@`](https://github.com/scalaz/scalaz/blob/81e68e845e91b54450a4542b19c1378f06aea861/core/src/main/scala/scalaz/package.scala#L90-L101) and a little bit to [`shapeless.tag.@@`](https://github.com/milessabin/shapeless/blob/6c659d253ba004baf74e20d5d815729552677303/core/src/main/scala/shapeless/typeoperators.scala#L28-L29)
The message type is put togehter with the surrounding type (`ActorRef` or `Props`) into a special type, that exists only at compile time.
It carries enough type information for the compiler reject certain calls to tell while not requiring any wrappers at runtime.
The actual methods are provided by a implicit ops wrapper that extends AnyVal, so that there is no runtime overhead as well.

#### Good Practices

Typed Actors does not try to prevent you from doing fancy things and shooting yourself in the foot, it rather wants to give you a way so you can help yourself in keeping your sanity.
That is, you can always switch between untyped and typed actors, even if the type information is not actually corresponding to the actors implementation. It is up to you to decide how much safety you want to trade in for flexibility.
That being said, you get the most benefit by using the [TypedActor](typed-actor.html) with the [Typed Creator](creator.html) and only on the `typedReceive` and `typedBecome` methods with the `Total` wrapper. Depending on the situation, you can fairly fine-tune the amount of untypedness you want to have.

One other thing that is frequently causing trouble is `sender()`.
For one, it's not referentially transparent, return the sender of whatever message the Actor is currently processing. This is causing trouble when the `sender()` call happens for example in a callback attached to a `Future`.
The other thing is, it's always an untyped actor and knowledge about the protocol has to be implicitly kept in the head of the developer.
For that reasons, it is a good idea to always provide a `replyTo: ActorRef[A]` field in the message itself and refrain from using `sender()`, ideally ever.

An example of how this could look like. First, the counter example using `sender()` as a quasi status quo.
To have a sensible `sender()` available, we're gonna use `akka.actor.Inbox`.

```tut:silent
import akka.actor.ActorDSL._
val box = inbox()
```

This is a typical request reply cycle using `sender()`.

```tut:silent
case class MyMessage(payload: Int)
case class MyResponse(payload: String)
case class MyActor() extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case MyMessage(payload) => sender() ! payload.toString
  }
}
```

```tut
val ref = Typed[MyActor].create()
box.send(ref.untyped, MyMessage(42))
```

Note that there already is a bug, as the return message was not wrapped in `MyResponse`.

```tut:fail
val MyResponse(response) = box.receive()
```

Here's how that looks using the `replyTo` pattern.

```tut:silent
case class MyResponse(payload: String)
case class MyMessage(payload: Int)(val replyTo: ActorRef[MyResponse])
case class MyActor() extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case m@MyMessage(payload) => m.replyTo ! MyResponse(payload.toString)
  }
}
```

```tut
val ref = Typed[MyActor].create()
ref ! MyMessage(42)(box.receiver.typed)
val MyResponse(response) = box.receive()
```

Let's try to reproduce the bug from earlier.

```tut:fail
case class MyActor() extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case m@MyMessage(payload) => m.replyTo ! payload.toString
  }
}
```

Now the compiler has caught the bug, benefit!

The `replyTo` pattern is also important in [Akka Typed](http://doc.akka.io/docs/akka/snapshot/scala/typed.html).

##### [&raquo; Comparision with Akka Typed](comparison.html)

```tut:invisible
system.shutdown()
```

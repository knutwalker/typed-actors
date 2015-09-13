---
layout: page
title: Unsafe Usage
tut: true
---

We will reuse the definitions and actors from the [&laquo; Basic Usage](index.html).

```tut:invisible
import akka.actor._
import de.knutwalker.akka.typed._
sealed trait MyMessage
case class Foo(bar: String) extends MyMessage
case object SomeOtherMessage
class MyActor extends Actor {
  def receive = {
    case Foo(bar) => println(s"received a Foo: $bar")
  }
}
implicit val system = ActorSystem("foo")
val props = Props[MyMessage, MyActor]
```

```tut
val typedRef = ActorOf[MyMessage](props, name = "my-actor")
```

Some messages are automatically handled by some actors and need or can not be provided in the actors type.
One example is `PoisonPill`. To sent those kind of messages anyway, use `unsafeTell`.

```tut
typedRef.unsafeTell(PoisonPill)
```

Also, some Akka APIs require you to pass an untyped ActorRef (the regular ActorRef).
You can easily turn your typed actor into an untyped one bu using `untyped`.

```tut
val untypedRef = typedRef.untyped
```

For convenience, `akka.actor.ActorRef` is type aliased as `de.knutwalker.akka.typed.UntypedActorRef`.
Similarly, you can turn any untyped ref into a typed one using `typed`.

```tut
val typedAgain = untypedRef.typed[MyMessage]
```

There are no compiler checks to make sure, that the given actually is able to receive that kind of message.
This signifies the point, that **Typed Actors** are really just a compile-time wrapper and do not carry any kind of runtime information.
To further demonstrate this, you can see that both instances are actually the very same (despite the scalac warning).

```tut
typedRef eq untypedRef
```

As scala tends to infer `Nothing` as the most specific bottom type, you want to make sure to always provide a useful type.

```tut
untypedRef.typed
```


```tut:invisible
system.shutdown()
```

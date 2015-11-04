---
layout: page
title: Unsafe Usage
tut: 103
---

We will reuse the definitions and actors from the [&laquo; Basic Usage](index.html).

```tut:invisible
import akka.actor._
import akka.LoggingReceive
import de.knutwalker.akka.typed._
sealed trait MyMessage
case class Foo(foo: String) extends MyMessage
case class Bar(bar: String) extends MyMessage
case object SomeOtherMessage
class MyActor extends Actor {
  def receive = {
    case Foo(foo) => println(s"received a Foo: $foo")
    case Bar(bar) => println(s"received a Bar: $bar")
  }
}
implicit val system = ActorSystem("foo", config=Some(com.typesafe.config.ConfigFactory.parseString("""
akka.loglevel=DEBUG
akka.stdout-loglevel=OFF
akka.loggers=["akka.PrintLogger"]
akka.actor.debug.receive=on
""")))
val props = Props[MyMessage, MyActor]
```

```tut
val typedRef = ActorOf[MyMessage](props, name = "my-actor")
```

#### Autoreceived Messages

Some messages are automatically handled by some actors and need or cannot be provided in the actors type.
One example is `PoisonPill`. To sent those kind of messages anyway, use `unsafeTell`.

```tut
typedRef.unsafeTell(PoisonPill)
```

#### Switch Between Typed and Untyped

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

As scala tends to infer `Nothing` as the most specific bottom type, you want to make sure to always provide a useful type.

```tut
untypedRef.typed
```

#### Compiletime only

There are no compiler checks to make sure, that the given actually is able to receive that kind of message.
This signifies the point, that **Typed Actors** are really just a compile-time wrapper and do not carry any kind of runtime information.
To further demonstrate this, you can see that both instances are actually the very same (despite the scalac warning).

```tut
typedRef eq untypedRef
```

#### Divergence

This also means, that it is possible to diverge from the specified type with `context.become`.

```tut
class MyOtherActor extends Actor {
  def receive = LoggingReceive {
    case Foo(foo) => println(s"received a Foo: $foo")
    case Bar(bar) => context become LoggingReceive {
      case SomeOtherMessage => println("received some other message")
    }
  }
}
val otherRef = ActorOf(Props[MyMessage, MyOtherActor], "my-other-actor")

otherRef ! Foo("foo")
otherRef ! Bar("bar")
otherRef ! Foo("baz")
otherRef.untyped ! SomeOtherMessage
```

Making sure, that this cannot happen is outside of the scope of **Typed Actors**.
There is, however, a `TypedActor` trait which tries to provide _some_ help. Learn about it next.

##### [&raquo; Typed Actor](typed-actor.html)


```tut:invisible
system.shutdown()
```

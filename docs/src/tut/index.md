---
layout: page
title: Basic Usage
tut: 01
---

To use **Typed Actors**, import the following:

```tut:silent
import de.knutwalker.akka.typed._
```

The underscore/wildcard import is important to bring some implicit classes into scope.
These classes enable the actual syntax to use typed actors.
Also, _Typed Actors_ shadows some names from `akka.actor`, so you need to make sure, that you add this import **after** your akka imports.
 
```tut:silent
import akka.actor._
import de.knutwalker.akka.typed._
```

#### Actor Definition

Using Typed Actors is, at first, similar to regular actors.
It is always a good idea to define your message protocol.

```tut:silent
sealed trait MyMessage
case class Foo(foo: String) extends MyMessage
case class Bar(bar: String) extends MyMessage

case object SomeOtherMessage
```

With that, define a regular actor.

```tut:silent
class MyActor extends Actor {
  def receive = {
    case Foo(foo) => println(s"received a Foo: $foo")
    case Bar(bar) => println(s"received a Bar: $bar")
  }
}
```

#### Actor Creation

Now, use `Props` and `ActorOf`. These are now the ones from `de.knutwalker.akka.typed`, not from `akka.actor`.

```tut
implicit val system = ActorSystem("foo")
val props = Props[MyMessage, MyActor]
val ref = ActorOf(props, name = "my-actor")
```

This will give you an `ActorRef[MyMessage]`. You can use `!` to send messages, as usual.

```tut
ref ! Foo("foo")
ref ! Bar("bar")
```

If you try to send a message from a different protocol, you will get a compile error. Hooray, benefit!

```tut:fail
ref ! SomeOtherMessage
```

There are three possible ways to create a `Props`, mirroring the constructors from `akka.actor.Props`.

```tut
val props = Props[MyMessage, MyActor]
val props = Props[MyMessage, MyActor](new MyActor)
val props = Props[MyMessage, MyActor](classOf[MyActor])
```

Next up, learn how to interact with the less safer parts of Akka.

##### [&raquo; Unsafe Usage](unsafe.html)


```tut:invisible
system.shutdown()
```

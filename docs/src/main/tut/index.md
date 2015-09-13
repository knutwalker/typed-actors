---
layout: page
title: Basic Usage
tut: true
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

Using Typed Actors is, at first, similar to regular actors.
It is always a good idea to define your message protocol.

```tut:silent
sealed trait MyMessage
case class Foo(bar: String) extends MyMessage

case object SomeOtherMessage
```

With that, define a regular actor.

```tut
class MyActor extends Actor {
  def receive = {
    case Foo(bar) => println(s"received a Foo: $bar")
  }
}
```

Now, use `Props` and `ActorOf`. These are now the ones from `de.knutwalker.akka.typed`, not from `akka.actor`.

```tut
implicit val system = ActorSystem("foo")
val props = Props[MyMessage, MyActor]
val ref = ActorOf(props, name = "my-actor")
ref ! Foo("bar")
```

If you try to send a message from a different protocol, you will get a compile error. Benefit!

```tut:fail
ref ! SomeOtherMessage
```

Next up, learn how to interact with the less safer parts of Akka:

#### [&raquo; Unsafe Usage](unsafe.html)


```tut:invisible
system.shutdown()
```

---
layout: page
title: Basic Usage
tut: true
---

To use **Typed Actors**, import the following:

```scala
import de.knutwalker.akka.typed._
```

The underscore/wildcard import is important to bring some implicit classes into scope.
These classes enable the actual syntax to use typed actors.
Also, _Typed Actors_ shadows some names from `akka.actor`, so you need to make sure, that you add this import **after** your akka imports.
 
```scala
import akka.actor._
import de.knutwalker.akka.typed._
```

Using Typed Actors is, at first, similar to regular actors.
It is always a good idea to define your message protocol.

```scala
sealed trait MyMessage
case class Foo(bar: String) extends MyMessage

case object SomeOtherMessage
```

With that, define a regular actor.

```scala
scala> class MyActor extends Actor {
     |   def receive = {
     |     case Foo(bar) => println(s"received a Foo: $bar")
     |   }
     | }
defined class MyActor
```

Now, use `Props` and `ActorOf`. These are now the ones from `de.knutwalker.akka.typed`, not from `akka.actor`.

```scala
scala> implicit val system = ActorSystem("foo")
system: akka.actor.ActorSystem = akka://foo

scala> val props = Props[MyMessage, MyActor]
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> val ref = ActorOf(props, name = "my-actor")
ref: de.knutwalker.akka.typed.package.ActorRef[props.Message] = Actor[akka://foo/user/my-actor#-599623381]

scala> ref ! Foo("bar")
received a Foo: bar
```

If you try to send a message from a different protocol, you will get a compile error. Benefit!

```scala
scala> ref ! SomeOtherMessage
<console>:29: error: type mismatch;
 found   : SomeOtherMessage.type
 required: ref.Message
    (which expands to)  MyMessage
       ref ! SomeOtherMessage
             ^
```

Next up, learn how to interact with the less safer parts of Akka.

#### [&raquo; Unsafe Usage](unsafe.html)





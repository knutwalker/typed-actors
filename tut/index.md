---
layout: page
title: Basic Usage
tut: 01
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

#### Actor Definition

Using Typed Actors is, at first, similar to regular actors.
It is always a good idea to define your message protocol.

```scala
sealed trait MyMessage
case class Foo(foo: String) extends MyMessage
case class Bar(bar: String) extends MyMessage

case object SomeOtherMessage
```

With that, define a regular actor.

```scala
class MyActor extends Actor {
  def receive = {
    case Foo(foo) => println(s"received a Foo: $foo")
    case Bar(bar) => println(s"received a Bar: $bar")
  }
}
```

#### Actor Creation

Now, use `Props` and `ActorOf`. These are now the ones from `de.knutwalker.akka.typed`, not from `akka.actor`.

```scala
scala> implicit val system = ActorSystem("foo")
system: akka.actor.ActorSystem = akka://foo

scala> val props = Props[MyMessage, MyActor]
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> val ref = ActorOf(props, name = "my-actor")
ref: de.knutwalker.akka.typed.package.ActorRef[props.Message] = Actor[akka://foo/user/my-actor#789057462]
```

This will give you an `ActorRef[MyMessage]`. You can use `!` to send messages, as usual.

```scala
scala> ref ! Foo("foo")
received a Foo: foo

scala> ref ! Bar("bar")
received a Bar: bar
```

If you try to send a message from a different protocol, you will get a compile error. Hooray, benefit!

```scala
scala> ref ! SomeOtherMessage
<console>:31: error: type mismatch;
 found   : SomeOtherMessage.type
 required: ref.Message
    (which expands to)  MyMessage
       ref ! SomeOtherMessage
             ^
```

There are three possible ways to create a `Props`, mirroring the constructors from `akka.actor.Props`.

```scala
scala> val props = Props[MyMessage, MyActor]
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> val props = Props[MyMessage, MyActor](new MyActor)
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class akka.actor.TypedCreatorFunctionConsumer,List(class MyActor, <function0>))

scala> val props = Props[MyMessage, MyActor](classOf[MyActor])
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())
```

Next up, learn how to interact with the less safer parts of Akka.

##### [&raquo; Unsafe Usage](unsafe.html)





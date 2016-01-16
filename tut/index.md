---
layout: page
title: Basic Usage
tut: 101
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
ref: de.knutwalker.akka.typed.package.ActorRef[props.Message] = Actor[akka://foo/user/my-actor#-2098030163]
```

This will give you an `ActorRef[MyMessage]`.

There are three possible ways to create a `Props`, mirroring the constructors from `akka.actor.Props`.

```scala
scala> val props = Props[MyMessage, MyActor]
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> val props = Props[MyMessage, MyActor](new MyActor)
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class akka.actor.TypedCreatorFunctionConsumer,List(class MyActor, <function0>))

scala> val props = Props[MyMessage, MyActor](classOf[MyActor])
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())
```

#### Sending messages

Sending messages to a typed actor is the same as sending messages to an untyped on, you use `!`.

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

#### Ask pattern

Typed actors support the ask pattern, `?`, without imports and the returned Future is properly typed.
In order to achieve this, instead of sending an already instantiated type, you send a function that, given the properly typed sender, will return the message.
This is usually achieved with a separate parameter list on a case class (message), typically called `replyTo`.

```scala
case class MyResponse(payload: String)
case class MyMessage(payload: String)(val replyTo: ActorRef[MyResponse])
```

If you define your messages this way, you can left out the last parameter list and will get the required function.
To respond, use `message.replyTo` instead of `sender()` to get the properly typed sender. Although, to be fair, `sender()` will be the same actor, it's just the untyped version.
Finally, `?` requires an `implicit Timeout`, just like the regular, untyped ask.

```scala
import scala.concurrent.duration._
import akka.util.Timeout

class MyActor extends Actor {
  def receive = {
    case m@MyMessage(payload) => m.replyTo ! MyResponse(payload)
  }
}
implicit val timeout: Timeout = 1.second
```

```scala
scala> val ref = ActorOf(Props[MyMessage, MyActor])
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/$a#1685525040]

scala> val future = ref ? MyMessage("foo")
future: scala.concurrent.Future[MyResponse] = scala.concurrent.impl.Promise$DefaultPromise@5a583af5

scala> val response = scala.concurrent.Await.result(future, 1.second)
response: MyResponse = MyResponse(foo)
```

Next up, learn how to mix multiple unrelated messages into the checked type.

##### [&raquo; Union Types](union.html)





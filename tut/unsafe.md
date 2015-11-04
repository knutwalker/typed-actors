---
layout: page
title: Unsafe Usage
tut: 03
---

We will reuse the definitions and actors from the [&laquo; Basic Usage](index.html).




```scala
scala> val typedRef = ActorOf[MyMessage](props, name = "my-actor")
typedRef: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#1586605599]
```

#### Autoreceived Messages

Some messages are automatically handled by some actors and need or cannot be provided in the actors type.
One example is `PoisonPill`. To sent those kind of messages anyway, use `unsafeTell`.

```scala
scala> typedRef.unsafeTell(PoisonPill)
```

#### Switch Between Typed and Untyped

Also, some Akka APIs require you to pass an untyped ActorRef (the regular ActorRef).
You can easily turn your typed actor into an untyped one bu using `untyped`.

```scala
scala> val untypedRef = typedRef.untyped
untypedRef: de.knutwalker.akka.typed.package.UntypedActorRef = Actor[akka://foo/user/my-actor#1586605599]
```

For convenience, `akka.actor.ActorRef` is type aliased as `de.knutwalker.akka.typed.UntypedActorRef`.
Similarly, you can turn any untyped ref into a typed one using `typed`.

```scala
scala> val typedAgain = untypedRef.typed[MyMessage]
typedAgain: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#1586605599]
```

As scala tends to infer `Nothing` as the most specific bottom type, you want to make sure to always provide a useful type.

```scala
scala> untypedRef.typed
res1: de.knutwalker.akka.typed.package.ActorRef[Nothing] = Actor[akka://foo/user/my-actor#1586605599]
```

#### Compiletime only

There are no compiler checks to make sure, that the given actually is able to receive that kind of message.
This signifies the point, that **Typed Actors** are really just a compile-time wrapper and do not carry any kind of runtime information.
To further demonstrate this, you can see that both instances are actually the very same (despite the scalac warning).

```scala
scala> typedRef eq untypedRef
<console>:29: warning: AnyRef{type Message = MyMessage; type Self = de.knutwalker.akka.typed.UntypedActorRef} and akka.actor.ActorRef are unrelated: they will most likely never compare equal
       typedRef eq untypedRef
                ^
res2: Boolean = true
```

#### Divergence

This also means, that it is possible to diverge from the specified type with `context.become`.

```scala
scala> class MyOtherActor extends Actor {
     |   def receive = LoggingReceive {
     |     case Foo(foo) => println(s"received a Foo: $foo")
     |     case Bar(bar) => context become LoggingReceive {
     |       case SomeOtherMessage => println("received some other message")
     |     }
     |   }
     | }
defined class MyOtherActor

scala> val otherRef = ActorOf(Props[MyMessage, MyOtherActor], "my-other-actor")
otherRef: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-other-actor#-158091196]

scala> otherRef ! Foo("foo")
[DEBUG] received handled message Foo(foo)

scala> otherRef ! Bar("bar")
received a Foo: foo
[DEBUG] received handled message Bar(bar)

scala> otherRef ! Foo("baz")
[DEBUG] received unhandled message Foo(baz)

scala> otherRef.untyped ! SomeOtherMessage
[DEBUG] received handled message SomeOtherMessage
received some other message
```

Making sure, that this cannot happen is outside of the scope of **Typed Actors**.
There is, however, a `TypedActor` trait which tries to provide _some_ help. Learn about it next.

##### [&raquo; Typed Actor](typed-actor.html)





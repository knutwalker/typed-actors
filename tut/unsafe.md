---
layout: page
title: Unsafe Usage
tut: true
---

We will reuse the definitions and actors from the [&laquo; Basic Usage](index.html).




```scala
scala> val typedRef = ActorOf[MyMessage](props, name = "my-actor")
typedRef: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#-353741282]
```

Some messages are automatically handled by some actors and need or can not be provided in the actors type.
One example is `PoisonPill`. To sent those kind of messages anyway, use `unsafeTell`.

```scala
scala> typedRef.unsafeTell(PoisonPill)
```

Also, some Akka APIs require you to pass an untyped ActorRef (the regular ActorRef).
You can easily turn your typed actor into an untyped one bu using `untyped`.

```scala
scala> val untypedRef = typedRef.untyped
untypedRef: de.knutwalker.akka.typed.package.UntypedActorRef = Actor[akka://foo/user/my-actor#-353741282]
```

For convenience, `akka.actor.ActorRef` is type aliased as `de.knutwalker.akka.typed.UntypedActorRef`.
Similarly, you can turn any untyped ref into a typed one using `typed`.

```scala
scala> val typedAgain = untypedRef.typed[MyMessage]
typedAgain: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#-353741282]
```

There are no compiler checks to make sure, that the given actually is able to receive that kind of message.
This signifies the point, that **Typed Actors** are really just a compile-time wrapper and do not carry any kind of runtime information.
To further demonstrate this, you can see that both instances are actually the very same (despite the scalac warning).

```scala
scala> typedRef eq untypedRef
<console>:26: warning: AnyRef{type Message = MyMessage; type Self = de.knutwalker.akka.typed.UntypedActorRef} and akka.actor.ActorRef are unrelated: they will most likely never compare equal
       typedRef eq untypedRef
                ^
res1: Boolean = true
```

As scala tends to infer `Nothing` as the most specific bottom type, you want to make sure to always provide a useful type.

```scala
scala> untypedRef.typed
res2: de.knutwalker.akka.typed.package.ActorRef[Nothing] = Actor[akka://foo/user/my-actor#-353741282]
```





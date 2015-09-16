---
layout: page
title: TypedActor
tut: 03
---

We will reuse the definitions and actors from the [&laquo; Unsafe Usage](unsafe.html).




Having a typed reference to an actor is one thing, but how can we improve type-safety within the actor itself?
**Typed Actors** offers a `trait` called `TypedActor` which you can extend from instead of `Actor`.
`TypedActor` itself extends `Actor` but contains an abstract type member and typed receive method
instead of just an untyped receive method. In order to use the `TypedActor`, you have to provide both.

```scala
scala> class MyActor extends TypedActor {
     |   type Message = MyMessage
     |   def typedReceive = {
     |     case Foo(foo) => println(s"received a Foo: $foo")
     |     case Bar(bar) => println(s"received a Bar: $bar")
     |   }
     | }
defined class MyActor

scala> val ref = ActorOf(Props[MyMessage, MyActor], name = "my-actor")
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#1724139829]

scala> ref ! Foo("foo")

scala> ref ! Bar("bar")
[DEBUG] received handled message Foo(foo)
received a Foo: foo
received a Bar: bar
[DEBUG] received handled message Bar(bar)
```





If you match on messages from a different type, you will get a compile error.

```scala
scala> class MyActor extends TypedActor {
     |   type Message = MyMessage
     |   def typedReceive = {
     |     case SomeOtherMessage => println("received some other message")
     |   }
     | }
<console>:24: error: pattern type is incompatible with expected type;
 found   : SomeOtherMessage.type
 required: MyActor.this.Message
    (which expands to)  MyMessage
           case SomeOtherMessage => println("received some other message")
                ^
```

#### Message as type parameter

The message type can also be provided as a type parameter on `TypedActor.Of[_]`.

```scala
scala> class MyActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = {
     |     case Foo(foo) => println(s"received a Foo: $foo")
     |   }
     | }
defined class MyActor

scala> val ref = ActorOf(Props[MyMessage, MyActor], name = "my-actor-2")
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor-2#-503655419]

scala> ref ! Foo("foo")
[DEBUG] received handled message Foo(foo)
```
received a Foo: foo


#### Divergence

Similar to the untyped actor, `context.become` is not hidden and can still lead to diverging actors.

```scala
scala> class MyOtherActor extends TypedActor {
     |   type Message = MyMessage
     |   def typedReceive = {
     |     case Foo(foo) => println(s"received a Foo: $foo")  
     |     case Bar(bar) => context become LoggingReceive {
     |       case SomeOtherMessage =>
     |     }
     |   }
     | }
defined class MyOtherActor

scala> val otherRef = ActorOf(Props[MyMessage, MyOtherActor], "my-other-actor")
otherRef: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-other-actor#-1020490913]

scala> otherRef ! Foo("foo")
[DEBUG] received handled message Foo(foo)
received a Foo: foo

scala> otherRef ! Bar("bar")
[DEBUG] received handled message Bar(bar)

scala> otherRef ! Foo("baz")

scala> otherRef.untyped ! SomeOtherMessage
[DEBUG] received unhandled message Foo(baz)
[DEBUG] received handled message SomeOtherMessage
```

#### More Typing

The `TypedActor` offers some more methods that ought to help with keeping within the defined type bound.
There is `typedSelf`, which is the typed version of the regular `self`.
Then there is `typedBecome`, the typed version of `context.become`. It takes a partial receive function, much like `typedReceive`.

Using `typedBecome`, diverging from the type bound is no longer possible

```scala
scala> class MyOtherActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = {
     |     case Foo(foo) => println(s"received a Foo: $foo")  
     |     case Bar(bar) => typedBecome {
     |       case SomeOtherMessage =>
     |     }
     |   }
     | }
<console>:29: error: pattern type is incompatible with expected type;
 found   : SomeOtherMessage.type
 required: MyOtherActor.this.Message
    (which expands to)  MyMessage
             case SomeOtherMessage =>
                  ^
```

You can event get exhaustiveness checks from the compiler by using the `Total` wrapper.

```scala
scala> class MyOtherActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = Total {
     |     case Foo(foo) => println(s"received a Foo: $foo")
     |   }
     | }
<console>:23: warning: match may not be exhaustive.
It would fail on the following input: Bar(_)
         def typedReceive = Total {
                                  ^
defined class MyOtherActor
```

Next, learn more ways to create `Props`.

##### [&raquo; Building Props](props.html)





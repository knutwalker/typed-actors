---
layout: page
title: TypedActor
tut: 03
---

We will reuse the definitions and actors from the [&laquo; Unsafe Usage](unsafe.html).




Having a typed reference to an actor is one thing, but how can we improve type-safety within the actor itself?
**Typed Actors** offers a `trait` called `TypedActor` which you can extend from instead of `Actor`.
`TypedActor` itself extends `Actor` but contains an abstract type member and typed receive method
instead of just an untyped receive method.
In order to use the `TypedActor`, you have to extend `TypedActor.Of[_]` and provide your message type via type parameter (you cannot extend directly from `TypedActor`).

```scala
scala> class MyActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = {
     |     case Foo(foo) => println(s"received a Foo: $foo")
     |     case Bar(bar) => println(s"received a Bar: $bar")
     |   }
     | }
defined class MyActor

scala> val ref = ActorOf(Props[MyMessage, MyActor], name = "my-actor")
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#281591139]

scala> ref ! Foo("foo")
received a Foo: foo

scala> ref ! Bar("bar")
received a Bar: bar
```

If you match on messages from a different type, you will get a compile error.

```scala
scala> class MyActor extends TypedActor {
     |   type Message = MyMessage
     |   def typedReceive = {
     |     case SomeOtherMessage => println("received some other message")
     |   }
     | }
<console>:20: error: illegal inheritance from sealed trait TypedActor
       class MyActor extends TypedActor {
                             ^
<console>:23: error: pattern type is incompatible with expected type;
 found   : SomeOtherMessage.type
 required: MyActor.this.Message
    (which expands to)  MyMessage
           case SomeOtherMessage => println("received some other message")
                ^
```


#### Divergence

Similar to the untyped actor, `context.become` is not hidden and can still lead to diverging actors.




```scala
scala> class MyOtherActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = {
     |     case Foo(foo) => println(s"received a Foo: $foo")
     |     case Bar(bar) => context become LoggingReceive {
     |       case SomeOtherMessage => println("received some other message")
     |     }
     |   }
     | }
defined class MyOtherActor

scala> val otherRef = ActorOf(Props[MyMessage, MyOtherActor], "my-other-actor")
otherRef: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-other-actor#156650772]

scala> otherRef ! Foo("foo")

scala> otherRef ! Bar("bar")
received a Foo: foo
[DEBUG] received handled message Foo(foo)
[DEBUG] received handled message Bar(bar)

scala> otherRef ! Foo("baz")
[DEBUG] received unhandled message Foo(baz)

scala> otherRef.untyped ! SomeOtherMessage
[DEBUG] received handled message SomeOtherMessage
received some other message
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
     |       case SomeOtherMessage => println("received some other message")
     |     }
     |   }
     | }
<console>:31: error: pattern type is incompatible with expected type;
 found   : SomeOtherMessage.type
 required: MyOtherActor.this.Message
    (which expands to)  MyMessage
             case SomeOtherMessage => println("received some other message")
                  ^
```

You can event get exhaustiveness checks from the compiler by using the `Total` wrapper.

```scala
scala> class MyOtherActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = Total {
     |     case Foo(foo) => println(s"received a Foo: $foo")
     |   }
     | }
<console>:25: warning: match may not be exhaustive.
It would fail on the following input: Bar(_)
         def typedReceive = Total {
                                  ^
defined class MyOtherActor
```

The companion object `TypedActor` has an `apply` method that wraps a total function in an actor and returns a prop for this actor.

```scala
scala> val ref = ActorOf(TypedActor[MyMessage] {
     |   case Foo(foo) => println(s"received a Foo: $foo")
     |   case Bar(bar) => println(s"received a Bar: $bar")
     | })
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/$a#2051043235]
```

Please be aware of a ~~bug~~ feature that wouldn't fail on non-exhaustive checks.
If you use guards in your matchers, the complete pattern is optimisiticaly treated as exhaustive; See [SI-5365](https://issues.scala-lang.org/browse/SI-5365), [SI-7631](https://issues.scala-lang.org/browse/SI-7631), and [SI-9232](https://issues.scala-lang.org/browse/SI-9232). Note the failing non-exhaustiveness warning in the next example.

```scala
scala> val False = false
False: Boolean = false

scala> class MyOtherActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = Total {
     |     case Foo(foo) if False =>
     |   }
     | }
defined class MyOtherActor
```

Unfortunately, this can not be worked around by library code. Even worse, this would not result in a unhandled message but in a runtime match error.


#### Going back to untyped land

Sometimes you have to receive messages that are outside of your protocol. A typical case is `Terminated`, but other modules and patterns have those messages as well.
You can use `Untyped` to specify a regular untyped receive block, just as if `receive` were actually the way to go.


```scala
scala> class MyOtherActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = Untyped {
     |     case Terminated(ref) => println(s"$ref terminated")
     |     case Foo(foo) => println(s"received a Foo: $foo")
     |   }
     | }
defined class MyOtherActor
```

With `Untyped`, you won't get any compiler support, it is meant as an escape hatch; If you find yourself using `Untyped` all over the place, consider just using a regular `Actor` instead.

Next, learn more ways to create `Props`.

##### [&raquo; Building Props](props.html)





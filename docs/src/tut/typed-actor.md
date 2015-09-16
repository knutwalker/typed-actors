---
layout: page
title: TypedActor
tut: 03
---

We will reuse the definitions and actors from the [&laquo; Unsafe Usage](unsafe.html).

```tut:invisible
import akka.actor._
import de.knutwalker.akka.typed._
sealed trait MyMessage
case class Foo(foo: String) extends MyMessage
case class Bar(bar: String) extends MyMessage
case object SomeOtherMessage
implicit val system = ActorSystem("foo", config=Some(com.typesafe.config.ConfigFactory.parseString("""
akka.loglevel=DEBUG
akka.stdout-loglevel=OFF
akka.loggers=["akka.PrintLogger"]
akka.actor.debug.receive=off
""")))
```

Having a typed reference to an actor is one thing, but how can we improve type-safety within the actor itself?
**Typed Actors** offers a `trait` called `TypedActor` which you can extend from instead of `Actor`.
`TypedActor` itself extends `Actor` but contains an abstract type member and typed receive method
instead of just an untyped receive method.
In order to use the `TypedActor`, you have to extend `TypedActor.Of[_]` and provide your message type via type parameter (you cannot extend directly from `TypedActor`).

```tut
class MyActor extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case Foo(foo) => println(s"received a Foo: $foo")
    case Bar(bar) => println(s"received a Bar: $bar")
  }
}
val ref = ActorOf(Props[MyMessage, MyActor], name = "my-actor")
ref ! Foo("foo")
ref ! Bar("bar")
```

If you match on messages from a different type, you will get a compile error.

```tut:fail
class MyActor extends TypedActor {
  type Message = MyMessage
  def typedReceive = {
    case SomeOtherMessage => println("received some other message")
  }
}
```


#### Divergence

Similar to the untyped actor, `context.become` is not hidden and can still lead to diverging actors.

```tut:invisible
import akka.LoggingReceive
system.shutdown()
implicit val system = ActorSystem("foo", config=Some(com.typesafe.config.ConfigFactory.parseString("""
akka.loglevel=DEBUG
akka.stdout-loglevel=OFF
akka.loggers=["akka.PrintLogger"]
akka.actor.debug.receive=on
""")))
```

```tut
class MyOtherActor extends TypedActor.Of[MyMessage] {
  def typedReceive = {
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

#### More Typing

The `TypedActor` offers some more methods that ought to help with keeping within the defined type bound.
There is `typedSelf`, which is the typed version of the regular `self`.
Then there is `typedBecome`, the typed version of `context.become`. It takes a partial receive function, much like `typedReceive`.

Using `typedBecome`, diverging from the type bound is no longer possible

```tut:fail
class MyOtherActor extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case Foo(foo) => println(s"received a Foo: $foo")  
    case Bar(bar) => typedBecome {
      case SomeOtherMessage => println("received some other message")
    }
  }
}
```

You can event get exhaustiveness checks from the compiler by using the `Total` wrapper.

```tut
class MyOtherActor extends TypedActor.Of[MyMessage] {
  def typedReceive = Total {
    case Foo(foo) => println(s"received a Foo: $foo")
  }
}
```

#### Going back to untyped land

Sometimes you have to receive messages that are outside of your protocol. A typical case is `Terminated`, but other modules and patterns have those messages as well.
You can use `Untyped` to specify a regular untyped receive block, just as if `receive` were actually the way to go.  


```tut
class MyOtherActor extends TypedActor.Of[MyMessage] {
  def typedReceive = Untyped {
    case Terminated(ref) => println(s"$ref terminated")
    case Foo(foo) => println(s"received a Foo: $foo")
  }
}
```

With `Untyped`, you won't get any compiler support, it is meant as an escape hatch; If you find yourself using `Untyped` all over the place, consider just using a regular `Actor` instead.

Next, learn more ways to create `Props`.

##### [&raquo; Building Props](props.html)


```tut:invisible
system.shutdown()
```

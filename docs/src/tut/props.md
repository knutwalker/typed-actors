---
layout: page
title: Building Props
tut: 05
---

With the [&laquo; Typed Actor](typed-actor.html) in place, we can now look at more ways to define a `Props`.

```tut:invisible
import akka.actor._
import de.knutwalker.akka.typed._
sealed trait MyMessage
case class Foo(foo: String) extends MyMessage
case class Bar(bar: String) extends MyMessage
case object SomeOtherMessage
implicit val system = ActorSystem("foo", config=Some(com.typesafe.config.ConfigFactory.parseString("""
akka.stdout-loglevel=OFF
akka.loggers=["akka.PrintLogger"]
""")))
```

#### Message Type Derivation

When creating a props for a `TypeActor`, we can derive the message type and thus reduce the amount of type annotation we have to write.
This is done with `PropsFor`.

Consider this typed actor.

```tut:silent
class MyActor extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case Foo(foo) => println(s"received a Foo: $foo")
  }
}
```

Using `Props` we have to repeat the information, that this actor only accepts messages of type `MyMessage`, although the compiler knows about this.

```tut
Props[MyMessage, MyActor] // MyMessage is repetitive
Props(new MyActor) // message type derives as Nothing
Props[MyMessage, MyActor](new MyActor) // MyMessage and MyActor are repetitive
Props(classOf[MyActor]) // message type derives as Nothing
Props[MyMessage, MyActor](classOf[MyActor]) // MyMessage and MyActor are repetitive
```

When you have a `TypedActor`, you can use `PropsFor` instead of `Props` to use the type information embedded in `TypedActor#Message`.

```tut
PropsFor[MyActor]
PropsFor(new MyActor)
PropsFor(classOf[MyActor])
```

Of course, some of these cases can also be mitigated by using type ascription on the result type.

```tut
val props: Props[MyMessage] = Props(new MyActor)
val props: Props[MyMessage] = Props(classOf[MyActor])
val props: Props[MyMessage] = PropsFor[MyActor]
val props: Props[MyMessage] = PropsFor(new MyActor)
val props: Props[MyMessage] = PropsFor(classOf[MyActor])
```

#### Type Currying for Props

`PropsFor` only works with a `TypedActor`. There is yet another way to create a `Props`, that has the type information curried, `PropsOf`.
With `PropsOf`, you apply once with the message type and then use one of the three ways to create a `Props`. This works for all actors

```tut
PropsOf[MyMessage][MyActor]
PropsOf[MyMessage](new MyActor)
PropsOf[MyMessage](classOf[MyActor])
```

Next, look at how you can improve type safety even further.

##### [&raquo; Typed Creator](creator.html)

```tut:invisible
system.shutdown()
```

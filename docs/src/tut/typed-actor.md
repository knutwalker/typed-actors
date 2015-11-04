---
layout: page
title: TypedActor
tut: 104
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
In order to use the `TypedActor`, you have to extend `TypedActor.Of[_]` and provide your message type via type parameter.

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
class MyActor extends TypedActor.Of[MyMessage] {
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

Please be aware of a ~~bug~~ feature that wouldn't fail on non-exhaustive checks.
If you use guards in your matchers, the complete pattern is optimistically treated as exhaustive; See [SI-5365](https://issues.scala-lang.org/browse/SI-5365), [SI-7631](https://issues.scala-lang.org/browse/SI-7631), and [SI-9232](https://issues.scala-lang.org/browse/SI-9232). Note the missing non-exhaustiveness warning in the next example.

```tut
val False = false
class MyOtherActor extends TypedActor.Of[MyMessage] {
  def typedReceive = Total {
    case Foo(foo) if False =>
  }
}
```

Unfortunately, this cannot be worked around by library code. Even worse, this would not result in a unhandled message but in a runtime match error.

#### Working with Union Types

Union typed [before](union.html) were declared on an already existing `Props` or `ActorRef` but how can we use union types together with `TypedActor`?

```tut:silent
case class Foo(foo: String)
case class Bar(bar: String)
case class Baz(baz: String)
case object SomeOtherMessage
```

(We're shadowing the previous definition of `Foo` and `Bar` here, they are reverted after this chapter).

Since union types are implemented at the type-level, there is no runtime value possible that would allow us to discriminate between those subtypes when running the receive block.

```tut:fail
class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
  def typedReceive: TypedReceive = {
    case Foo(foo) => println(s"received a Foo: $foo")
    case Bar(bar) => println(s"received a Bar: $bar")
    case Baz(baz) => println(s"received a Baz: $baz")
  }
}
```

We have to do this discrimination at type-level as well. Don't worry, it's less complicated as that sound. As a side note, sum types like `Either` are sometimes referred to as tagged union, the tag being the thing that would help us to discrimite at runtime – our union type is an untagged union instead.

The basics stay the same, you still extends `TypedActor.Of` and implement `typedReceive` but this time using either `Union` or `TotalUnion`. Use `Union` if you only cover some of the union types cases and `TotalUnion` if you want to cover _all_ cases. The compiler can perform exhaustiveness checks on the latter.
Both methods return a builder-style object that has an `on` method that must be used to enumerate the individual subcases of the union type and you close with a call to `apply`.

```tut
class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
  def typedReceive: TypedReceive = Union
    .on[Foo]{ case Foo(foo) => println(s"received a Foo: $foo") }
    .on[Bar]{ case Bar(bar) => println(s"received a Bar: $bar") }
    .on[Baz]{ case Baz(baz) => println(s"received a Baz: $baz") }
    .apply
}
```

Or if you have a total function for the cases, there is a shortcut:

```tut
class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
  def typedReceive: TypedReceive = Union
    .total[Foo]{ foo ⇒ println(s"received a Foo: $foo.foo") }
    .total[Bar]{ bar ⇒ println(s"received a Bar: $bar.bar") }
    .total[Baz]{ baz ⇒ println(s"received a Baz: $baz.baz") }
    .apply
}
```

You have to provide at least one case, you cannot define an empty behavior.

```tut:fail
class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
  def typedReceive: TypedReceive = Union
    .apply
}
```


If you remove one of those cases it still compiles, since `Union` does not check for exhaustiveness.

```tut
class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
  def typedReceive: TypedReceive = Union
    .on[Foo]{ case Foo(foo) => println(s"received a Foo: $foo") }
    .on[Baz]{ case Baz(baz) => println(s"received a Baz: $baz") }
    .apply
}
```

If you switch to `TotalUnion` you can see the compiler message telling that something is missing. Unfortunately it doesn't tell you _which_ case is missing exactly, although that might change in the future.

```tut:fail
class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
  def typedReceive: TypedReceive = TotalUnion
    .on[Foo]{ case Foo(foo) => println(s"received a Foo: $foo") }
    .on[Baz]{ case Baz(baz) => println(s"received a Baz: $baz") }
    .apply
}
```

As you can see, you basically provide a receive block for all relevant subtypes of the union. One such receive block is typed in its input, though you cannot use the `Total` helper as this one is fixed on the complete message type, the union type itself in this case.

```tut:fail
class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
  def typedReceive: TypedReceive = Union
    .on[Foo](Total { case Foo(foo) => println(s"received a Foo: $foo") })
    .apply
}
```

At any rate, the `Props` and `ActorRef` from this `TypedActor` are union typed as well.

```tut
val props = PropsFor[MyActor]
val ref = ActorOf(props)

ref ! Foo("foo")
ref ! Bar("bar")
ref ! Baz("baz")
```

```tut:fail
ref ! SomeOtherMessage
```


If you want to `context.become` with a union type there are some options.

1. You can use the `Union`/`TotalUnion` helper as described earlier.
2. You can use `unionBecome` if you only want to cover _one_ particular case.
   It is a shortcut for `typedBecome(Union.on[Msg]{ case ... }.apply)`


```tut
class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
  def typedReceive: TypedReceive = Union
    .on[Foo]{
       case Foo(foo) =>
       unionBecome.on[Bar] {
         case Bar(bar) => println(s"received a Boo: $bar")
       }
    }
    .apply
}
```


#### Stateless actor from a total function

```tut:invisible
sealed trait MyMessage
case class Foo(foo: String) extends MyMessage
case class Bar(bar: String) extends MyMessage
```

The companion object `TypedActor` has an `apply` method that wraps a total function in an actor and returns a prop for this actor.

```tut
val ref = ActorOf(TypedActor[MyMessage] {
  case Foo(foo) => println(s"received a Foo: $foo")
  case Bar(bar) => println(s"received a Bar: $bar")
})
```


#### Low-level TypedActor

You can also directly extend `TypedActor`, in which case you have to implement the abstract type `Message`. The `Of` constructor just does this for you by getting all information from the defined type parameter.
You want to use this you need the `TypedActor` as a trait, for example when mixing it together with other Actor traits, like `PersistenActor`.
For normal use-case, extending `TypedActor.Of[_]` is encouraged.


```tut
import scala.reflect.classTag
class MyTypedActor extends TypedActor {
  type Message = MyMessage

  def typedReceive = {
    case Foo(foo) =>
  }
}
```

You can even override the `receive` method, if you have to, using the `untypedFromTyped` method.

```tut
class MyTypedActor extends TypedActor {
  type Message = MyMessage

  override def receive =
    untypedFromTyped(typedReceive)

  def typedReceive = {
    case Foo(foo) =>
  }
}
```

Using this, you can mix a `TypedActor` and a `PersistentActor` together.

```tut
import akka.persistence.PersistentActor

class TypedPersistentActor extends TypedActor with PersistentActor with ActorLogging {
  type Message = MyMessage

  def persistenceId: String = "typed-persistent-id"

  val receiveRecover: Receive = akka.actor.Actor.emptyBehavior

  val typedReceive: TypedReceive = {
    case foo: Foo =>
      persist(foo)(f => context.system.eventStream.publish(foo))
  }

  val receiveCommand: Receive =
    untypedFromTyped(typedReceive)

  override def receive: Receive =
    receiveCommand
}
```


#### Going back to untyped land

Sometimes you have to receive messages that are outside of your protocol. A typical case is `Terminated`, but other modules and patterns have those messages as well.
You can use `Untyped` to specify a regular untyped receive block, just as if `receive` were actually the way to go. `Untyped` also works with union types without any special syntax.


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

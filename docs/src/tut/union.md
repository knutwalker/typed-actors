---
layout: page
title: Union typed actors
tut: 02
---

We will assume imports and the actor system from the [&laquo; Basic Usage](index.html).

```tut:invisible
import akka.actor._
import akka.LoggingReceive
import de.knutwalker.akka.typed._
implicit val system = ActorSystem("foo", config=Some(com.typesafe.config.ConfigFactory.parseString("""
akka.loglevel=DEBUG
akka.stdout-loglevel=OFF
akka.loggers=["akka.PrintLogger"]
akka.actor.debug.receive=on
""")))
```


#### Unrelated Messages

The actor messages [before](index.html#actor-definition) were defined as a sealed trait so that the actor can deal with all subclasses of this trait. That way the actor can deal with multiple types of messages.
This works great if you're in control of the messages, unfortunately this is not always the case. Sometimes you have to write an actor that receives multiple messages that are not part of the same sealed trait, possibly because you don't own said messages.
To still use `Typed Actors`, you could use `Any`, which is just as bad as using untyped actors directly.
Alternatively, you could use a [sum type](https://en.wikipedia.org/wiki/Sum_type) like `Either`, define the actor as `ActorRef[Either[A, B]]` and pattern match on the either in the receive block. This has some drawbacks though.
First, listing more than 2 different messages with Either gets very tedious and you'll probably start writing specific custom sum types for each different set of messages and end up with sealed traits that do nothing but wrap other messages and are thus just noisy boilerplate.
Second, there is a runtime overhead involved of wrapping and unwrapping the message in the sum type, i.e. you have to `apply` and `unapply` the `Left`/`Right` instances.
Third, and probably the most disruptive one, you cannot send any of the summed types directly but have to wrap them at tellsite, coupling the actor to the chosen sum type. This also means, that you cannot write proxy-like actors that sit in-between other actors because you have to change the messages.

`Typed Actors` offer an easy alternative, that solves all the aforementioned problems: **Union Types**.
Both, `ActorRef[A]` and `Props[A]`, have a `or[B]` method, that turns those types into an `ActorRef[A | B]` or a `Props[A | B]`, respectively.
`A | B` is a so called [union type](http://ktoso.github.io/scala-types-of-types/#union-type-span-style-color-red-span) (also sometimes called a disjoint or discriminated union) meaning it is either `A` or `B`. In this regard, it serves the same purpose as `Either[A, B]`, but it is a pure type-level construct. There is no runtime value possible for `A | B`, it is intended to be used a [phantom type](http://ktoso.github.io/scala-types-of-types/#phantom-type) to allow the compiler to apply specific constraints on certain methods.
You, as a library user, needn't worry about this; just read `A | B` as `A or B`.
As a side note, the implementation is different than the one provided by Miles, referenced in the link above and, dare I say, simpler; it's not based on Curry-Howard isomorphism and doesn't require unicode to type.

You can call `or` multiple times, creating an ever-growing union type. For example `ActorRef[A].or[B].or[C].or[D]` yields `ActorRef[A | B | C | D]`, which just reads `A or B or C or D`. There is no restriction on the length (certainly not at 22), although compile times will suffer for very large union types.
This solves the first problem, enumerating many types just works naturally. To be fair, this is mainly due to the infix notation. You could write `A Either B Either C` as well, but that's just weird while `A | B | C` comes naturally.
As mentioned before, `|` is a pure typelevel construct—there is no runtime value, not event a simple wrapper. This fact solves both, the aforementioned second and third issue. Since there is not even a valid runtime representation, there can be no overhead and there is no wrapping required at tellsite.
Okay, enough theory – lets see union types in action.

#### Union types

First, let's define some unrelated messages. Note that these are not part of a sealed trait hierarchy.

```tut:silent
case class Foo(foo: String)
case class Bar(bar: String)
case class Baz(baz: String)
case object SomeOtherMessage
```

Now, let's define an actor that receives all of these messages.

```tut:silent
class MyActor extends Actor {
  def receive = {
    case Foo(foo) => println(s"received a Foo: $foo")
    case Bar(bar) => println(s"received a Bar: $bar")
    case Baz(baz) => println(s"received a Baz: $baz")
  }
}
```

Define a `Props` for one of those messages.

```tut
val props: Props[Foo] = Props[Foo, MyActor]
```

Now just list the other message types using `or`, either on the `Props` or on a created `ActorRef`.

```tut
val props2: Props[Foo | Bar] = props.or[Bar]
val ref2: ActorRef[Foo | Bar] = ActorOf(props2, name = "my-actor")
val ref: ActorRef[Foo | Bar | Baz] = ref2.or[Baz]
```

Now you can send either one of the messages that are listed in the union type.

```tut
ref ! Foo("foo")
ref ! Bar("bar")
ref ! Baz("baz")
```

And if you try to send a message that is not part of the type union, you will get a compile error.

```tut:fail
ref ! SomeOtherMessage
```

As you can see, there are no wrappers involved. When you send the message, the compiler checks that the message you want to send is part of the union and if this checks succeeds, the compiler will allow the call to `!` (by not failing to compile).
Since there can be no runtime value of the union type, there is a clear distinction for the dispatch to the check if the message itself is the specified type or a subtype thereof and the check if the message is part of the specified union type.

Union types will return later; for now, the next part is to learn how to interact with the less safer parts of Akka.

##### [&raquo; Unsafe Usage](unsafe.html)


```tut:invisible
system.shutdown()
```

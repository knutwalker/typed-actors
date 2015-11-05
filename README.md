[![Build Status][ci-img]][ci]
[![Coverage][coverage-img]][coverage]
[![Maven][maven-img]][maven]
[![Gitter][gitter-img]][gitter]
[![Apache License][license-img]][license]

# Typed Actors

compile-time typechecked akka actors.

**Note**: For Akka 2.4, you have to add `-a24` to the version number. For example `1.1.0` becomes `1.1.0-a24`.
The first version available for Akka 2.4 is `1.4.0`.

<!--- TUT:START -->
```scala
libraryDependencies ++= List(
  "de.knutwalker" %% "typed-actors" % "1.5.0",
  "de.knutwalker" %% "typed-actors-creator" % "1.5.0"
)
```


## [Documentation][docs]

- [Motivation](#motivation)
- [Basic Usage](#basic-usage)
- [Union typed actors](#union-typed-actors)
- [Unsafe Usage](#unsafe-usage)
- [TypedActor](#typedactor)
- [Building Props](#building-props)
- [Typed Creator](#typed-creator)
- [Implementation Notes](#implementation-notes)
- [Comparison with Akka Typed](#comparison-with-akka-typed)

## Motivation


One critique of Akka, that [comes](https://groups.google.com/d/msg/akka-user/rLKk7-D_jHQ/M_Anx7vRNhcJ) up [every](http://noelwelsh.com/programming/2013/03/04/why-i-dont-like-akka-actors/#akkas-actors-are-not-usefully-typed) [now](http://stew.vireo.org/posts/I-hate-akka/) and [then](http://stackoverflow.com/q/5547947/2996265) is the lack of type safety. Actors essentially represent a `PartialFunction[Any, Unit]` which is, from a type point of view, something of the worst you can have. It tells you nothing useful; Anything can go in, it might or might not be processed and if so, anything anywhere anytime can happen. It forgoes all the benefits of a statically typed language.
There are many reasons for this though, amongst others: location transparency and `context.become`. While its true that only `Any` allows us to model _everything_ that _can_ happen, it doesn't mean that everything _will always_ happen. Not every actor gets moved around between different nodes and changes its behavior to something completely unrelated over and over again.

So, why not tell the compiler that we know something about certain actors and have it help us? We're in a statically typed language after all. We're used to compiler support when it comes to refactoring, design and composition. Why forgo this for the sake of a feature I don't want to use.

Hence, `Typed Actors`!

Akka underwent some experiments itself, for example  from [typed-channels](http://doc.akka.io/docs/akka/2.2.0/scala/typed-channels.html) and [typed-actors](http://doc.akka.io/docs/akka/2.3.0/scala/typed-actors.html) to [akka-typed](http://doc.akka.io/docs/akka/2.4.0/scala/typed.html).
Especially the last approach, `Akka Typed` is really nice and the benefit of having an `ActorRef[A]` lead to the creation of this library.

`Typed Actors` has the following goals:

- add a compile-time layer to existing `ActorRef`s with minimal runtime overhead
- be compatible with all of the existing Akka modules, traits, and extensions in terms of composition and behavior

and the following non-goals:

- enforce an impenetrable mantle of types, don't fight the users knowledge about the actor system, those *are* dynamic after all
- support Java

So, let's dive in.


## Basic Usage


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
ref: de.knutwalker.akka.typed.package.ActorRef[props.Message] = Actor[akka://foo/user/my-actor#-676349988]
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
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/$a#-1285645404]

scala> val future = ref ? MyMessage("foo")
future: scala.concurrent.Future[MyResponse] = scala.concurrent.impl.Promise$DefaultPromise@621703d

scala> val response = scala.concurrent.Await.result(future, 1.second)
response: MyResponse = MyResponse(foo)
```

Next up, learn how to mix multiple unrelated messages into the checked type.






## Union typed actors







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

```scala
case class Foo(foo: String)
case class Bar(bar: String)
case class Baz(baz: String)
case object SomeOtherMessage
```

Now, let's define an actor that receives all of these messages.

```scala
class MyActor extends Actor {
  def receive = {
    case Foo(foo) => println(s"received a Foo: $foo")
    case Bar(bar) => println(s"received a Bar: $bar")
    case Baz(baz) => println(s"received a Baz: $baz")
  }
}
```

Define a `Props` for one of those messages.

```scala
scala> val props: Props[Foo] = Props[Foo, MyActor]
props: de.knutwalker.akka.typed.Props[Foo] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())
```

Now just list the other message types using `or`, either on the `Props` or on a created `ActorRef`.

```scala
scala> val props2: Props[Foo | Bar] = props.or[Bar]
props2: de.knutwalker.akka.typed.Props[de.knutwalker.akka.typed.|[Foo,Bar]] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> val ref2: ActorRef[Foo | Bar] = ActorOf(props2, name = "my-actor")
ref2: de.knutwalker.akka.typed.ActorRef[de.knutwalker.akka.typed.|[Foo,Bar]] = Actor[akka://foo/user/my-actor#1473410450]

scala> val ref: ActorRef[Foo | Bar | Baz] = ref2.or[Baz]
ref: de.knutwalker.akka.typed.ActorRef[de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[Foo,Bar],Baz]] = Actor[akka://foo/user/my-actor#1473410450]
```

Now you can send either one of the messages that are listed in the union type.

```scala
scala> ref ! Foo("foo")
received a Foo: foo

scala> ref ! Bar("bar")
received a Bar: bar

scala> ref ! Baz("baz")
received a Baz: baz
```

And if you try to send a message that is not part of the type union, you will get a compile error.

```scala
scala> ref ! SomeOtherMessage
<console>:32: error: Cannot prove that message of type SomeOtherMessage.type is a member of ref.Message.
       ref ! SomeOtherMessage
           ^
```

As you can see, there are no wrappers involved. When you send the message, the compiler checks that the message you want to send is part of the union and if this checks succeeds, the compiler will allow the call to `!` (by not failing to compile).
Since there can be no runtime value of the union type, there is a clear distinction for the dispatch to the check if the message itself is the specified type or a subtype thereof and the check if the message is part of the specified union type.

You can turn an actor that accepts an union type into of its subcases with `only`:

```scala
scala> ref.only[Foo]
res4: de.knutwalker.akka.typed.package.ActorRef[Foo] = Actor[akka://foo/user/my-actor#310685349]

scala> ref.only[Bar]
res5: de.knutwalker.akka.typed.package.ActorRef[Bar] = Actor[akka://foo/user/my-actor#310685349]

scala> ref.only[Baz]
res6: de.knutwalker.akka.typed.package.ActorRef[Baz] = Actor[akka://foo/user/my-actor#310685349]
```

Which checks the untion type as well.

```scala
scala> ref.only[SomeOtherMessage]
<console>:31: error: not found: type SomeOtherMessage
       ref.only[SomeOtherMessage]
                ^
```


Union types will return later; for now, the next part is to learn how to interact with the less safer parts of Akka.






## Unsafe Usage






```scala
scala> val typedRef = ActorOf[MyMessage](props, name = "my-actor")
typedRef: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#-1040368688]
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
untypedRef: de.knutwalker.akka.typed.package.UntypedActorRef = Actor[akka://foo/user/my-actor#-1040368688]
```

For convenience, `akka.actor.ActorRef` is type aliased as `de.knutwalker.akka.typed.UntypedActorRef`.
Similarly, you can turn any untyped ref into a typed one using `typed`.

```scala
scala> val typedAgain = untypedRef.typed[MyMessage]
typedAgain: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#-1040368688]
```

As scala tends to infer `Nothing` as the most specific bottom type, you want to make sure to always provide a useful type.

```scala
scala> untypedRef.typed
res1: de.knutwalker.akka.typed.package.ActorRef[Nothing] = Actor[akka://foo/user/my-actor#-1040368688]
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
otherRef: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-other-actor#1861913463]

scala> otherRef ! Foo("foo")
[DEBUG] received handled message Foo(foo)
received a Foo: foo

scala> otherRef ! Bar("bar")
[DEBUG] received handled message Bar(bar)

scala> otherRef ! Foo("baz")
[DEBUG] received unhandled message Foo(baz)

scala> otherRef.untyped ! SomeOtherMessage
[DEBUG] received handled message SomeOtherMessage
received some other message
```

Making sure, that this cannot happen is outside of the scope of **Typed Actors**.
There is, however, a `TypedActor` trait which tries to provide _some_ help. Learn about it next.






## TypedActor






Having a typed reference to an actor is one thing, but how can we improve type-safety within the actor itself?
**Typed Actors** offers a `trait` called `TypedActor` which you can extend from instead of `Actor`.
`TypedActor` itself extends `Actor` but contains an abstract type member and typed receive method
instead of just an untyped receive method.
In order to use the `TypedActor`, you have to extend `TypedActor.Of[_]` and provide your message type via type parameter.

```scala
scala> class MyActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = {
     |     case Foo(foo) => println(s"received a Foo: $foo")
     |     case Bar(bar) => println(s"received a Bar: $bar")
     |   }
     | }
defined class MyActor

scala> val ref = ActorOf(Props[MyMessage, MyActor], name = "my-actor")
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#1080835611]

scala> ref ! Foo("foo")
received a Foo: foo

scala> ref ! Bar("bar")
received a Bar: bar
```

If you match on messages from a different type, you will get a compile error.

```scala
scala> class MyActor extends TypedActor.Of[MyMessage] {
     |   def typedReceive = {
     |     case SomeOtherMessage => println("received some other message")
     |   }
     | }
<console>:22: error: pattern type is incompatible with expected type;
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
otherRef: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-other-actor#-1956143584]

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

Please be aware of a ~~bug~~ feature that wouldn't fail on non-exhaustive checks.
If you use guards in your matchers, the complete pattern is optimistically treated as exhaustive; See [SI-5365](https://issues.scala-lang.org/browse/SI-5365), [SI-7631](https://issues.scala-lang.org/browse/SI-7631), and [SI-9232](https://issues.scala-lang.org/browse/SI-9232). Note the missing non-exhaustiveness warning in the next example.

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

Unfortunately, this cannot be worked around by library code. Even worse, this would not result in a unhandled message but in a runtime match error.

#### Working with Union Types

Union typed [before](#union-typed-actors) were declared on an already existing `Props` or `ActorRef` but how can we use union types together with `TypedActor`?

```scala
case class Foo(foo: String)
case class Bar(bar: String)
case class Baz(baz: String)
case object SomeOtherMessage
```

(We're shadowing the previous definition of `Foo` and `Bar` here, they are reverted after this chapter).

Since union types are implemented at the type-level, there is no runtime value possible that would allow us to discriminate between those subtypes when running the receive block.

```scala
scala> class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
     |   def typedReceive: TypedReceive = {
     |     case Foo(foo) ⇒ println(s"received a Foo: $foo")
     |     case Bar(bar) ⇒ println(s"received a Bar: $bar")
     |     case Baz(baz) ⇒ println(s"received a Baz: $baz")
     |   }
     | }
<console>:29: error: constructor cannot be instantiated to expected type;
 found   : Foo
 required: de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[Foo,Bar],Baz]
           case Foo(foo) ⇒ println(s"received a Foo: $foo")
                ^
<console>:30: error: constructor cannot be instantiated to expected type;
 found   : Bar
 required: de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[Foo,Bar],Baz]
           case Bar(bar) ⇒ println(s"received a Bar: $bar")
                ^
<console>:31: error: constructor cannot be instantiated to expected type;
 found   : Baz
 required: de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[Foo,Bar],Baz]
           case Baz(baz) ⇒ println(s"received a Baz: $baz")
                ^
```

We have to do this discrimination at type-level as well. Don't worry, it's less complicated as that sound. As a side note, sum types like `Either` are sometimes referred to as tagged union, the tag being the thing that would help us to discrimite at runtime – our union type is an untagged union instead.

The basics stay the same, you still extends `TypedActor.Of` and implement `typedReceive` but this time using either `Union` or `TotalUnion`. Use `Union` if you only cover some of the union types cases and `TotalUnion` if you want to cover _all_ cases. The compiler can perform exhaustiveness checks on the latter.
Both methods return a builder-style object that has an `on` method that must be used to enumerate the individual subcases of the union type and you close with a call to `apply`.

```scala
scala> class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
     |   def typedReceive: TypedReceive = Union
     |     .on[Foo]{ case Foo(foo) ⇒ println(s"received a Foo: $foo") }
     |     .on[Bar]{ case Bar(bar) ⇒ println(s"received a Bar: $bar") }
     |     .on[Baz]{ case Baz(baz) ⇒ println(s"received a Baz: $baz") }
     |     .apply
     | }
defined class MyActor
```

You have to provide at least one case, you cannot define an empty behavior.

```scala
scala> class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
     |   def typedReceive: TypedReceive = Union
     |     .apply
     | }
<console>:29: error: Cannot prove that de.knutwalker.akka.typed.TypedActor.MkPartialUnionReceive.Empty =:= de.knutwalker.akka.typed.TypedActor.MkPartialUnionReceive.NonEmpty.
           .apply
            ^
```


If you remove one of those cases it still compiles, since `Union` does not check for exhaustiveness.

```scala
scala> class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
     |   def typedReceive: TypedReceive = Union
     |     .on[Foo]{ case Foo(foo) ⇒ println(s"received a Foo: $foo") }
     |     .on[Baz]{ case Baz(baz) ⇒ println(s"received a Baz: $baz") }
     |     .apply
     | }
defined class MyActor
```

If you switch to `TotalUnion` you can see the compiler message telling that something is missing. Unfortunately it doesn't tell you _which_ case is missing exactly, although that might change in the future.

```scala
scala> class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
     |   def typedReceive: TypedReceive = TotalUnion
     |     .on[Foo]{ case Foo(foo) ⇒ println(s"received a Foo: $foo") }
     |     .on[Baz]{ case Baz(baz) ⇒ println(s"received a Baz: $baz") }
     |     .apply
     | }
<console>:31: error: Cannot prove that de.knutwalker.akka.typed.|[Foo,Baz] contains the same members as de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[Foo,Bar],Baz].
           .apply
            ^
```

As you can see, you basically provide a receive block for all relevant subtypes of the union. One such receive block is typed in its input, though you cannot use the `Total` helper as this one is fixed on the complete message type, the union type itself in this case.

```scala
scala> class MyActor extends TypedActor.Of[Foo | Bar | Baz] {
     |   def typedReceive: TypedReceive = Union
     |     .on[Foo](Total { case Foo(foo) ⇒ println(s"received a Foo: $foo") })
     |     .apply
     | }
<console>:29: error: constructor cannot be instantiated to expected type;
 found   : Foo
 required: de.knutwalker.akka.typed.|[de.knutwalker.akka.typed.|[Foo,Bar],Baz]
           .on[Foo](Total { case Foo(foo) ⇒ println(s"received a Foo: $foo") })
                                 ^
```

At any rate, the `Props` and `ActorRef` from this `TypedActor` are union typed as well.

```scala
scala> val props = PropsFor[MyActor]
props: de.knutwalker.akka.typed.Props[MyActor#Message] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> val ref = ActorOf(props)
ref: de.knutwalker.akka.typed.package.ActorRef[props.Message] = Actor[akka://foo/user/$a#442721323]

scala> ref ! Foo("foo")
[DEBUG] received handled message Foo(foo)
received a Foo: foo

scala> ref ! Bar("bar")
[DEBUG] received handled message Bar(bar)
received a Bar: bar

scala> ref ! Baz("baz")
[DEBUG] received handled message Baz(baz)
received a Baz: baz
```

```scala
scala> ref ! SomeOtherMessage
<console>:32: error: Cannot prove that message of type SomeOtherMessage.type is a member of ref.Message.
       ref ! SomeOtherMessage
           ^
```

#### Stateless actor from a total function




The companion object `TypedActor` has an `apply` method that wraps a total function in an actor and returns a prop for this actor.

```scala
scala> val ref = ActorOf(TypedActor[MyMessage] {
     |   case Foo(foo) => println(s"received a Foo: $foo")
     |   case Bar(bar) => println(s"received a Bar: $bar")
     | })
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/$a#-2111156869]
```


#### Low-level TypedActor

You can also directly extend `TypedActor`, in which case you have to implement the abstract type `Message`. The `Of` constructor just does this for you by getting all information from the defined type parameter.
You want to use this you need the `TypedActor` as a trait, for example when mixing it together with other Actor traits, like `PersistenActor`.
For normal use-case, extending `TypedActor.Of[_]` is encouraged.


```scala
scala> import scala.reflect.classTag
import scala.reflect.classTag

scala> class MyTypedActor extends TypedActor {
     |   type Message = MyMessage
     | 
     |   def typedReceive = {
     |     case Foo(foo) =>
     |   }
     | }
defined class MyTypedActor
```

You can even override the `receive` method, if you have to, using the `untypedFromTyped` method.

```scala
scala> class MyTypedActor extends TypedActor {
     |   type Message = MyMessage
     | 
     |   override def receive =
     |     untypedFromTyped(typedReceive)
     | 
     |   def typedReceive = {
     |     case Foo(foo) =>
     |   }
     | }
defined class MyTypedActor
```

Using this, you can mix a `TypedActor` and a `PersistentActor` together.

```scala
scala> import akka.persistence.PersistentActor
import akka.persistence.PersistentActor

scala> class TypedPersistentActor extends TypedActor with PersistentActor with ActorLogging {
     |   type Message = MyMessage
     | 
     |   def persistenceId: String = "typed-persistent-id"
     | 
     |   val receiveRecover: Receive = akka.actor.Actor.emptyBehavior
     | 
     |   val typedReceive: TypedReceive = {
     |     case foo: Foo ⇒
     |       persist(foo)(f => context.system.eventStream.publish(foo))
     |   }
     | 
     |   val receiveCommand: Receive =
     |     untypedFromTyped(typedReceive)
     | 
     |   override def receive: Receive =
     |     receiveCommand
     | }
defined class TypedPersistentActor
```


#### Going back to untyped land

Sometimes you have to receive messages that are outside of your protocol. A typical case is `Terminated`, but other modules and patterns have those messages as well.
You can use `Untyped` to specify a regular untyped receive block, just as if `receive` were actually the way to go. `Untyped` also works with union types without any special syntax.


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






## Building Props






#### Message Type Derivation

When creating a props for a `TypeActor`, we can derive the message type and thus reduce the amount of type annotation we have to write.
This is done with `PropsFor`.

Consider this typed actor.

```scala
class MyActor extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case Foo(foo) => println(s"received a Foo: $foo")
  }
}
```

Using `Props` we have to repeat the information, that this actor only accepts messages of type `MyMessage`, although the compiler knows about this.

```scala
scala> Props[MyMessage, MyActor] // MyMessage is repetitive
res0: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> Props(new MyActor) // message type derives as Nothing
res1: de.knutwalker.akka.typed.package.Props[Nothing] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class akka.actor.TypedCreatorFunctionConsumer,List(class MyActor, <function0>))

scala> Props[MyMessage, MyActor](new MyActor) // MyMessage and MyActor are repetitive
res2: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class akka.actor.TypedCreatorFunctionConsumer,List(class MyActor, <function0>))

scala> Props(classOf[MyActor]) // message type derives as Nothing
res3: Object{type Message = Nothing; type Self = akka.actor.Props} = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> Props[MyMessage, MyActor](classOf[MyActor]) // MyMessage and MyActor are repetitive
res4: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())
```

When you have a `TypedActor`, you can use `PropsFor` instead of `Props` to use the type information embedded in `TypedActor#Message`.

```scala
scala> PropsFor[MyActor]
res5: de.knutwalker.akka.typed.Props[MyActor#Message] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> PropsFor(new MyActor)
res6: de.knutwalker.akka.typed.package.Props[MyActor#Message] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class akka.actor.TypedCreatorFunctionConsumer,List(class MyActor, <function0>))

scala> PropsFor(classOf[MyActor])
res7: Object{type Message = MyMessage; type Self = akka.actor.Props} = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())
```

Of course, some of these cases can also be mitigated by using type ascription on the result type.

```scala
scala> val props: Props[MyMessage] = Props(new MyActor)
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class akka.actor.TypedCreatorFunctionConsumer,List(class MyActor, <function0>))

scala> val props: Props[MyMessage] = Props(classOf[MyActor])
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> val props: Props[MyMessage] = PropsFor[MyActor]
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> val props: Props[MyMessage] = PropsFor(new MyActor)
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class akka.actor.TypedCreatorFunctionConsumer,List(class MyActor, <function0>))

scala> val props: Props[MyMessage] = PropsFor(classOf[MyActor])
props: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())
```

#### Type Currying for Props

`PropsFor` only works with a `TypedActor`. There is yet another way to create a `Props`, that has the type information curried, `PropsOf`.
With `PropsOf`, you apply once with the message type and then use one of the three ways to create a `Props`. This works for all actors

```scala
scala> PropsOf[MyMessage][MyActor]
res8: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())

scala> PropsOf[MyMessage](new MyActor)
res9: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class akka.actor.TypedCreatorFunctionConsumer,List(class MyActor, <function0>))

scala> PropsOf[MyMessage](classOf[MyActor])
res10: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class MyActor,List())
```

Next, look at how you can improve type safety even further.





## Typed Creator





When creating a `Props`, the preferred way is to use the `(Class[_], Any*)` overload, since this one does not create a closure.
If you create a props from within an Actor using the `(=> Actor)` overload, you accidentally close over the `ActorContext`, that's shared state you don't want.
The problem with the constructor using `Class`, you don't get any help from the compiler. If you change one parameter, there is nothing telling you to change the Props constructor but the eventual runtime error (from your tests, hopefully).

Using shapeless, we can try to fix this issue.

#### Using the creator module

The types creator lives in a [separate module](http://search.maven.org/#search%7Cga%7C1%7Cg:%22de.knutwalker%22%20AND%20a:typed-actors-creator*) that you have to include first.

```scala
libraryDependencies += "de.knutwalker" %% "typed-actors-creator" % "1.5.0"
```

Next, you _have_ to use the [`TypedActor`](#typedactor) trait and you _have_ to make your actor a `case class`.
This is necessary, so that shapeless' generic machinery can pick up the required constructor parameters.

```scala
case class MyActor(param: String) extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case Foo(foo) => println(s"$param - received a Foo: $foo")
    case Bar(bar) => println(s"$param - received a Bar: $bar")
  }
}
```

Next, use the `Typed` constructor. It takes one type parameter, which is supposed to be your `TypedActor`.
Now you can use two methods, `props` and `create`. Both accept the same arguments as the constructor of your `TypedActor` and will either return a typed `Props` or typed `ActorRef`, respectively (thanks to some shapeless magic).

```scala
scala> Typed[MyActor].props("Bernd")
res0: de.knutwalker.akka.typed.Props[MyMessage] = Props(Deploy(,Config(SimpleConfigObject({})),NoRouter,NoScopeGiven,,),class akka.actor.TypedCreatorFunctionConsumer,List(class MyActor, <function0>))

scala> Typed[MyActor].create("Bernd")
res1: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/$a#-650500778]

scala> ActorOf(Typed[MyActor].props("Bernd"), "typed-bernd")
res2: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/typed-bernd#1638266135]
```

Wrong invocations are greeted with a compile error instead of a runtime error!

```scala
scala> Typed[MyActor].create()
<console>:26: error: type mismatch;
 found   : shapeless.HNil
 required: shapeless.::[String,shapeless.HNil]
       Typed[MyActor].create()
                            ^

scala> Typed[MyActor].create("Bernd", "Ralf")
<console>:26: error: type mismatch;
 found   : shapeless.::[String("Bernd"),shapeless.::[String("Ralf"),shapeless.HNil]]
 required: shapeless.::[String,shapeless.HNil]
       Typed[MyActor].create("Bernd", "Ralf")
                            ^

scala> Typed[MyActor].create(42)
<console>:26: error: type mismatch;
 found   : shapeless.::[Int(42),shapeless.HNil]
 required: shapeless.::[String,shapeless.HNil]
       Typed[MyActor].create(42)
                            ^
```

Hooray, Benefit!

As you can see, shapeless leaks in the error messages, but you can still easily see what parameters are wrong.
This technique uses whitebox macros under the hood, which means that support from IDEs such as IntelliJ will be meager, so prepare for red, squiggly lines.
If you open autocomplete on a `Typed[MyActor]`, you won't see the `create` or `props` methods but `createProduct` and `propsProduct`. This is a leaky implementation as well, better just ignore it and type against those IDE errors.


The next bits are about the internals and some good pratices..






## Implementation Notes





Typed Actors are implemented as a type tag, a structural type refinement.
This is very similar to [`scalaz.@@`](https://github.com/scalaz/scalaz/blob/81e68e845e91b54450a4542b19c1378f06aea861/core/src/main/scala/scalaz/package.scala#L90-L101) and a little bit to [`shapeless.tag.@@`](https://github.com/milessabin/shapeless/blob/6c659d253ba004baf74e20d5d815729552677303/core/src/main/scala/shapeless/typeoperators.scala#L28-L29)
The message type is put together with the surrounding type (`ActorRef` or `Props`) into a special type, that exists only at compile time.
It carries enough type information for the compiler reject certain calls to tell while not requiring any wrappers at runtime.

The actual methods are provided by an implicit ops wrapper that extends AnyVal, so that there is no runtime overhead as well.

The union type is inspired by shapeless' `HNil` or `Coproduct`. The main differences are: 1) There is no runtime, value-level representation and as such, there is no Inr/Inl/:: constructor, it's just the type `|` (instead of `::` or `:+:` for HList and Coproduct, respectively). 2) It doesn't have an end type, a base case like `HNil` or `CNil`. Other than that, the operations around the union type are similar to what you would write if you'd define a function for an HList: There is a typeclass representing the function and some implicit induction steps that recurse on the type.
There are some other union type implementations out there, including the one that is offered by shapeless itself but they often just focus on offering membership testing as functionality, while `Typed Actors` also includes a union set comparison to check whether two union types cover the same elements without them being defined in the same order.

#### Good Practices

Typed Actors does not try to prevent you from doing fancy things and shooting yourself in the foot, it rather wants to give you a way so you can help yourself in keeping your sanity.
That is, you can always switch between untyped and typed actors, even if the type information is not actually corresponding to the actors implementation. It is up to you to decide how much safety you want to trade in for flexibility.
That being said, you get the most benefit by using the [TypedActor](#typedactor) with the [Typed Creator](#typed-creator) and only on the `typedReceive` and `typedBecome` methods with the `Total` wrapper. Depending on the situation, you can fairly fine-tune the amount of untypedness you want to have.

One other thing that is frequently causing trouble is `sender()`.
For one, it's not referentially transparent, return the sender of whatever message the Actor is currently processing. This is causing trouble when the `sender()` call happens for example in a callback attached to a `Future`.
The other thing is, it's always an untyped actor and knowledge about the protocol has to be implicitly kept in the head of the developer.
For that reasons, it is a good idea to always provide a `replyTo: ActorRef[A]` field in the message itself and refrain from using `sender()`, ideally ever.

An example of how this could look like. First, the counter example using `sender()` as a quasi status quo.
To have a sensible `sender()` available, we're gonna use `akka.actor.Inbox`.

```scala
import akka.actor.ActorDSL._
val box = inbox()
```

This is a typical request reply cycle using `sender()`.

```scala
case class MyMessage(payload: Int)
case class MyResponse(payload: String)
case class MyActor() extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case MyMessage(payload) => sender() ! payload.toString
  }
}
```

```scala
scala> val ref = Typed[MyActor].create()
ref: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/$a#-466014441]

scala> box.send(ref.untyped, MyMessage(42))
```

Note that there already is a bug, as the return message was not wrapped in `MyResponse`.

```scala
scala> val MyResponse(response) = box.receive()
scala.MatchError: 42 (of class java.lang.String)
  ... 374 elided
```

Here's how that looks using the `replyTo` pattern.

```scala
case class MyResponse(payload: String)
case class MyMessage(payload: Int)(val replyTo: ActorRef[MyResponse])
case class MyActor() extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case m@MyMessage(payload) => m.replyTo ! MyResponse(payload.toString)
  }
}
```

```scala
scala> val ref = Typed[MyActor].create()
ref: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/$b#-940617015]

scala> ref ! MyMessage(42)(box.receiver.typed)

scala> val MyResponse(response) = box.receive()
response: String = 42
```

Let's try to reproduce the bug from earlier.

```scala
scala> case class MyActor() extends TypedActor.Of[MyMessage] {
     |   def typedReceive = {
     |     case m@MyMessage(payload) => m.replyTo ! payload.toString
     |   }
     | }
<console>:27: error: type mismatch;
 found   : String
 required: m.replyTo.Message
    (which expands to)  MyResponse
           case m@MyMessage(payload) => m.replyTo ! payload.toString
                                                            ^
```

Now the compiler has caught the bug, benefit!

The `replyTo` pattern is also important in [Akka Typed](http://doc.akka.io/docs/akka/snapshot/scala/typed.html).





## Comparison with Akka Typed


The [`Akka Typed`](http://doc.akka.io/docs/akka/snapshot/scala/typed.html) project is a module of Akka (as of 2.4) which aims to provide typesafe actors as well.
Akka typed takes a completely different approach, mirroring most of the untyped API and ultimately offering a completely new API to define your actors behavior. Currently, this implementation sits on top of untyped They are currently actors.
Let me add that I really like Akka Typed and having worked with it for some time lead me to think about how to bring type safety to the rest of Akka.

`Akka Typed` is not only about a typed `ActorRef[A]`, there's much more that's changed and is reason to use `Akka Typed`, both in general and over `Typed Actors`. It separates the behavior of your actors from its execution model, making them really easy to test; You can just use a synchronous stub execution model and you get to test just the behavior without concerning yourself about the how-to-test-this-async-thingy. The new behavior API is not just a convoluted `PartialFunction[A, Unit]` but allows you to split your behavior into nice little pieces and have them composed together. `Akka Typed`'s getting rid of some old (and bad) habits as well; `sender()` is gone, as are lifecycle methods that have to be overridden, even the `Actor` trait itself is gone. It's messages and behavior all the way down!

Those are all concerns that `Typed Actor` will never deal with, this is one important difference: `Typed Actors` is a possibility to add some compile-time checking while `Akka Typed` is a completely new API. Understandingly, `Akka Typed` is better at hiding their untyped implementation, nothing in the public API leads to the fact that something like an untyped actor could even exist.

On the other hand, having `Akka Typed` as a separate module means it is difficult to use the typed API with other modules. Most APIs expect an `akka.actor.ActorRef` and you can't get one from a akka-typed actor (well, you can, but it's dirty). This also applies to things like `ActorLogging` and `Stash`.
`Typed Actors` doesn't try to prevent you from going untyped and as there is no different runtime representation, it can be easily used with all existing akka modules.
However, if you mix typed/untyped code too much, you run into unhandled messages or maybe even runtime class cast exceptions or match errors (which ought to be bugs then, really).

`Typed Actors` makes it easy to deal with multiple types of messages, not just one `A` thanks to its [Union type](#union-typed-actors) support. Joining multiple behavior requires them to be of the same type, although you can get far with a little bit of type-fu. Basically, you can take advantage of the covariant nature of `ActorRef[-A]` (in `Typed Actors`, ActorRef is actually invariant) and create phantom intersection types (`A with B`) and upcast at tellsite. It is, however, something different whether you as the library user has to know how to fu or I as the library author know so you don't have to.

Also, `Akka Typed` is concerned with Java interop, which `Typed Actors` is not.
Nevertheless, `Akka Typed` is a – in my opinion – really nice project and its new API is a major improvement over the default `Actor`. The resulting patterns, like `replyTo` are a good idea to use with `Typed Actor`s as well.

That concludes the Usage Guide. I guess the only thing left is to go on hAkking!
<!--- TUT:END -->

## License

This code is open source software licensed under the Apache 2.0 License.


[ci-img]: https://img.shields.io/travis/knutwalker/typed-actors/master.svg
[coverage-img]: https://img.shields.io/codecov/c/github/knutwalker/typed-actors/master.svg
[maven-img]: https://img.shields.io/maven-central/v/de.knutwalker/typed-actors_2.11.svg?label=latest
[gitter-img]: https://img.shields.io/badge/gitter-Join_Chat-1dce73.svg
[license-img]: https://img.shields.io/badge/license-APACHE_2-green.svg

[ci]: https://travis-ci.org/knutwalker/typed-actors
[coverage]: https://codecov.io/github/knutwalker/typed-actors
[maven]: http://search.maven.org/#search|ga|1|g%3A%22de.knutwalker%22%20AND%20a%3Atyped-actors*_2.11
[gitter]: https://gitter.im/knutwalker/typed-actors
[license]: https://www.apache.org/licenses/LICENSE-2.0

[docs]: http://knutwalker.github.io/typed-actors/

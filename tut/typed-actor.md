---
layout: page
title: TypedActor
tut: 104
---

We will reuse the definitions and actors from the [&laquo; Unsafe Usage](unsafe.html).




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
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#984975751]

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
otherRef: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-other-actor#-687606846]

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

Union typed [before](union.html) were declared on an already existing `Props` or `ActorRef` but how can we use union types together with `TypedActor`?

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
ref: de.knutwalker.akka.typed.package.ActorRef[props.Message] = Actor[akka://foo/user/$a#2046139936]

scala> ref ! Foo("foo")
[DEBUG] received handled message Foo(foo)
received a Foo: foo

scala> ref ! Bar("bar")

scala> ref ! Baz("baz")
[DEBUG] received unhandled message Bar(bar)
[DEBUG] received handled message Baz(baz)
```
received a Baz: baz

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
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/$b#-2094215015]
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

##### [&raquo; Building Props](props.html)





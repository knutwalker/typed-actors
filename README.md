# Typed Actors [![Travis CI][travis-img]][travis] [![Coverage][codecov-img]][codecov] [![Maven][maven-img]][maven] [![Gitter][gitter-img]][gitter] [![Apache License][license-img]][license]

compile-time typechecked akka actors.

<!--- TUT:START -->
```scala
libraryDependencies ++= List(
  "de.knutwalker" %% "typed-actors" % "1.3.1",
  "de.knutwalker" %% "typed-actors-creator" % "1.3.1"
)
```


## [Documentation][docs]

- [Comparison with Akka Typed](#comparison-with-akka-typed)
- [Typed Creator](#typed-creator)
- [Implementation Notes](#implementation-notes)
- [Building Props](#building-props)
- [Unsafe Usage](#unsafe-usage)
- [TypedActor](#typedactor)
- [Basic Usage](#basic-usage)

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
ref: de.knutwalker.akka.typed.package.ActorRef[props.Message] = Actor[akka://foo/user/my-actor#180183502]
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
In order to achieve this, instead of sending an already instantiaded type, you send a function that, given the properly typed sender, will return the message.
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
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/$a#2043048217]

scala> val future = ref ? MyMessage("foo")
future: scala.concurrent.Future[MyResponse] = scala.concurrent.impl.Promise$DefaultPromise@aa96219

scala> val response = scala.concurrent.Await.result(future, 1.second)
response: MyResponse = MyResponse(foo)
```

Next up, learn how to interact with the less safer parts of Akka.






## Unsafe Usage






```scala
scala> val typedRef = ActorOf[MyMessage](props, name = "my-actor")
typedRef: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#-1725501433]
```

#### Autoreceive Messages

Some messages are automatically handled by some actors and need or can not be provided in the actors type.
One example is `PoisonPill`. To sent those kind of messages anyway, use `unsafeTell`.

```scala
scala> typedRef.unsafeTell(PoisonPill)
```

#### Switch Between Typed and Untyped

Also, some Akka APIs require you to pass an untyped ActorRef (the regular ActorRef).
You can easily turn your typed actor into an untyped one bu using `untyped`.

```scala
scala> val untypedRef = typedRef.untyped
untypedRef: de.knutwalker.akka.typed.package.UntypedActorRef = Actor[akka://foo/user/my-actor#-1725501433]
```

For convenience, `akka.actor.ActorRef` is type aliased as `de.knutwalker.akka.typed.UntypedActorRef`.
Similarly, you can turn any untyped ref into a typed one using `typed`.

```scala
scala> val typedAgain = untypedRef.typed[MyMessage]
typedAgain: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#-1725501433]
```

As scala tends to infer `Nothing` as the most specific bottom type, you want to make sure to always provide a useful type.

```scala
scala> untypedRef.typed
res1: de.knutwalker.akka.typed.package.ActorRef[Nothing] = Actor[akka://foo/user/my-actor#-1725501433]
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
otherRef: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-other-actor#1743163396]

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
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-actor#-954685732]

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
otherRef: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/my-other-actor#-362298108]

scala> otherRef ! Foo("foo")

scala> otherRef ! Bar("bar")
[DEBUG] received handled message Foo(foo)
received a Foo: foo
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
ref: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/$a#-1432583014]
```

Please be aware of a ~~bug~~ feature that wouldn't fail on non-exhaustive checks.
If you use guards in your matchers, the complete pattern is optimisiticaly treated as exhaustive; See [SI-5365](https://issues.scala-lang.org/browse/SI-5365), [SI-7631](https://issues.scala-lang.org/browse/SI-7631), and [SI-9232](https://issues.scala-lang.org/browse/SI-9232). Note the missing non-exhaustiveness warning in the next example.

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
libraryDependencies += "de.knutwalker" %% "typed-actors-creator" % "1.3.1"
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
res1: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/$a#23147986]

scala> ActorOf(Typed[MyActor].props("Bernd"), "typed-bernd")
res2: de.knutwalker.akka.typed.package.ActorRef[MyMessage] = Actor[akka://foo/user/typed-bernd#1127731090]
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

As you can see, shapeless leakes in the error messages, but you can still easily see what parameters are wrong.
This technique uses whitebox macros under the hood, which means that support from IDEs such as IntelliJ will be meager, so prepare for red, squiggly lines.
If you open autocomplete on a `Typed[MyActor]`, you won't see the `create` or `props` methods but `createProduct` and `propsProduct`. This is a leaky implementation as well, better just ignore it and type against those IDE errors.


The next bits are about the internals and some good pratices..






## Implementation Notes





Typed Actors are implemented as a type tag, a structural type refinement.
This is very similar to [`scalaz.@@`](https://github.com/scalaz/scalaz/blob/81e68e845e91b54450a4542b19c1378f06aea861/core/src/main/scala/scalaz/package.scala#L90-L101) and a little bit to [`shapeless.tag.@@`](https://github.com/milessabin/shapeless/blob/6c659d253ba004baf74e20d5d815729552677303/core/src/main/scala/shapeless/typeoperators.scala#L28-L29)
The message type is put togehter with the surrounding type (`ActorRef` or `Props`) into a special type, that exists only at compile time.
It carries enough type information for the compiler reject certain calls to tell while not requiring any wrappers at runtime.
The actual methods are provided by a implicit ops wrapper that extends AnyVal, so that there is no runtime overhead as well.

#### Good Practices

Typed Actors does not try to prevent you from doing fancy things and shooting yourself in the foot, it rather wants to give you a way so you can help yourself in keeping your sanity.
That is, you can aways switch between untyped and typed actors, even if the type information is not actually corresponding to the actors implementation. It is up to you to decide how much safety you want to trade in for flexibility.
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
ref: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/$a#1651546929]

scala> box.send(ref.untyped, MyMessage(42))
```

Note that there already is a bug, as the return message was not wrapped in `MyResponse`.

```scala
scala> val MyResponse(response) = box.receive()
scala.MatchError: 42 (of class java.lang.String)
  ... 378 elided
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
ref: de.knutwalker.akka.typed.ActorRef[MyMessage] = Actor[akka://foo/user/$b#369338725]

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

This is one important difference to; `Typed Actors` is a possibility to add some compile-time checking while `Akka Typed` is a completely new API.

`Akka Typed` is better at hiding their untyped implementation, nothing in the public API leads to the fact that something like an untyped actor could even exist.
They removed `sender()` and, in fact, the whole `Actor` trait. The new `Behavior` API is really nice and gives you a great way to compose and change your behaviour and it really shines in tests, as behaviors can be easily tested in a synchronous fashion, unrelated to the whole actors thing.

On the other hand, having `Akka Typed` as a separate module means it is difficult to use the typed API with other modules. Most APIs expect an `akka.actor.ActorRef` and you can't get one from a akka-typed actor (well, you can, but it's dirty). This also applies to things like `ActorLogging` and `Stash`.
`Typed Actors` doesn't try to prevent you from going untyped and as there is no different runtime representation, it can be easily used with all existing akka modules.
However, if you mix typed/untyped code too much, you run into unhandled messages or even runtime class cast exceptions.

Also, `Akka Typed` is concerned with Java interop, which `Typed Actors` is not.
Nevertheless, `Akka Typed` is a &emdash; in my opinion &emdash; really nice project and its new API is a major improvement over the default `Actor`. The resulting patterns, like `replyTo` are a good idea to use with `Typed Actor`s as well.

That concludes the Usage Guide. I guess the only thing left is to go on hAkking!
<!--- TUT:END -->

## License

This code is open source software licensed under the Apache 2.0 License.


[travis-img]: https://img.shields.io/travis/knutwalker/typed-actors/develop.svg
[codecov-img]: https://img.shields.io/codecov/c/github/knutwalker/typed-actors/develop.svg
[maven-img]: https://img.shields.io/maven-central/v/de.knutwalker/typed-actors_2.11.svg?label=latest
[gitter-img]: https://img.shields.io/badge/gitter-Join_Chat-1dce73.svg
[license-img]: https://img.shields.io/badge/license-APACHE_2-green.svg

[travis]: https://travis-ci.org/knutwalker/typed-actors
[codecov]: https://codecov.io/github/knutwalker/typed-actors?branch=develop
[maven]: http://search.maven.org/#search|ga|1|g%3A%22de.knutwalker%22%20AND%20a%3Atyped-actors*_2.11
[gitter]: https://gitter.im/knutwalker/typed-actors
[license]: https://www.apache.org/licenses/LICENSE-2.0

[docs]: http://knutwalker.github.io/typed-actors/

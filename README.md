# Typed Actors

```
libraryDependencies ++= List(
  "de.knutwalker" %% "typed-actors" % "1.0.0"
)
```

## Usage

Define your actors as and their protocol trait as usual.

```scala
import akka.actor.Actor

object MyActor {
  sealed trait MyMessage
  case class Foo(bar: String) extends MyMessage
}
class MyActor extends Actor {
  import MyActor._

  def receive = {
    case Foo(bar) => println("sreceived bar: $bar")
  }
}
```

Then, use `Props` and `ActorOf` from `de.knutwalker.akka.typed` instead of `akka.actor`

```scala
import akka.actor.ActorSystem
import de.knutwalker.akka.typed._

implicit val system = ActorSystem("name")

val props: Props[MyMessage] = Props(new MyActor)
val ref: ActorRef[MyMessage] = ActorOf(props, name = "my-actor")

ref ! Foo("bar") // compiles
ref ! SomeOtherMessage // compile error
```

Some messages are automatically handled by some actors and
need or can not be provided in the actors type. One example is `PoisonPill`.
To sent those kind of messages anyway, use `unsafeTell`:

```
ref.unsafeTell(PoisonPill)
```


### Typesafe creator

The recommended way to create a untyped `Props` is to use the `apply(Class[_], Any*)` overload, that doesn't create a closure that may accidentally capture state that is now shared.
This is especially true when your create child actors, as the
`Props(new ChildActor)` way will accidentally close over the current `ActorContext`.
The downside is, you completely lose typesafety.
Using shapeless, we can try to get this back.

First, include the creator module:

```
libraryDependencies ++= List(
  "de.knutwalker" %% "typed-actors" % "1.0.0",
  "de.knutwalker" %% "typed-actors-creator" % "1.0.0"
)
```

Second, extend `TypedActor.Of[MyMessage]` instead of `Actor` for your actor definition:

```scala
import de.knutwalker.akka.typed._

object MyActor {
  sealed trait MyMessage
  case class Foo(bar: String) extends MyMessage
}
case class MyActor(param: String) extends TypedActor.Of[MyActor.MyMessage] {
  import MyActor._

  def receiveMsg(msg: MyMessage) = msg match {
    case Foo(bar) => println("sreceived bar: $bar")
  }
}
```

You'll notice, that you have to implement a different method, `receiveMsg`, instead of `receive`. This method is a total method, not a partial one as `receive` is. This gives you the additional benefit of getting exhaustiveness checks for your messages.

Also, your actor has to be a `case class` in order for it to be
generically mapped to an `HList` by shapeless.

Third, use the `Typed` helper instead of `Props` to create the actor or
the props:

```scala
import akka.actor.ActorSystem
import de.knutwalker.akka.typed._

implicit val system = ActorSystem("name")

val ref: ActorRef[MyMessage] = Typed[MyActor].create("Bernd")
```

Wrong invocations are greeted with a compile error instead of a runtime error:

```scala
scala> Typed[MyActor].create()
<console>:39: error: type mismatch;
 found   : shapeless.HNil
 required: shapeless.::[String,shapeless.HNil]
       Typed[MyActor].create()

scala> Typed[MyActor].create("Bernd", "Ralf")
<console>:39: error: type mismatch;
 found   : shapeless.::[String("Bernd"),shapeless.::[String("Ralf"),shapeless.HNil]]
 required: shapeless.::[String,shapeless.HNil]
       Typed[MyActor].create("Bernd", "Ralf")

scala> Typed[MyActor].create(42)
<console>:39: error: type mismatch;
 found   : shapeless.::[Int(42),shapeless.HNil]
 required: shapeless.::[String,shapeless.HNil]
       Typed[MyActor].create(42)
```

Instead of `create`, you can use `props` to get a `Props[T]` and
use it with `ActorOf[T](Props[T], String)` to create a named actor.

```
scala> ActorOf(Typed[MyActor].props("Bernd"), "Ralf")
res0: ActorRef[MyMessage] = Actor[akka://test/user/Ralf#-1075245617]
```

This technique uses whitebox macros under the hood, which means that
support from IDEs such as IntelliJ will be meager, so prepare for red, squiggly lines.

## Implementation

Typed Actors are implemented as a type tag (very similar to `scalaz.@@`).
The message type is put togehter with the surrounding type (`ActorRef` or `Props`) into a special type, that exists only at compile time.
It carries enough type information for the compiler reject certain calls to tell while not requiring any wrappers at runtime.
The actual methods are provided by a implicit ops wrapper that extends AnyVal, so that there is no runtime overhead as well.



## What of Akka Typed?

The [Akka Typed](http://doc.akka.io/docs/akka/snapshot/scala/typed.html) project is an upcomping (with 2.4) module of Akka which aims to provide typesafe actors as well.
Akka typed takes a completely different approach, mirroring most of the untyped API and ultimately offering a completely new API to define your actors behavior. They are currently implemented on top of untyped actors.

As Akka typed is mirroring the untyped API, they are better at hiding their untyped implementation. They especially cover change of behavior (`context.become`) in a typesafe manner.
Also, Akka typed removes some of the more annoying parts of the untyped API, making it harder/impossible to mis-use Akka.
On the other hand, this means it is harder to use Akka typed with other modules of Akka, that are not type-ready yet (e.g. logging, FSM, etc).


`Typed Actors` is just a tiny wrapper that only exists during compile time. The actors you're using are the same (as in Java `==` reference same) as untyped actors.
It is also much easier to get access to the untyped part (it is the same thing, after all) and since `context.become` is in no way secured, it is also possible to diverge from the defined type and run into runtime cast exceptions.


## License

This code is open source software licensed under the Apache 2.0 License.

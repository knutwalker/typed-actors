---
layout: page
title: Typed Creator
tut: 06
---

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

Now that we covered all ways to [&laquo; Create Props](props.html), let's look closer at one more API that is unsafe.
When creating a `Props`, the preferred way is to use the `(Class[_], Any*)` overload, since this one does not create a closure.
If you create a props from within an Actor using the `(=> Actor)` overload, you accidentally close over the `ActorContext`, that's shared state you don't want.
The problem with the constructor using `Class`, you don't get any help from the compiler. If you change one parameter, there is nothing telling you to change the Props constructor but the eventual runtime error (from your tests, hopefully).

Using shapeless, we can try to fix this issue.

#### Using the creator module

The types creator lives in a [separate module](http://search.maven.org/#search%7Cga%7C1%7Cg:%22de.knutwalker%22%20AND%20a:typed-actors-creator*) that you have to include first.

```scala
libraryDependencies += "de.knutwalker" %% "typed-actors-creator" % "{{ site.data.version.version }}"
```

Next, you _have_ to use the [`TypedActor`](typed-actor.html) trait and you _have_ to make your actor a `case class`.
This is necessary, so that shapeless' generic machinery can pick up the required constructor parameters.

```tut:silent
case class MyActor(param: String) extends TypedActor.Of[MyMessage] {
  def typedReceive = {
    case Foo(foo) => println(s"$param - received a Foo: $foo")
    case Bar(bar) => println(s"$param - received a Bar: $bar")
  }
}
```

Next, use the `Typed` constructor. It takes one type parameter, which is supposed to be your `TypedActor`.
Now you can use two methods, `props` and `create`. Both accept the same arguments as the constructor of your `TypedActor` and will either return a typed `Props` or typed `ActorRef`, respectively (thanks to some shapeless magic).

```tut
Typed[MyActor].props("Bernd")
Typed[MyActor].create("Bernd")
ActorOf(Typed[MyActor].props("Bernd"), "typed-bernd")
```

Wrong invocations are greeted with a compile error instead of a runtime error!

```tut:fail
Typed[MyActor].create()
Typed[MyActor].create("Bernd", "Ralf")
Typed[MyActor].create(42)
```

Hooray, Benefit!

As you can see, shapeless leaks in the error messages, but you can still easily see what parameters are wrong.
This technique uses whitebox macros under the hood, which means that support from IDEs such as IntelliJ will be meager, so prepare for red, squiggly lines.
If you open autocomplete on a `Typed[MyActor]`, you won't see the `create` or `props` methods but `createProduct` and `propsProduct`. This is a leaky implementation as well, better just ignore it and type against those IDE errors.


The next bits are about the internals and some good pratices..

##### [&raquo; Implementation Notes](implementation.html)


```tut:invisible
system.shutdown()
```

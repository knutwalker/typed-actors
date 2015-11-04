---
layout: page
title: Building Props
tut: 05
---

With the [&laquo; Typed Actor](typed-actor.html) in place, we can now look at more ways to define a `Props`.




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

##### [&raquo; Typed Creator](creator.html)




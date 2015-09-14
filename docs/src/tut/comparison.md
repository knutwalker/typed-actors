---
layout: page
title: Comparison with Akka Typed
tut: 07
---

The [`Akka Typed`](http://doc.akka.io/docs/akka/snapshot/scala/typed.html) project is an upcomping (2.4) module of Akka which aims to provide typesafe actors as well.
Akka typed takes a completely different approach, mirroring most of the untyped API and ultimately offering a completely new API to define your actors behavior. Currently, this implementation sits on top of untyped They are currently actors.

This is one important difference to; `Typed Actors` is a possibility to add some compile-time checking while `Akka Typed` is a completely new API.

`Akka Typed` is better at hiding their untyped implementation, nothing in the public API leads to the fact that something like an untyped actor could even exist.
They removed `sender()` and, in fact, the whole `Actor` trait. The new `Behavior` API is really nice and gives you a great way to compose and change your behaviour and it really shines in tests, as behaviors can be easily tested in a synchronous fashion, unrelated to the whole actors thing.

On the other hand, having `Akka Typed` as a separate module means it is difficult to use the typed API with other modules. Most APIs expect an `akka.actor.ActorRef` and you can't get one from a akka-typed actor (well, you can, but it's dirty).  
`Typed Actors` doesn't try to prevent you from going untyped and as there is no different runtime representation, it can be easily used with all existing akka modules.
However, if you mix typed/untyped code too much, you run into unhandled messages or even runtime class cast exceptions.
 
Nevertheless, `Akka Typed` is a &emdash; in my opinion &emdash; really nice project and its new API is a major improvement over the default `Actor`. The resulting patterns, like `replyTo` are a good idea to use with `Typed Actor`s as well.

That concludes the Usage Guide. I guess the only thing left is to go on hAkking!

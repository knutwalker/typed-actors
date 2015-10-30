---
layout: page
title: Motivation
tut: 00
---

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

##### [&raquo; Basic Usage](index.html)

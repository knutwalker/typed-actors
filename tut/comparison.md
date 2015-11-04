---
layout: page
title: Comparison with Akka Typed
tut: 08
---

The [`Akka Typed`](http://doc.akka.io/docs/akka/snapshot/scala/typed.html) project is a module of Akka (as of 2.4) which aims to provide typesafe actors as well.
Akka typed takes a completely different approach, mirroring most of the untyped API and ultimately offering a completely new API to define your actors behavior. Currently, this implementation sits on top of untyped They are currently actors.
Let me add that I really like Akka Typed and having worked with it for some time lead me to think about how to bring type safety to the rest of Akka.

`Akka Typed` is not only about a typed `ActorRef[A]`, there's much more that's changed and is reason to use `Akka Typed`, both in general and over `Typed Actors`. It separates the behavior of your actors from its execution model, making them really easy to test; You can just use a synchronous stub execution model and you get to test just the behavior without concerning yourself about the how-to-test-this-async-thingy. The new behavior API is not just a convoluted `PartialFunction[A, Unit]` but allows you to split your behavior into nice little pieces and have them composed together. `Akka Typed`'s getting rid of some old (and bad) habits as well; `sender()` is gone, as are lifecycle methods that have to be overridden, even the `Actor` trait itself is gone. It's messages and behavior all the way down!

Those are all concerns that `Typed Actor` will never deal with, this is one important difference: `Typed Actors` is a possibility to add some compile-time checking while `Akka Typed` is a completely new API. Understandingly, `Akka Typed` is better at hiding their untyped implementation, nothing in the public API leads to the fact that something like an untyped actor could even exist.

On the other hand, having `Akka Typed` as a separate module means it is difficult to use the typed API with other modules. Most APIs expect an `akka.actor.ActorRef` and you can't get one from a akka-typed actor (well, you can, but it's dirty). This also applies to things like `ActorLogging` and `Stash`.
`Typed Actors` doesn't try to prevent you from going untyped and as there is no different runtime representation, it can be easily used with all existing akka modules.
However, if you mix typed/untyped code too much, you run into unhandled messages or maybe even runtime class cast exceptions or match errors (which ought to be bugs then, really).

`Typed Actors` makes it easy to deal with multiple types of messages, not just one `A` thanks to its [Union type](union.html) support. Joining multiple behavior requires them to be of the same type, although you can get far with a little bit of type-fu. Basically, you can take advantage of the covariant nature of `ActorRef[-A]` (in `Typed Actors`, ActorRef is actually invariant) and create phantom intersection types (`A with B`) and upcast at tellsite. It is, however, something different whether you as the library user has to know how to fu or I as the library author know so you don't have to.

Also, `Akka Typed` is concerned with Java interop, which `Typed Actors` is not.
Nevertheless, `Akka Typed` is a – in my opinion – really nice project and its new API is a major improvement over the default `Actor`. The resulting patterns, like `replyTo` are a good idea to use with `Typed Actor`s as well.

That concludes the Usage Guide. I guess the only thing left is to go on hAkking!

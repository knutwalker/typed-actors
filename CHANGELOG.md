# Change Log

## Versioning

This project tries to follows [Semantic Versioning](http://semver.org/),
adopted for Scala/ABI changes. Concretely, that means:

- a MAJOR increase breaks source compability
    - it introduces, removes and modifies public API
- a MINOR increase breaks binary compability
    - it may add new features to the public API
    - it may deprecate but never remove parts of the public API
    - it may, without warning or deprecation phase, remove and alter private API
- a PATCH increase is binary and source compatible with regards to its accompanying MINOR version
    - it may fix bugs, optimise performance
    - it may add new features  

In all fairness, claims to binary compability are not guaranteed but rather educated 
guesses based on [mima](https://github.com/typesafehub/migration-manager) reports.



## [Unreleased][unreleased]
**This release is source compatible with the previous release, but not binary compatible.**

### Added
- `unionBecome` to change behavior when the actor is of a union type
- `apply` on union receive builders can be inferred

### Fixed
- Provers for union type membership were unnecessarily left-biased, see #8


## [1.5.1][1.5.1] - 2015-11-05

### Added
- `only` method to union typed actors to gain a view into a specific subcase of the union 


## [1.5.0][1.5.0] - 2015-11-04
**This release is source compatible with the previous release, but not binary compatible.**

### Fixed
- the dependeny on `akka-actor` is set to the `provided` scope again

### Changed
- `TypedActor.Of` no longer requires an implicit classTag to be available.

### Added
- Phantom Union types for Typed Actors to support mulitple unrelated messages


## [1.4.0][1.4.0] - 2015-10-16
**This release is source compatible with the previous release, but not binary compatible.**

### Added
- New modules for Akka 2.4

### Changed
- `TypedActor` can now be extended directly and used as a trait


## [1.3.1][1.3.1] - 2015-10-01
### Fixed
- Lubbing on `forward` could lead to unchecked messages being send


## [1.3.0][1.3.0] - 2015-09-29
### Added
- `TypedActor.apply` to quickly create an actor from a total function
- Ask support for typed actors


## [1.2.0][1.2.0] - 2015-09-19
### Added
- typed `Props` gets all the pretty methods
- `Total` wrapper for usage with `typedBecome` or `typedReceive`
- `Untyped` wrapper for defining a `typedReceive` that can accept messages outside of the required type
- `PropsFor` constructors that can infer the message type from the given `TypedActor`
- `PropsOf` constructors that are type curried and can better infer the message type
- `untyped` and `typed` converters on typed and untyped actors, resp.
- lots of documentation

### Changed
- Rename `receiveMsg` to `typedBecome` and deprecate the former

### Removed
- `typedBecomeFull` in favor of `typedBecome` and `Total`
- Requirement of `TypedActor` to be a `case class`

### Fixed
- Sending a wrong message type the the untyped cast of an typed actor now results in an unhandled message instead of an error


## [1.1.0][1.1.0] - 2015-08-30
### Added
- `typedBecomeFull` on `TypedActor` for become with total functions

### Changed
- `typedBecome` and `receiveMsg` prefer partial functions over total ones


## [1.0.1][1.0.1] - 2015-08-30
### Added
- `typedBecome` on `TypedActor`

### Changed
- `TypedActor` wraps its `typedReceive` in a `LoggingReceive`

### Removed
- Support for Scala 2.10


## [1.0.0][1.0.0] - 2015-08-30
### Added
- Initial release, basic typed actors


[unreleased]: https://github.com/knutwalker/typed-actors/compare/v1.5.1...develop
[1.5.1]: https://github.com/knutwalker/typed-actors/compare/v1.5.0...v1.5.1
[1.5.0]: https://github.com/knutwalker/typed-actors/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/knutwalker/typed-actors/compare/v1.3.1...v1.4.0
[1.3.1]: https://github.com/knutwalker/typed-actors/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/knutwalker/typed-actors/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/knutwalker/typed-actors/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/knutwalker/typed-actors/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/knutwalker/typed-actors/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/knutwalker/typed-actors/compare/9cae71d329e808479e50cd6c10cd1ca4aca2343f...v1.0.0

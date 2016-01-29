import Build.autoImport._ // screw you, IntelliJ

lazy val union = project settings (
  name := "union",
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided")

lazy val core = project dependsOn union settings (
  name := "typed-actors",
  libraryDependencies += akkaActor(akkaActorVersion.value))

lazy val creator = project dependsOn core settings (
  libraryDependencies ++= List(
    akkaActor(akkaActorVersion.value),
    "com.chuusai" %% "shapeless" % "2.2.5"))

lazy val tests = project dependsOn (union, core % "test->test", creator) settings (
  dontRelease,
  libraryDependencies ++= List(
    akkaActor(akkaActorVersion.value),
    "org.specs2" %% "specs2-core"          % "3.7" % "test",
    "org.specs2" %% "specs2-matcher-extra" % "3.7" % "test"))

lazy val examples = project dependsOn (union, core % "test->test", creator) settings (
  dontRelease,
  libraryDependencies ++= List(
    akkaActor(akkaActorVersion.value),
    akkaPersistence(akkaActorVersion.value)))

lazy val docs = project dependsOn (union, core, creator) settings (
  tutsSettings(union, core, creator),
  libraryDependencies ++= List(
    akkaActor(akkaActorVersion.value),
    akkaPersistence(akkaActorVersion.value)))

lazy val parent = project in file(".") dependsOn (union, core, creator) aggregate (union, core, creator, tests, examples) settings parentSettings()

addCommandAlias("travis", ";clean;coverage;testOnly -- timefactor 3;coverageReport;coverageAggregate;docs/makeSite")

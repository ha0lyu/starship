
// Reference: https://github.com/ucb-bar/chipyard/blob/dd2ce08/build.sbt

Global / lintUnusedKeysOnLoad := false

import Tests._

val chisel6Version = "3.6-SNAPSHOT"
val scalaVersionFromChisel = "2.13.10"

lazy val commonSettings = Seq(
  organization := "ISCAS",
  version := "0.1",
  scalaVersion := scalaVersionFromChisel,
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Ytasty-reader",
    "-Ymacro-annotations"),
  allDependencies := {
    val dropDeps = Seq(("edu.berkeley.cs", "rocketchip"))
    allDependencies.value.filterNot { dep =>
      dropDeps.contains((dep.organization, dep.name))
    }
  },
  libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.3.1",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,

  exportJars := true,
  resolvers ++=
    Resolver.sonatypeOssRepos("snapshots") ++
    Resolver.sonatypeOssRepos("releases") :+
    Resolver.mavenLocal
)

val rocketChipDir = file("repo/rocket-chip")
val dependenciesDir = "repo/rocket-chip/dependencies"

def freshProject(name: String, dir: File): Project = {
  Project(id = name, base = dir / "src")
    .settings(
      Compile / scalaSource := baseDirectory.value / "main" / "scala",
      Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
    )
}

lazy val chisel6Settings = Seq(
  // libraryDependencies ++= Seq("org.chipsalliance" %% "chisel" % chisel6Version),
  // addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chisel6Version cross CrossVersion.full)
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % "3.6-SNAPSHOT" cross CrossVersion.full),
  libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.6-SNAPSHOT"
)

lazy val chiselSettings = chisel6Settings ++ Seq(
  libraryDependencies ++= Seq(
    "org.apache.commons" % "commons-lang3" % "3.12.0",
    "org.apache.commons" % "commons-text" % "1.9"
  )
)

lazy val scalaTestSettings =  Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.+" % "test"
  )
)

lazy val cde = (project in file(dependenciesDir + "/cde"))
  .settings(commonSettings)
  .settings(Compile / scalaSource := baseDirectory.value / "cde/src/chipsalliance/rocketchip")

// if this have problem, change it: repo/hardfloat:4225367ed
lazy val hardfloat = (project in file(dependenciesDir + "/hardfloat"))
  .settings(chiselSettings)
  .settings(commonSettings)
  .settings(scalaTestSettings)
  .settings(Compile / scalaSource := baseDirectory.value / "hardfloat/src")

lazy val rocketMacros  = (project in rocketChipDir / "macros")
  .settings(commonSettings)
  .settings(scalaTestSettings)

lazy val diplomacy = (project in file(dependenciesDir + "/diplomacy"))
  .dependsOn(cde)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(Compile / scalaSource := baseDirectory.value / "diplomacy/src")

lazy val rocketchip = freshProject("rocketchip", rocketChipDir)
  .dependsOn(hardfloat, rocketMacros, diplomacy, cde)
  .settings(commonSettings)
  .settings(chiselSettings)
  .settings(scalaTestSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "mainargs" % "0.5.0",
      "org.json4s" %% "json4s-jackson" % "4.0.5",
      "org.scala-graph" %% "graph-core" % "1.13.5"
    )
  )

lazy val rocketLibDeps = (rocketchip / Keys.libraryDependencies)

lazy val testchipip = (project in file("repo/testchipip"))
  .dependsOn(rocketchip, rocketchip_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val rocketchip_blocks = (project in file("repo/rocket-chip-blocks"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val boom = freshProject("boom", file("repo/riscv-boom"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val starship = (project in file("repo/starship"))
  .dependsOn(rocketchip, cde, rocketchip_blocks, boom)
  .settings(commonSettings)

lazy val root = (project in file("."))
  .dependsOn(starship)
  .settings(commonSettings)

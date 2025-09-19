import mill._
import scalalib._
import $file.repo.`rocket-chip`.dependencies.hardfloat.common
import $file.repo.`rocket-chip`.dependencies.cde.common
import $file.repo.`rocket-chip`.dependencies.diplomacy.common
import $file.repo.`rocket-chip`.common

val chiselVersion = "3.6.1"
val defaultScalaVersion = "2.13.9"
val pwd = os.Path(sys.env("MILL_WORKSPACE_ROOT"))

object v {
  def chiselIvy: Option[Dep] = Some(ivy"edu.berkeley.cs::chisel3:${chiselVersion}")
  def chiselPluginIvy: Option[Dep] = Some(ivy"edu.berkeley.cs:::chisel3-plugin:${chiselVersion}")
}

trait HasThisChisel extends SbtModule {
  def chiselModule: Option[ScalaModule] = None
  def chiselPluginJar: T[Option[PathRef]] = None
  def chiselIvy: Option[Dep] = v.chiselIvy
  def chiselPluginIvy: Option[Dep] = v.chiselPluginIvy
  override def scalaVersion = defaultScalaVersion
  override def scalacOptions = super.scalacOptions() ++
    Agg("-language:reflectiveCalls", "-Ymacro-annotations", "-Ytasty-reader")
  override def ivyDeps = super.ivyDeps() ++ Agg(chiselIvy.get)
  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(chiselPluginIvy.get)
}

object rocketchip extends RocketChip
trait RocketChip extends $file.repo.`rocket-chip`.common.RocketChipModule with HasThisChisel {
  override def scalaVersion: T[String] = T(defaultScalaVersion)
  override def millSourcePath = pwd / "repo" / "rocket-chip"
  def dependencyPath = pwd / "repo" / "rocket-chip" / "dependencies"
  def macrosModule = Macros
  def hardfloatModule = Hardfloat
  def cdeModule = CDE
  def diplomacyModule = Diplomacy
  def diplomacyIvy = None
  def mainargsIvy = ivy"com.lihaoyi::mainargs:0.5.4"
  def json4sJacksonIvy = ivy"org.json4s::json4s-jackson:4.0.6"
  override def moduleDeps = super.moduleDeps // ++ Seq(difftest)

  object Macros
    extends $file.repo.`rocket-chip`.common.MacrosModule
      with SbtModule {
      def scalaVersion: T[String] = T(defaultScalaVersion)
      def scalaReflectIvy = ivy"org.scala-lang:scala-reflect:${defaultScalaVersion}"
    }

  object Hardfloat 
    extends $file.repo.`rocket-chip`.dependencies.hardfloat.common.HardfloatModule with HasThisChisel {
      override def scalaVersion: T[String] = T(defaultScalaVersion)
      override def millSourcePath = dependencyPath / "hardfloat" / "hardfloat"
    }

  object CDE 
    extends $file.repo.`rocket-chip`.dependencies.cde.common.CDEModule with ScalaModule {
      def scalaVersion: T[String] = T(defaultScalaVersion)
      override def millSourcePath = dependencyPath / "cde" / "cde"
    }

  object Diplomacy 
    extends $file.repo.`rocket-chip`.dependencies.diplomacy.common.DiplomacyModule with ScalaModule {
      def scalaVersion: T[String] = T(defaultScalaVersion)
      override def millSourcePath = dependencyPath / "diplomacy" / "diplomacy"

      def chiselModule: Option[ScalaModule] = None
      def chiselPluginJar: T[Option[PathRef]] = None
      def chiselIvy: Option[Dep] = v.chiselIvy
      def chiselPluginIvy: Option[Dep] = v.chiselPluginIvy

      def cdeModule = CDE
      def sourcecodeIvy = ivy"com.lihaoyi::sourcecode:0.3.1"
    }
}

trait Boom extends ScalaModule with HasThisChisel {
  override def millSourcePath = pwd / "repo" / "riscv-boom"
  override def scalaVersion = defaultScalaVersion
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-P:chiselplugin:genBundleElements"
  )

  def rocketModule: ScalaModule = rocketchip

  override def moduleDeps = super.moduleDeps ++ Seq(rocketModule) //++ Seq(difftest) ++ Seq(ccover)

  override def ivyDeps = Agg(
    ivy"edu.berkeley.cs::chisel3:$chiselVersion",
    ivy"ch.epfl.scala::bloop-config:2.0.3"
  )

  override def scalacPluginIvyDeps = Agg(
    ivy"edu.berkeley.cs:::chisel3-plugin:$chiselVersion",
  )
}

object boom extends Boom

trait ChipBlocks extends ScalaModule with HasThisChisel {
  override def millSourcePath = pwd / "repo" / "rocket-chip-blocks"
  override def scalaVersion = defaultScalaVersion
  // override def scalacOptions = Seq(
  //   "-language:reflectiveCalls",
  //   "-deprecation",
  //   "-feature",
  //   "-Xcheckinit",
  //   "-P:chiselplugin:genBundleElements"
  // )

  def rocketModule: ScalaModule = rocketchip

  override def moduleDeps = super.moduleDeps ++ Seq(rocketModule) 

  override def ivyDeps = Agg(
    ivy"edu.berkeley.cs::chisel3:$chiselVersion",
    ivy"ch.epfl.scala::bloop-config:2.0.3"
  )

  override def scalacPluginIvyDeps = Agg(
    ivy"edu.berkeley.cs:::chisel3-plugin:$chiselVersion",
  )
}

object chipblocks extends ChipBlocks


trait StarShipModule extends ScalaModule {
  def rocketModule: ScalaModule
  def boomModule: ScalaModule
  def chipsModule: ScalaModule
  override def moduleDeps = super.moduleDeps ++ Seq(
    rocketModule, boomModule, chipsModule
  )
}

object starship extends Starship
trait Starship extends StarShipModule with HasThisChisel {
  override def millSourcePath = pwd
  override def sources = Task.Sources(millSourcePath / "repo" / "starship" / "src" )
  def rocketModule = rocketchip
  def boomModule = boom
  def chipsModule = chipblocks
}


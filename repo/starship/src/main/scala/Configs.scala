package starship

import chisel3._
import chisel3.util._
import freechips.rocketchip.system._
import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._

import freechips.rocketchip.rocket.{WithNBigCores}
import boom.v3.common.{WithNSmallBooms}

import sys.process._

case object FrequencyKey extends Field[Double](50)   // 50 MHz

class WithRocketCore extends Config(new WithNBigCores(1))
class WithBOOMCore extends Config(new WithNSmallBooms(1))

class StarshipBaseConfig extends Config(
  // new WithRoccExample ++
  new WithExtMemSize(0x80000000L) ++
  new WithNExtTopInterrupts(0) ++
  new WithDTS("zjv,starship", Nil) ++
  new WithEdgeDataBits(64) ++
  new WithCoherentBusTopology ++
  new WithoutTLMonitors ++
  new BaseConfig().alter((site,here,up) => {
    case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
      // invoke makefile for zero stage boot
      val freqMHz = 100 * 1000000
      val path = System.getProperty("user.dir")
      val make = s"make -C firmware/zsbl ROOT_DIR=${path} img"
      println("[Leaving rocketchip] " + make)
      require (make.! == 0, "Failed to build bootrom")
      println("[rocketchip Continue]")
      p.copy(hang = 0x10000, contentFileName = s"build/firmware/zsbl/bootrom.img")
    }
  })
)

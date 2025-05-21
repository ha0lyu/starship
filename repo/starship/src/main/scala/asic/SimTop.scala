package starship.asic

import starship._
import chisel3._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import org.chipsalliance.cde.config._
import freechips.rocketchip.system._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.resources.DeviceSnippet

class StarshipSimTop(implicit p: Parameters) extends StarshipSystem
    with CanHaveMasterAXI4MemPort
    with CanHaveSlaveAXI4Port
    with HasAsyncExtInterrupts
    with HasPeripheryDebug
    with CanHavePeripheryMagicDevice
{
  val chosen = new DeviceSnippet {
    def describe() = Description("chosen", Map(
      "bootargs" -> Seq(ResourceString("nokaslr"))
    ))
  }

  override lazy val module = new StarshipSimTopModuleImp(this)
}

class StarshipSimTopModuleImp[+L <: StarshipSimTop](_outer: L) extends StarshipSystemModuleImp(_outer)
    with HasRTCModuleImp
    with HasExtInterruptsModuleImp
    with DontTouch{
        // fix: Error: No implicit clock,  introduced by diplomacy:b4f93b774
        override def provideImplicitClockToLazyChildren:Boolean = true
    }

class TestHarness()(implicit p: Parameters) extends Module {
  
  val io = IO(new Bundle {
    val success = Output(Bool())
  })
  val ldut = LazyModule(new StarshipSimTop)
  val dut = Module(ldut.module)
  io.success := DontCare
  // Allow the debug ndreset to reset the dut, but not until the initial reset has completed
  // fix: reset is not a member of dut
  ldut.io_clocks.get.elements.values.foreach(_.clock := clock)
  val dut_reset = (reset.asBool | ldut.debug.map { debug => AsyncResetReg(debug.ndreset) }.getOrElse(false.B)).asBool
  ldut.io_clocks.get.elements.values.foreach(_.reset := dut_reset)

  dut.dontTouchPorts()
  dut.tieOffInterrupts()
  SimAXIMem.connectMem(ldut)

  ldut.l2_frontend_bus_axi4.foreach(
    p => {
      p.ar.valid := false.B
      p.ar.bits := DontCare
      p.aw.valid := false.B
      p.aw.bits := DontCare
      p.w.valid := false.B
      p.w.bits := DontCare
      p.r.ready := false.B
      p.b.ready := false.B
    }
  )
  Debug.connectDebug(ldut.debug, ldut.resetctrl, ldut.psd, clock, reset.asBool, io.success)
}

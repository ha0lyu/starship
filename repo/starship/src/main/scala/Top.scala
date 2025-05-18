// package starship

// import chisel3._
// import chisel3.util._
// import freechips.rocketchip.tile._
// import freechips.rocketchip.util._
// import freechips.rocketchip.prci._
// import org.chipsalliance.cde.config._
// import freechips.rocketchip.system._
// import freechips.rocketchip.subsystem._
// import freechips.rocketchip.diplomacy._
// import freechips.rocketchip.devices.debug._
// import freechips.rocketchip.devices.tilelink._


// // import sifive.blocks.devices.spi._
// // import sifive.blocks.devices.uart._

// class StarshipSystem(implicit p: Parameters) extends RocketSubsystem
//   with HasAsyncExtInterrupts
// {
//   val bootROM  = p(BootROMLocated(location)).map { BootROM.attach(_, this, CBUS) }
//   val maskROMs = p(MaskROMLocated(location)).map { MaskROM.attach(_, this, CBUS) }

//   override lazy val module = new StarshipSystemModuleImp(this)
// }

// class StarshipSystemModuleImp[+L <: StarshipSystem](_outer: L) extends RocketSubsystemModuleImp(_outer)
//   with HasRTCModuleImp
//   with HasExtInterruptsModuleImp
//   with DontTouch


package starship

import starship._
import chisel3._
import chisel3.{Module}
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
import sifive.blocks.devices.uart._
import freechips.rocketchip.resources.DeviceSnippet
import sifive.blocks.devices.uart.HasPeripheryUARTModuleImp
import starship.asic._

class StarshipSimTop(implicit p: Parameters) extends RocketSubsystem
    with CanHaveMasterAXI4MemPort
    with CanHaveSlaveAXI4Port
    with HasAsyncExtInterrupts
    with HasPeripheryUART
    with HasPeripheryDebug
    with CanHavePeripheryMagicDevice
{
  val chosen = new DeviceSnippet {
    def describe() = Description("chosen", Map(
      "bootargs" -> Seq(ResourceString("nokaslr"))
    ))
  }
  val bootROM  = p(BootROMLocated(location)).map { BootROM.attach(_, this, CBUS) }
  val maskROMs = p(MaskROMLocated(location)).map { MaskROM.attach(_, this, CBUS) }


  override lazy val module = new StarshipSimTopModuleImp(this)
}

class StarshipSimTopModuleImp[+L <: StarshipSimTop](_outer: L) extends RocketSubsystemModuleImp(_outer)
    with HasRTCModuleImp
    with HasExtInterruptsModuleImp
    with HasPeripheryUARTModuleImp
    with DontTouch

class TestHarness()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val uart_tx = Output(Bool())
    val uart_rx = Input(Bool())
  })

  val ldut = LazyModule(new StarshipSimTop)
  val dut = Module(ldut.module)

  ldut.io_clocks.get.elements.values.foreach(_.clock := clock)
  // Allow the debug ndreset to reset the dut, but not until the initial reset has completed
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

  dut.uart.headOption.foreach(uart => {
      uart.rxd := SyncResetSynchronizerShiftReg(io.uart_rx, 2, init = true.B, name=Some("uart_rxd_sync"))
      io.uart_tx  := uart.txd
    }
  )
}
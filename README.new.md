# Only support for verilator.
We want to run starship on verilator and build latest version of BOOM & Rocket.

## Build starship on verilator

OS: Ubuntu 20.04 LTS

0. riscv toolchain installed

1. Install Verilator v5.024 (ref: https://github.com/sycuricon/starship/issues/20)

2. Insatll sbt (ref: https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html)

3. git clone git clone https://github.com/riscv-zju/riscv-starship.git
   cd ./riscv-starship && git submodule update --init --recursive --progress

4. make patch; make vlt -j12
    
**wait for a while, and you will see:**
```
make[1]: Leaving directory '/home/riscv-starship/build/verilator'
cd /home/riscv-starship/build/verilator; ./Testbench +testcase=/home/riscv-starship/build/starship-dummy-testcase
-e [>] vcs start 1745998797.874
ToDo: cosim_cj_t::proc_reset
<stdin>:51.23-26: Warning (simple_bus_reg): /soc/dummy_stdout: missing or empty reg/ranges property
[*] `Commit & Judge' General Co-simulation Framework
                powered by Spike 1.1.1-dev
- core 1, isa: rv64gc_zicntr MSU vlen:128,elen:64
- memory configuration: 0x80000000@0x80000000 0x10000@0x10000 0x20000@0x2000
- elf file list: /home/riscv-starship/build/starship-dummy-testcase
- tohost address: 0x80001000
- fuzz information: [Handler] 0 page (0x0 0x0)
                    [Payload] 0 page (0x0 0x0)
TestHarness Memory Load Testcase: /home/riscv-starship/build/starship-dummy-testcase.hex
-e [>] vcs init 1745998798.805
[CJ] trying to communicate with testbench
*** PASSED *** Completed after                  414 simulation cycles
-e [>] vcs stop 1745998798.814
Finish time:              9458355 ns
[CJ] coverage sum =        308
- /home/riscv-starship/asic/sim/Testbench.v:177: Verilog $finish
- S i m u l a t i o n   R e p o r t: Verilator 5.024 2024-04-05
- Verilator: $finish at 415ns; walltime 0.948 s; speed 0.000 s/s
- Verilator: cpu 0.000 s on 1 threads; alloced 2449 MB
```

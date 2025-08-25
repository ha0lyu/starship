# Only support for verilator.
We want to run starship on verilator and build latest version of BOOM & Rocket.

## Build starship on verilator

OS: Ubuntu 20.04 LTS or Ubuntu 22.04 LTS

0. riscv toolchain installed

```bash
apt install -y autoconf automake autotools-dev curl python3 python3-pip python3-tomli libmpc-dev libmpfr-dev libgmp-dev gawk build-essential bison flex texinfo gperf libtool patchutils bc zlib1g-dev libexpat-dev ninja-build git cmake libglib2.0-dev libslirp-dev
git clone https://github.com/riscv/riscv-gnu-toolchain
cd riscv-gnu-toolchain && ./configure --prefix=/opt/riscv --enable-multilib && make -j `nproc`

# environment variable is needed
export PATH="$PATH:/opt/riscv/bin"
export RISCV="/opt/riscv"
```

1. Install Verilator v5.024 (ref: https://github.com/sycuricon/starship/issues/20)

```bash
apt install -y help2man perl make g++ libfl2 libfl-dev zlib1g

# To build or run Verilator, the following are optional but should be installed for good performance
apt install -y ccache mold libgoogle-perftools-dev numactl 

git clone https://github.com/verilator/verilator && unset VERILATOR_ROOT && cd verilator && git checkout v5.024 && autoconf && ./configure && make -j `nproc` && make install
```

2. Insatll sbt (ref: https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html)

```bash
apt install apt-transport-https curl gnupg -yqq 
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list 
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list 
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import 
chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg 

apt update 
apt install -y openjdk-11-jdk 
apt install -y sbt 
```

3. git clone & install mill

```bash
git clone https://github.com/ha0lyu/starship.git 
git checkout -b verilator origin/verilator 

# /usr/local/bin should be included in the environment variable
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.10.15/0.10.15 > /usr/local/bin/mill 
chmod +x /usr/local/bin/mill 
```

4. build

```bash
cd riscv-starship
git submodule update --init --recursive --progress
make patch
make vlt -j12
```
    
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

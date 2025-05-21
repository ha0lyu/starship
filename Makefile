# Starship Project
# Copyright (C) 2020-2023 by phantom
# Email: phantom@zju.edu.cn
# This file is under MIT License, see https://www.phvntom.tech/LICENSE.txt

TOP			:= $(CURDIR)
SRC			:= $(TOP)/repo
BUILD		:= $(TOP)/build
CONFIG		:= $(TOP)/conf
SBT_BUILD 	:= $(TOP)/target $(TOP)/project/target $(TOP)/project/project
ASIC		:= $(TOP)/asic

ifndef RISCV
  $(error $$RISCV is undefined, please set $$RISCV to your riscv-toolchain)
endif

GCC_VERSION	:= $(word 1,$(subst ., ,$(shell gcc -dumpversion)))
ifeq ($(shell echo $(GCC_VERSION)\>=9 | bc ),0)
  SCL_PREFIX := source scl_source enable devtoolset-10 &&
endif

all: vlt


#######################################
#                                      
#         Starship Configuration
#                                      
#######################################

include conf/build.mk

ifeq ($(STARSHIP_CORE),CVA6)
  ifndef CVA6_REPO_DIR
    $(error $$CVA6_REPO_DIR is undefined, please add $$CVA6_REPO_DIR in your configuration)
  else
    export CVA6_REPO_DIR
  endif
endif

#######################################
#                                      
#         Verilog Generator
#                                      
#######################################

ROCKET_TOP		:= $(STARSHIP_TH)
ROCKET_CONF		:= starship.With$(STARSHIP_CORE)Core,$(STARSHIP_CONFIG) #,starship.With$(STARSHIP_FREQ)MHz
ROCKET_SRC		:= $(SRC)/rocket-chip
ROCKET_BUILD	:= $(BUILD)/rocket-chip
ROCKET_SRCS     := $(shell find $(TOP) -name "*.scala")
ROCKET_OUTPUT	:= $(STARSHIP_CORE).$(lastword $(subst ., ,$(STARSHIP_TOP))).$(lastword $(subst ., ,$(STARSHIP_CONFIG)))
ROCKET_FIRRTL	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).fir
ROCKET_ANNO		:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).anno.json
ROCKET_DTS		:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).dts
ROCKET_ROMCONF	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).rom.conf
ROCKET_TOP_VERILOG	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).top.v
ROCKET_TH_VERILOG 	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).testharness.v
ROCKET_TOP_INCLUDE	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).top.f
ROCKET_TH_INCLUDE 	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).testharness.f
ROCKET_TOP_MEMCONF	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).sram.top.conf
ROCKET_TH_MEMCONF 	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).sram.testharness.conf

verilog-debug: FIRRTL_DEBUG_OPTION ?= -ll info

$(ROCKET_FIRRTL) $(ROCKET_DTS) $(ROCKET_ROMCONF) $(ROCKET_ANNO)&: $(ROCKET_SRCS)
	mkdir -p $(ROCKET_BUILD)
	mill starship.runMain starship.FIRRTLGenerator \
		--dir $(ROCKET_BUILD) \
		--top $(ROCKET_TOP) \
		--config $(ROCKET_CONF) \
		--name $(ROCKET_OUTPUT)

$(ROCKET_TOP_VERILOG) $(ROCKET_TOP_INCLUDE) $(ROCKET_TOP_MEMCONF) $(ROCKET_TH_VERILOG) $(ROCKET_TH_INCLUDE) $(ROCKET_TH_MEMCONF)&: $(ROCKET_FIRRTL)
	mkdir -p $(ROCKET_BUILD)
	mill starship.runMain starship.RTLGenerator \
		--infer-rw $(STARSHIP_TOP) \
		-T $(STARSHIP_TOP) -oinc $(ROCKET_TOP_INCLUDE) \
		--repl-seq-mem -c:$(STARSHIP_TOP):-o:$(ROCKET_TOP_MEMCONF) \
		-faf $(ROCKET_ANNO) -fct firrtl.passes.InlineInstances \
		-X verilog $(FIRRTL_DEBUG_OPTION) \
		-i $< -o $(ROCKET_TOP_VERILOG)

	mill starship.runMain starship.RTLGenerator \
		--infer-rw $(STARSHIP_TH) \
		-T $(STARSHIP_TOP) -TH $(STARSHIP_TH) -oinc $(ROCKET_TH_INCLUDE) \
		--repl-seq-mem -c:$(STARSHIP_TH):-o:$(ROCKET_TH_MEMCONF) \
		-faf $(ROCKET_ANNO) -fct firrtl.passes.InlineInstances \
		-X verilog $(FIRRTL_DEBUG_OPTION) \
		-i $< -o $(ROCKET_TH_VERILOG)
	touch $(ROCKET_TOP_INCLUDE) $(ROCKET_TH_INCLUDE)


#######################################
#
#         SRAM Generator
#
#######################################

FIRMWARE_SRC	:= $(TOP)/firmware
FIRMWARE_BUILD	:= $(BUILD)/firmware
FSBL_SRC		:= $(FIRMWARE_SRC)/fsbl
FSBL_BUILD		:= $(FIRMWARE_BUILD)/fsbl

ROCKET_INCLUDE 	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).f
ROCKET_ROM_HEX 	:= $(FSBL_BUILD)/sdboot.hex
ROCKET_ROM		:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).rom.v
ROCKET_TOP_SRAM	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).behav_srams.top.v
ROCKET_TH_SRAM	:= $(ROCKET_BUILD)/$(ROCKET_OUTPUT).behav_srams.testharness.v

VERILOG_SRC		:= $(ROCKET_TOP_SRAM) $(ROCKET_TH_SRAM) \
				   $(ROCKET_ROM) \
				   $(ROCKET_TH_VERILOG) $(ROCKET_TOP_VERILOG)

$(ROCKET_INCLUDE): | $(ROCKET_TH_INCLUDE) $(ROCKET_TOP_INCLUDE)
	mkdir -p $(ROCKET_BUILD)
	cat $(ROCKET_TH_INCLUDE) $(ROCKET_TOP_INCLUDE) 2> /dev/null | sort -u > $@
	echo $(VERILOG_SRC) >> $@
	sed -i "s/.*\.f$$/-f &/g" $@

$(ROCKET_TOP_SRAM): $(ROCKET_TOP_MEMCONF)
	mkdir -p $(ROCKET_BUILD)
	$(ROCKET_SRC)/scripts/vlsi_mem_gen $(ROCKET_TOP_MEMCONF) > $(ROCKET_TOP_SRAM)

$(ROCKET_TH_SRAM): $(ROCKET_TH_MEMCONF)
	mkdir -p $(ROCKET_BUILD)
	$(ROCKET_SRC)/scripts/vlsi_mem_gen $(ROCKET_TH_MEMCONF) > $(ROCKET_TH_SRAM)

$(ROCKET_ROM_HEX): $(ROCKET_DTS)
	mkdir -p $(FSBL_BUILD)
	$(MAKE) -C $(FSBL_SRC) PBUS_CLK=$(STARSHIP_FREQ)000000 ROOT_DIR=$(TOP) DTS=$(ROCKET_DTS) hex

$(ROCKET_ROM): $(ROCKET_ROM_HEX) $(ROCKET_ROMCONF)
	mkdir -p $(ROCKET_BUILD)
	$(ROCKET_SRC)/scripts/vlsi_rom_gen $(ROCKET_ROMCONF) $< > $@
	 @echo romgen complete!

verilog: $(VERILOG_SRC)
verilog-debug: verilog
verilog-patch: verilog
	# sed -i "s/s2_pc <= 42'h10000/s2_pc <= 42'h80000000/g" $(ROCKET_TOP_VERILOG)
	sed -i "s/s2_pc <= 40'h10000/s2_pc <= 40'h80000000/g" $(ROCKET_TOP_VERILOG)
	sed -i "s/core_boot_addr_i = 64'h10000/core_boot_addr_i = 64'h80000000/g" $(ROCKET_TOP_VERILOG)
	sed -i "s/40'h10000 : 40'h0/40'h80000000 : 40'h0/g" $(ROCKET_TOP_VERILOG)
	sed -i "s/ram\[initvar\] = {2 {\$$random}}/ram\[initvar\] = 0/g" $(ROCKET_TH_SRAM)
	sed -i "s/_covMap\[initvar\] = _RAND/_covMap\[initvar\] = 0; \/\//g" $(ROCKET_TOP_VERILOG)
	sed -i "s/_covState = _RAND/_covState = 0; \/\//g" $(ROCKET_TOP_VERILOG)
	sed -i "s/_covSum = _RAND/_covSum = 0; \/\//g" $(ROCKET_TOP_VERILOG)


#######################################
#
#         RTL Simulation
#
#######################################

SIM_DIR			:= $(ASIC)/sim
TB_TOP			?= Testbench

TESTCASE_ELF	:= $(STARSHIP_TESTCASE)
TESTCASE_BIN	:= $(shell mktemp)
TESTCASE_HEX	:= $(STARSHIP_TESTCASE).hex

CHISEL_DEFINE 	:= +define+PRINTF_COND=$(TB_TOP).printf_cond	\
			   	   +define+STOP_COND=!$(TB_TOP).reset			\
				   +define+RANDOMIZE							\
				   +define+RANDOMIZE_MEM_INIT					\
				   +define+RANDOMIZE_REG_INIT					\
				   +define+RANDOMIZE_GARBAGE_ASSIGN				\
				   +define+RANDOMIZE_INVALID_ASSIGN				\
				   +define+RANDOMIZE_DELAY=0.1


#######################################
#                                      
#         Spike & testcase
#                                      
#######################################

# Experience: If you don't have `make clean`, you can comment the following part

SPIKE_DIR		:= $(SRC)/riscv-isa-sim
SPIKE_SRC		:= $(shell find $(SPIKE_DIR) -name "*.cc" -o -name "*.h" -o -name "*.c")
SPIKE_BUILD		:= $(BUILD)/spike
SPIKE_LIB		:= $(addprefix $(SPIKE_BUILD)/,libcosim.a libriscv.a libdisasm.a libsoftfloat.a libfesvr.a libfdt.a)
SPIKE_INCLUDE	:= $(SPIKE_DIR) $(SPIKE_DIR)/cosim $(SPIKE_DIR)/fdt $(SPIKE_DIR)/fesvr \
			       $(SPIKE_DIR)/riscv $(SPIKE_DIR)/softfloat $(SPIKE_BUILD)

export LD_LIBRARY_PATH=$(SPIKE_BUILD)

$(SPIKE_BUILD)/Makefile:
	mkdir -p $(SPIKE_BUILD)
	cd $(SPIKE_BUILD); $(SCL_PREFIX) $(SPIKE_DIR)/configure

$(SPIKE_LIB)&: $(SPIKE_SRC) $(SPIKE_BUILD)/Makefile
	cd $(SPIKE_BUILD); $(SCL_PREFIX) make -j$(shell nproc) $(notdir $(SPIKE_LIB))

$(TESTCASE_HEX): $(TESTCASE_ELF)
	riscv64-unknown-elf-objcopy --gap-fill 0			\
		--set-section-flags .bss=alloc,load,contents	\
		--set-section-flags .sbss=alloc,load,contents	\
		--set-section-flags .tbss=alloc,load,contents	\
		-O binary $< $(TESTCASE_BIN)
	od -v -An -tx8 $(TESTCASE_BIN) > $@
	rm $(TESTCASE_BIN)


#######################################
#
#            Verilator
#
#######################################

VLT_BUILD	:= $(BUILD)/verilator
VLT_WAVE 	:= $(VLT_BUILD)/wave
VLT_TARGET  := $(VLT_BUILD)/$(TB_TOP)

VLT_CFLAGS	:= -std=c++17 $(addprefix -I,$(SPIKE_INCLUDE)) -I$(ROCKET_BUILD)

VLT_SRC_C	:= $(SIM_DIR)/spike_difftest.cc \
			   $(SPIKE_LIB) \
			   $(SIM_DIR)/timer.cc

VLT_SRC_V	:= $(SIM_DIR)/$(TB_TOP).v \
			   $(SIM_DIR)/spike_difftest.v \
			   $(SIM_DIR)/tty.v

VLT_DEFINE	:= +define+MODEL=$(STARSHIP_TH)				\
			   +define+TOP_DIR=\"$(VLT_BUILD)\"			\
			   +define+INITIALIZE_MEMORY				\
			   +define+CLOCK_PERIOD=1.0	   				\
			   +define+DEBUG_VCD						\
			   +define+TARGET_$(STARSHIP_CORE)

VLT_OPTION	:= -Wno-WIDTH -Wno-STMTDLY -Wno-fatal --timescale 1ns/10ps --trace --timing		\
			   +systemverilogext+.sva+.pkg+.sv+.SV+.vh+.svh+.svi+ 							\
			   +incdir+$(ROCKET_BUILD) +incdir+$(SIM_DIR) $(CHISEL_DEFINE) $(VLT_DEFINE)		\
			   --cc --exe --Mdir $(VLT_BUILD) --top-module $(TB_TOP) --main -o $(TB_TOP) 	\
			   -CFLAGS "-DVL_DEBUG -DTOP=${TB_TOP} ${VLT_CFLAGS}"
VLT_SIM_OPTION	:= +testcase=$(TESTCASE_ELF)

vlt-wave: 		VLT_SIM_OPTION	+= +dump 
vlt-jtag: 		VLT_SIM_OPTION	+= +jtag_rbb_enable=1
vlt-jtag-debug: VLT_SIM_OPTION	+= +dump +jtag_rbb_enable=1

$(VLT_TARGET): $(VERILOG_SRC) $(ROCKET_ROM_HEX) $(ROCKET_INCLUDE) $(VLT_SRC_V) $(VLT_SRC_C) $(SPIKE_LIB) 
	$(MAKE) verilog-patch
	mkdir -p $(VLT_BUILD) $(VLT_WAVE)
	cd $(VLT_BUILD); verilator $(VLT_OPTION) -f $(ROCKET_INCLUDE) $(VLT_SRC_V) $(VLT_SRC_C)
	make -C $(VLT_BUILD) -f V$(TB_TOP).mk $(TB_TOP)
	
vlt: $(VLT_TARGET) $(TESTCASE_HEX)
	cd $(VLT_BUILD); ./$(TB_TOP) $(VLT_SIM_OPTION)

vlt-wave: 		vlt
vlt-jtag: 		vlt
vlt-jtag-debug: vlt

gtkwave:
	gtkwave $(VLT_WAVE)/starship.vcd

#######################################
#
#               Utils
#
#######################################

.PHONY: clean clean-all patch

patch:
	find patch -name "*.patch" | \
		awk -F/ '{print \
			"(" \
				"echo \"Apply " $$0 "\" && " \
				"cd repo/" $$2 " && " \
				"git apply --ignore-space-change --ignore-whitespace ../../" $$0 \
			")" \
		}' | sh

clean:
	rm -rf $(BUILD)

clean-all:
	rm -rf $(BUILD) $(SBT_BUILD)

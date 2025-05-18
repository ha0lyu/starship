# Verilog Generation Configuration
##################################

STARSHIP_CORE	?= Rocket
STARSHIP_FREQ	?= 100
STARSHIP_TH = starship.TestHarness
STARSHIP_TOP = starship.StarshipSimTop
STARSHIP_CONFIG = starship.asic.StarshipSimConfig


# FPGA Configuration
####################

STARSHIP_BOARD	?= vc707


# Simulation Configuration
##########################

STARSHIP_TESTCASE	?= $(BUILD)/starship-dummy-testcase

$(BUILD)/starship-dummy-testcase:
	mkdir -p $(BUILD)
	wget https://github.com/sycuricon/riscv-tests/releases/download/dummy/rv64ui-p-simple -O $@

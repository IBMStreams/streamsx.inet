# Copyright (C) 2016, International Business Machines Corporation
# All Rights Reserved

.PHONY: all distributed standalone clean clean-runtime-generated-files clean-all run

ifeq ($(STREAMS_INSTALL),)
$(error error: environment variable STREAMS_INSTALL has to be set)
endif

STREAMS_INET_TOOLKIT ?=../../com.ibm.streamsx.inet:${STREAMS_INSTALL}/toolkits/com.ibm.streamsx.inet
SPLC_FLAGS ?= -a -t $(STREAMS_INET_TOOLKIT)
SPL_CMD_ARGS ?=
SPLC = $(STREAMS_INSTALL)/bin/sc
SPL_MAIN_COMPOSITE = Main
OUTPUT=output

all: clean distributed

distributed:
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE) --output-directory $(OUTPUT) $(SPL_CMD_ARGS)

standalone:
	$(SPLC) $(SPLC_FLAGS) -T -M $(SPL_MAIN_COMPOSITE) --output-directory $(OUTPUT) $(SPL_CMD_ARGS)

clean-all: clean clean-runtime-generated-files

clean:
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE) --output-directory $(OUTPUT)

clean-runtime-generated-files:
	-rm -rf data/*.txt

run: standalone
	output/bin/standalone

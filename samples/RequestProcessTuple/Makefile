# Copyright (C) 2016, International Business Machines Corporation. 
# All Rights Reserved.

.PHONY: all distributed standalone clean

ifeq ($(STREAMS_INSTALL),)
$(error error: environment variable STREAMS_INSTALL has to be set)
endif

STREAMS_INET_TOOLKIT ?=../../com.ibm.streamsx.inet
SPLC_FLAGS ?= -a -t $(STREAMS_INET_TOOLKIT)
SPLC = $(STREAMS_INSTALL)/bin/sc
SPL_CMD_ARGS ?= 
SPL_MAIN_COMPOSITE = application::DynTuple

all: clean distributed

distributed:
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE) $(SPL_CMD_ARGS)

standalone:
	$(SPLC) $(SPLC_FLAGS) -T -M $(SPL_MAIN_COMPOSITE) $(SPL_CMD_ARGS)

run:	standalone
	./output/bin/standalone

clean:
	-pkill -9 standalone
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE)


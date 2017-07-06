# Copyright (C) 2017, International Business Machines Corporation. 
# All Rights Reserved.

.PHONY: all distributed standalone clean

ifeq ($(STREAMS_INSTALL),)
$(error error: environment variable STREAMS_INSTALL has to be set)
endif

STREAMS_INET_TOOLKIT ?=../../com.ibm.streamsx.inet:${STREAMS_INSTALL}/toolkits/com.ibm.streamsx.inet
SPLC_FLAGS ?= -a -t $(STREAMS_INET_TOOLKIT)
SPLC = $(STREAMS_INSTALL)/bin/sc
SPL_CMD_ARGS ?= 
SPL_MAIN_COMPOSITE = com.ibm.streamsx.inet.sample.rest::HttpBlobInjectTest
DATA_DIR=$(abspath ./data)

all: clean distributed

distributed:
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE) --data-directory $(DATA_DIR) $(SPL_CMD_ARGS)

standalone:
	$(SPLC) $(SPLC_FLAGS) -T -M $(SPL_MAIN_COMPOSITE) --data-directory $(DATA_DIR) $(SPL_CMD_ARGS)

clean:
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE) --data-directory $(DATA_DIR)

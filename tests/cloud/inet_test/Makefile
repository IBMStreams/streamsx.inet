# Copyright (C) 2016, International Business Machines Corporation. 
# All Rights Reserved.

.PHONY: all distributed standalone clean

ifeq ($(STREAMS_INSTALL),)
$(error error: environment variable STREAMS_INSTALL has to be set)
endif

STREAMS_INET_TOOLKIT ?=../../../com.ibm.streamsx.inet:${STREAMS_INSTALL}/toolkits/com.ibm.streamsx.inet
SPLC_FLAGS ?= -a -t $(STREAMS_INET_TOOLKIT)
SPLC = $(STREAMS_INSTALL)/bin/sc
SPL_CMD_ARGS ?= 
SPL_MAIN_COMPOSITE1 = com.ibm.streamsx.inet.test::InetSourceTest
SPL_MAIN_COMPOSITE2 = com.ibm.streamsx.inet.test::HTTPRequestTest

all: clean distributed

distributed:
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE1) $(SPL_CMD_ARGS)
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE2) $(SPL_CMD_ARGS)

clean:
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE1)
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE2)
	rm -rf output

tkidx:
	$(STREAMS_INSTALL)/bin/spl-make-toolkit -i .


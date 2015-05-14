# Copyright (C) 2013-2014, International Business Machines Corporation
# All Rights Reserved

.PHONY: all clean standalone distributed clean-standalone clean-distributed clean-output clean-runtime-generated-files clean-all run

ifeq ($(STREAMS_INSTALL),)
$(error error: environment variable STREAMS_INSTALL has to be set)
endif

STREAMS_INET_TOOLKIT ?=../../com.ibm.streamsx.inet:${STREAMS_INSTALL}/toolkits/com.ibm.streamsx.inet
SPLC_FLAGS?=
SPLC_FLAGS+= -a -t $(STREAMS_INET_TOOLKIT)
SPLC = $(STREAMS_INSTALL)/bin/sc
SPL_MAIN_COMPOSITE = com.ibm.streamsx.inet.ftp.sample::RemoteDirScanAndGet
OUTPUT=output
DATA_DIR=$(abspath ./data)
STAND_ALONE_OUTPUT_DIR = $(OUTPUT)/Standalone
DISTRIBUTED_OUTPUT_DIR = $(OUTPUT)/Distributed

all: clean standalone distributed

run: standalone
	output/Standalone/bin/standalone
	@echo "*** RESULTS ***********************************************"
	@find data -name '*.txt' -print -exec cat {} \;

standalone:
	$(SPLC) $(SPLC_FLAGS) -T -M $(SPL_MAIN_COMPOSITE) --output-directory $(STAND_ALONE_OUTPUT_DIR) --data-directory $(DATA_DIR)

distributed:
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE) --output-directory $(DISTRIBUTED_OUTPUT_DIR) --data-directory $(DATA_DIR)

clean: clean-standalone clean-distributed clean-output

clean-all: clean clean-runtime-generated-files

clean-standalone:
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE) --output-directory $(STAND_ALONE_OUTPUT_DIR)
	-rm -rf toolkit.xml

clean-distributed:
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE) --output-directory $(DISTRIBUTED_OUTPUT_DIR)
	-rm -rf toolkit.xml

clean-output:
	-rm -rf $(OUTPUT)

clean-runtime-generated-files:
	-rm -rf data/*.txt

# Copyright (C) 2013-2014, International Business Machines Corporation
# All Rights Reserved

.PHONY: all clean clean-runtime-generated-files clean-all run

ifeq ($(STREAMS_INSTALL),)
$(error error: environment variable STREAMS_INSTALL has to be set)
endif

STREAMS_INET_TOOLKIT ?=../../com.ibm.streamsx.inet:${STREAMS_INSTALL}/toolkits/com.ibm.streamsx.inet
SPLC_FLAGS ?= -a -t $(STREAMS_INET_TOOLKIT)
SPL_CMD_ARGS ?=
SPLC = $(STREAMS_INSTALL)/bin/sc
SPL_MAIN_COMPOSITE = com.ibm.streamsx.inet.ftp.sample::PutFile
OUTPUT=output
DATA_DIR=$(abspath ./data)

all:
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE) --output-directory $(OUTPUT) --data-directory $(DATA_DIR) $(SPL_CMD_ARGS)

clean-all: clean clean-runtime-generated-files

clean:
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE) --output-directory $(OUTPUT)
	-rm -rf toolkit.xml

clean-runtime-generated-files:
	-rm -rf data/*.txt
	-rm -rf data/in/*.txt
	-rm -rf data/move/*.txt

run: all
	output/bin/standalone
	@echo "*** RESULTS ***********************************************"
	@find data -name '*.txt' -print -exec cat {} \;

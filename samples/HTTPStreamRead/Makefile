# Copyright (C) 2019, International Business Machines Corporation.
# All Rights Reserved.

.PHONY: all distributed standalone clean

STREAMS_INET_TOOLKIT ?=../../com.ibm.streamsx.inet:${STREAMS_INSTALL}/toolkits/com.ibm.streamsx.inet
ifeq ($(STREAMS_STUDIO_BUILDING), 1)
    $(info Building from Streams Studio, use env vars set by studio)
    SPLC = $(STREAMS_STUDIO_SC_PATH)
    DATA_DIR = $(STREAMS_STUDIO_DATA_DIRECTORY)
    OUTPUT_DIR = $(STREAMS_STUDIO_OUTPUT_DIRECTORY)
    TOOLKIT_PATH = $(STREAMS_STUDIO_SPL_PATH)
else
    $(info build use env settings)
    ifeq ($(STREAMS_INSTALL),)
        $(error error: environment variable STREAMS_INSTALL has to be set)
    endif
    SPLC = $(STREAMS_INSTALL)/bin/sc
    DATA_DIR = data
    OUTPUT_DIR = output
    TOOLKIT_PATH = $(STREAMS_INET_TOOLKIT)
endif

SPLC_FLAGS = -a
SPL_CMD_ARGS ?=
SPL_MAIN_COMPOSITE = com.ibm.streamsx.inet.http.sample::HTTPStreamSample

all: clean distributed

distributed:
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE)  -t ${TOOLKIT_PATH} --data-dir $(DATA_DIR) --output-dir $(OUTPUT_DIR) $(SPL_CMD_ARGS)

standalone:
	$(SPLC) $(SPLC_FLAGS) -T -M $(SPL_MAIN_COMPOSITE)  -t ${TOOLKIT_PATH} --data-dir $(DATA_DIR) --output-dir $(OUTPUT_DIR) $(SPL_CMD_ARGS)

clean:
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE)  -t ${TOOLKIT_PATH} --data-dir $(DATA_DIR) --output-dir $(OUTPUT_DIR) $(SPL_CMD_ARGS)

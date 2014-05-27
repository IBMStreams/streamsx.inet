# Copyright (C) 2013-2014, International Business Machines Corporation. 
# All Rights Reserved.

CXX=g++
SOURCES=impl/cpp/src/InetRetriever.cpp impl/cpp/src/URIHelper.cpp
OBJECTS=$(SOURCES:.cpp=.o)
INCLUDES=impl/cpp/include/InetRetriever.h impl/cpp/include/URIHelper.h
LIBS = -lcurl
CURL_INCLUDE_DIR=/usr/include
CFLAGS= -fPIC -I impl/cpp/include -I $(CURL_INCLUDE_DIR) -I $(STREAMS_INSTALL)/include
BIN_DIR=impl/cpp/bin
LDFLAGS=-shared

all: toolkit.xml

toolkit.xml:
	spl-make-toolkit -i .

clean:
	spl-make-toolkit -c -i .




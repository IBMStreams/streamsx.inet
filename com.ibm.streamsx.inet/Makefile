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

all: impl/lib/libstreams-tk-inet.so ant toolkit.xml

toolkit.xml:
	spl-make-toolkit -i .

$(BIN_DIR): 
	mkdir $(BIN_DIR)

$(BIN_DIR)/InetRetriever.o: impl/cpp/src/InetRetriever.cpp $(INCLUDES) $(BIN_DIR)
	$(CXX) -c -o $@ $< $(CFLAGS)

$(BIN_DIR)/URIHelper.o: impl/cpp/src/URIHelper.cpp $(INCLUDES) $(BIN_DIR)
	$(CXX) -c -o $@  $< $(CFLAGS)

impl/lib:
	mkdir impl/lib

impl/lib/libstreams-tk-inet.so: $(BIN_DIR)/InetRetriever.o $(BIN_DIR)/URIHelper.o impl/lib
	$(CXX) $(LDFLAGS) $(BIN_DIR)/InetRetriever.o $(BIN_DIR)/URIHelper.o $(LIBS) -o impl/lib/libstreams-tk-inet.so

clean:
	rm -rf impl/bin
	rm -f impl/lib/libstreams-tk-inet.so
	ant clean


ant:
	ant
#######################################################################
# Copyright (C)2012, International Business Machines Corporation and
# others. All Rights Reserved.
#######################################################################

# make expects BOOST_HOME and CPP-NETLIB_HOME variables
BOOST_HOME?=/usr/local/include
CPP-NETLIB_HOME?=/usr/local/include

SPL_PKGCFG=$(STREAMS_INSTALL)/bin/dst-pe-pkg-config.sh
SPL_PKG=dst-spl-pe-install
SPL_INCLUDE_OPTIONS = `$(SPL_PKGCFG) --cflags $(SPL_PKG)`
CXXFLAGS=-O3 -DNDEBUG -fPIC -std=gnu++11 -Wno-deprecated  -I$(BOOST_HOME) -I$(CPP-NETLIB_HOME)
LDFLAGS=-Llib -lcppnetlib-client-connections -lcppnetlib-uri -lssl
#OS = $(shell cat /etc/redhat-release | awk '{print substr($$3,1,1)}')
#ifeq ($(OS), 6)
#	TOOLSET=scl enable devtoolset-4
#else
#	TOOLSET=eval
#endif

.PHONY: clean distclean

all: lib/libhttp_client.so

lib/client.o: src/http/client.cpp include/http/client.hpp 
	@echo Compiling 'src/http/client.cpp' ...
	@g++ -Wall $(CXXFLAGS) -I include $(SPL_INCLUDE_OPTIONS) -c src/http/client.cpp -o $@ 
#	@$(TOOLSET) "g++ -Wall $(CXXFLAGS) -I include $(SPL_INCLUDE_OPTIONS) -c src/http/client.cpp -o $@" 

lib/libhttp_client.so: \
	lib/client.o
	@echo Building C++ shared library '$@'
	@g++ -shared -o $@ $^ $(LDFLAGS)
#	@$(TOOLSET) "g++ -shared -o $@ $^ $(LDFLAGS)"

clean:
	rm -fr lib/*.o lib/libhttp_client.so 

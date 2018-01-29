#######################################################################
# Copyright (C)2012, International Business Machines Corporation and
# others. All Rights Reserved.
#######################################################################

all: httpLib
	source $(STREAMS_INSTALL)/bin/streamsprofile.sh && spl-make-toolkit -m -i .
	
clean:
	cd impl; make clean
	
httpLib:
	cd impl; make all
	
httpLibClean:
	cd impl; make clean

spldoc:
	spl-make-doc -i .

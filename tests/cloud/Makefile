# Licensed Materials - Property of IBM
# Text Toolkit for IBM Streams
# (c) Copyright IBM Corp. 2019
# All rights reserved.                               


.PHONY: all clean 

all:
	cd inet_test; make all; cd -;

clean:
	cd inet_test; make clean; cd -;
	rm -rf __pycache__
	rm -f job_*.tar.gz

tkidx:
	cd inet_test; make tkidx; cd -;

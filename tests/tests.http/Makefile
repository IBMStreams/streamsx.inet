#
# *******************************************************************************
# * Copyright (C)2014, International Business Machines Corporation and *
# * others. All Rights Reserved. *
# *******************************************************************************
#

sc=${STREAMS_INSTALL}/bin/sc
args=-t ../../com.ibm.streamsx.inet
ns=com.ibm.streamsx.inet.http.tests
outputdir=./output

tests=HTTPStreamBasicFunctionTestMain HTTPStreamBadUrlTestMain HTTPStreamConsistentRegionFailMain HTTPPostBasicFunctionTestMain HTTPPostConsistentRegionFailMain

rntest=./scripts/testRunner.sh
ftest=./scripts/expectFail.sh
getprops=./scripts/getProps.sh
propfile=data/http.properties

all: ${tests}
	@echo "All Tests Passed"

compile: ${tests}

%TestMain:
	${sc} -T ${args} --output-directory ${outputdir}/$@ -M ${ns}::$@
	${rntest} ${outputdir}/$@/bin/standalone `${getprops} ${propfile} ${ns}::$@`


%FailMain:
	${ftest} ${sc} ${args} --output-directory ${outputdir}/$@ -M ${ns}::$@ 

clean:
	rm -rf ${outputdir}

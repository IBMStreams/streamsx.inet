# test for FTP Reader as dir scan

setCategory 'quick'

if isExisting 'TTPR_ftpServerHost' && isFalse 'TTPR_ftpServerHost'; then
	setSkip "\$TTPR_ftpServerHost is empty -> No ftp server available skip ftp tests"
fi

PREPS='copyOnly splCompile'
STEPS=(
	'mySubmit'
	'checkJobNo'
	'waitForFinAndHealth'
	'cancelJob'
	'myEval'
)

FINS='cancelJob'

mySubmit() {
	submitJobInterceptAndSuccess '-P' "host=$TTPR_ftpServerHost" '-P' 'username=anonymous' '-P' 'password=' '-P' 'protocol=ftp'
}

myEval() {
	linewisePatternMatchInterceptAndSuccess\
		"$TT_dataDir/Tuples"\
		"true"\
		"*fileName=\"1MB.zip\",size=*,isFile=true,transferCount=1,failureCount=0*"\
		"*fileName=\"20MB.zip\",size=*,isFile=true,transferCount=1,failureCount=0*"
}

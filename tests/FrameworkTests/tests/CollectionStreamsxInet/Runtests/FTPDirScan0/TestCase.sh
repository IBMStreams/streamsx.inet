# test for FTP Reader as dir scan

#--variantList='anonymous ftpuser ftpusersftp'

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
	case "$TTRO_variantCase" in
	anonymous)
		submitJobInterceptAndSuccess '-P' "host=$TTPR_ftpServerHost" '-P' 'username=anonymous' '-P' 'password=' '-P' 'protocol=ftp';;
	ftpuser)
		submitJobInterceptAndSuccess '-P' "host=$TTPR_ftpServerHost" '-P' "username=$TTPR_ftpServerUser" '-P' "password=$TTPR_ftpServerPasswd" '-P' 'protocol=ftp' '-P' "path=/$TTPR_ftpDirForReadTests/";;
	ftpusersftp)
		submitJobInterceptAndSuccess '-P' "host=$TTPR_ftpServerHost" '-P' "username=$TTPR_ftpServerUser" '-P' "password=$TTPR_ftpServerPasswd" '-P' 'protocol=sftp' '-P' "path=~/$TTPR_ftpDirForReadTests/";;
	*)
		printErrorAndExit "Wrong variant $TTRO_variantCase" $errRt;;
	esac
}

myEval() {
	linewisePatternMatchInterceptAndSuccess\
		"$TT_dataDir/Tuples"\
		"true"\
		"*fileName=\"1MB.zip\",size=*,isFile=true,transferCount=1,failureCount=0*"\
		"*fileName=\"20MB.zip\",size=*,isFile=true,transferCount=1,failureCount=0*"
}

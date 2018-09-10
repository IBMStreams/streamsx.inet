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
	local dirWithFileToCheck
	if [[ $TTRO_variantCase == 'anonymous' ]]; then
		dirWithFileToCheck="$TTRO_workDirSuite/anonymous"
	else
		dirWithFileToCheck="$TTRO_workDirSuite/ftpuser"
	fi
	checkAllFilesEqual "$dirWithFileToCheck" "$TT_dataDir" "1MB.zip 20MB.zip"
}

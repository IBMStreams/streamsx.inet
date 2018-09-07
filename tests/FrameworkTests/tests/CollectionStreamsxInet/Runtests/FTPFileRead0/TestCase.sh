# test for FTP Reader as dir scan

#--variantList='anonymous ftpuser'

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
	if [[ $TTRO_variantCase == 'anonymous' ]]; then
		submitJobInterceptAndSuccess '-P' "host=$TTPR_ftpServerHost" '-P' 'username=anonymous' '-P' 'password=' '-P' 'protocol=ftp'
	else
		submitJobInterceptAndSuccess '-P' "host=$TTPR_ftpServerHost" '-P' "username=$TTPR_ftpServerUser" '-P' "password=$TTPR_ftpServerPasswd" '-P' 'protocol=ftp' '-P' "path=/$TTPR_ftpDirForReadTests/"
	fi
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

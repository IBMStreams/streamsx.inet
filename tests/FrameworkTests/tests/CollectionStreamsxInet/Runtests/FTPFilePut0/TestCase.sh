# test for FTP Reader as dir scan

setCategory 'quick'

if isExisting 'TTPR_ftpServerHost' && isFalse 'TTPR_ftpServerHost'; then
	setSkip "\$TTPR_ftpServerHost is empty -> No ftp server available skip ftp tests"
fi

PREPS=(
	'copyOnly'
	'splCompile'
	'mkdir -p $TT_dataDir'
	"cp \"$TTRO_workDirSuite/anonymous/1MB.zip\" \"$TT_dataDir\""
	"cp \"$TTRO_workDirSuite/anonymous/20MB.zip\" \"$TT_dataDir\""
)

STEPS=(
	'mySubmit'
	'checkJobNo'
	'waitForFinAndHealth'
	'cancelJobAndLog'
	'getFiles'
	'myEval'
)

FINS='cancelJobAndLog'

mySubmit() {
	submitJobInterceptAndSuccess '-P' "host=$TTPR_ftpServerHost" '-P' "username=$TTPR_ftpServerUser" '-P' "password=$TTPR_ftpServerPasswd" '-P' 'protocol=ftp' '-P' 'path=/ftpw/'
}

getFiles() {
	mkdir resultFiles
	if ftp -n "$TTPR_ftpServerHost" << END_SCRIPT
quote USER $TTPR_ftpServerUser
quote PASS $TTPR_ftpServerPasswd
cd ftpw
ls
binary
get 1MB.zip resultFiles/1MB.zip
get 20MB.zip resultFiles/20MB.zip
bye
END_SCRIPT
	then
		printInfo "Transfer ok"
	else
		setFailure "Transfer failed"
	fi
}

myEval() {
	checkAllFilesEqual "$TT_dataDir" "resultFiles" "1MB.zip 20MB.zip"
}


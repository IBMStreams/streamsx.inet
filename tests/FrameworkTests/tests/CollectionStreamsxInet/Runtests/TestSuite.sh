setVar 'TTPR_timeout' 240

#setVar 'TTPR_ftpServerHost' 'speedtest.tele2.net'
# If the property TTPR_ftpServerHost was set soutside of this script, this ftp server is used for the tests
# If this property is not set, an attempt is made to start a local ftp server and this local server is used for the tests
# If this property is set and empty (false), all ftp test should be skipped
#setVar 'TTPR_ftpServerHost' 'speedtest.tele2.net'
setVar 'TTPR_ftpServerUser' 'ftpuser'
setVar 'TTPR_ftpServerPasswd' 'streams'


#Make sure instance and domain is running, ftp server running
PREPS='cleanUpInstAndDomainAtStart mkDomain startDomain mkInst startInst startFtpServer checkFtpServer'
FINS='stopFtpServer cleanUpInstAndDomainAtStop'


startFtpServer() {
	if isNotExisting 'TTPR_ftpServerHost'; then
		startStopFtpServer start
		setVar 'TTPR_ftpServerHost' "$HOSTNAME"
		setVar 'TTRO_ftpServerLocal' 'true'
	else
		printInfo "Property TTPR_ftpServerHost exists -> no start of local ftp server"
	fi
}

stopFtpServer() {
	if isExistingAndTrue 'TTRO_ftpServerLocal'; then
		startStopFtpServer stop
	else
		printInfo "no start of local ftp server -> no stop of local ftp server"
	fi
}

checkFtpServer() {
	if isFalse 'TTPR_ftpServerHost'; then
		printInfo "TTPR_ftpServerHost is empty -> No ftp test enabled"
		return 0
	fi
	printInfo "Check whether ftp server is reachable at $TTPR_ftpServerHost"
	if ftp -n "$TTPR_ftpServerHost" > ftpResult 2> ftpError << END_SCRIPT
quote USER anonymous
quote PASS
ls
get 1MB.zip
get 20MB.zip
bye
END_SCRIPT
	then
		if linewisePatternMatch ftpResult true '*1MB.zip*' '*20MB.zip*'; then
			printInfo "ftp server is reachable"
			setVar 'TTPR_ftpServerPubFile1' "$PWD/1MB.zip"
			setVar 'TTPR_ftpServerPubFile2' "$PWD/20MB.zip"
			setVar 'TTRO_ftpServerAvailable' 'true'
		else
			printError "Make sure that a remote ftp server is running and allows anonymous read access to files 1MB.zip and 20MB.zip"
			printError "Or enable a local vsftp and "
			printError "Prepare the public file storage with 2 files 1MB.zip and 20MB.zip"
			printError "Execure commands"
			printError "'openssl rand -out /var/ftp/1MB.zip 1048576'"
			printError "'openssl rand -out /var/ftp/20MB.zip 20971520'"
		fi
	fi
	return 0
}

startStopFtpServer() {
	printInfo "$1 local ftp server"
	#echo "\$PATH=$PATH"
	if ! /sbin/service vsftpd "$1"; then
		printError "Can not $1 local ftp server"
	fi
	return 0
}


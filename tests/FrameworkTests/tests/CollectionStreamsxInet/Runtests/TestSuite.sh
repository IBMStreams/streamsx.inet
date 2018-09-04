setVar 'TTPR_timeout' 240

#setVar 'TTPR_ftpServerHost' 'speedtest.tele2.net'
setVar 'TTPR_ftpServerHost' "$HOSTNAME"
setVar 'TTPR_ftpServerUser' 'ftpuser'
setVar 'TTPR_ftpServerPasswd' 'streams'


#Make sure instance and domain is running, ftp server running
PREPS='cleanUpInstAndDomainAtStart mkDomain startDomain mkInst startInst startFtpServer checkFtpServer'
FINS='stopFtpServer cleanUpInstAndDomainAtStop'


startFtpServer() {
	startStopFtpServer start
}

stopFtpServer() {
	startStopFtpServer stop
}

checkFtpServer() {
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
			printError "Make sure that the ftp server is running and allows anonymous read access"
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

	if ! service vsftpd "$1"; then
		printError "Can not $1 local ftp server"
	fi
	return 0
}


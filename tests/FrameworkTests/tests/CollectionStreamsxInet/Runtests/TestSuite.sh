setVar 'TTPR_timeout' 240

setVar 'TTPR_ftpServerHost' 'speedtest.tele2.net'

#Make sure instance and domain is running
PREPS='cleanUpInstAndDomain mkDomain startDomain mkInst startInst checkFtpServer'
FINS='cleanUpInstAndDomain'

checkFtpServer() {
	printInfo "Check whether ftp server is reachable at $TTPR_ftpServerHost"
	ftp -n "$TTPR_ftpServerHost" > ftpResult 2> ftpError << END_SCRIPT
quote USER anonymous
quote PASS
ls
bye
END_SCRIPT
	local result=$?
	if [[ $result -eq 0 ]]; then
		if linewisePatternMatch ftpResult true '*1MB.zip*'; then
			printInfo "ftp server is reachable"
			setVar 'TTRO_ftpServerAvailable' 'true'
		fi
	fi
	return 0
}


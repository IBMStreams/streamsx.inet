setVar 'TTPR_timeout' 240

setVar 'TTPR_ftpServerHost' 'speedtest.tele2.net'

#Make sure instance and domain is running
PREPS='cleanUpInstAndDomainAtStart mkDomain startDomain mkInst startInst checkFtpServer'
FINS='cleanUpInstAndDomainAtStop'

checkFtpServer() {
	printInfo "Check whether ftp server is reachable at $TTPR_ftpServerHost"
	if ftp -n "$TTPR_ftpServerHost" > ftpResult 2> ftpError << END_SCRIPT
quote USER anonymous
quote PASS
ls
bye
END_SCRIPT
	then
		if linewisePatternMatch ftpResult true '*1MB.zip*'; then
			printInfo "ftp server is reachable"
			setVar 'TTRO_ftpServerAvailable' 'true'
		fi
	fi
	return 0
}


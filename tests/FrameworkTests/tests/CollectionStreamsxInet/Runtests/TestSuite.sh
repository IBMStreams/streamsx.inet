# If the property TTPR_ftpServerHost was set soutside of this script, this ftp server is used for the tests
# If this property is not set, an attempt is made to start a local ftp server and this local server is used for the tests
# If this property is set and empty (false), no attempt is made to start the ftp server and all ftp test should be skipped
# If the running user is not in the sudoers list, you can start the ftp-server manually and setup TTPR_ftpServerHost variable like:
#     sudo service vsftpd start
#     export TTPR_ftpServerHost=$HOSTNAME
# or use the -D command line option to inject the property : -D TTPR_ftpServerHost=$HOSTNAME
# The ssh deamon must be running on the ftp-server host
#     sudo service sshd status
#     sudo service sshd start

#setVar 'TTPR_ftpServerHost' 'speedtest.tele2.net'
setVar 'TTPR_ftpServerUser' 'ftpuser'
setVar 'TTPR_ftpServerPasswd' 'streams'

setVar 'TTPR_ftpDirForReadTests' "ftpr$HOSTNAME"
setVar 'TTPR_ftpDirForWriteTests' "ftpw$HOSTNAME"

# http server install location
setVar 'TTPR_httpServerDir' "$TTRO_inputDir/../HTTPTestServer"

# expected http server definitions
# TTPR_httpServerHost
# if TTPR_httpServerHost is not set, the http server is started from http server install location TTPR_httpServerDir
# the following props depend usually from host
# TTPR_httpServerAddr  "${TTPR_httpServerHost}:8097"
# TTPR_httpsServerAddr "${TTPR_httpServerHost}:1443"

#Make sure instance and domain is running, ftp server running
PREPS='cleanUpInstAndDomainAtStart mkDomain startDomain mkInst startInst startFtpServer checkFtpServer prepFtpServer startHttpServer'
FINS='stopFtpServer cleanUpInstAndDomainAtStop stopHttpServer'


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
	if lftp > ftpResult << END_SCRIPT
set ftp:ssl-allow false
open $TTPR_ftpServerHost
ls
!mkdir anonymous
lcd anonymous
get 1MB.zip
get 20MB.zip
bye
END_SCRIPT
	then
		if linewisePatternMatch ftpResult true '*1MB.zip*' '*20MB.zip*'; then
			printInfo "ftp server is reachable"
			setVar 'TTPR_ftpServerPubFile1' "$PWD/1MB.zip"
			setVar 'TTPR_ftpServerPubFile2' "$PWD/20MB.zip"
		else
			printError "Make sure that a remote ftp server is running and allows anonymous read access to files 1MB.zip and 20MB.zip"
			printError "Or enable a local vsftp and "
			printError "Prepare the public file storage with 2 files 1MB.zip and 20MB.zip"
			printError "Execure commands"
			printError "'openssl rand -out /var/ftp/1MB.zip 1048576'"
			printError "'openssl rand -out /var/ftp/20MB.zip 20971520'"
		fi
	else
		printError "Transfer failure"
	fi
	return 0
}

prepFtpServer() {
	mkdir ftpuser
	if [[ -z $TTPR_ftpServerHost ]]; then
		return 0
	fi
	printInfo "Login as $TTPR_ftpServerUser and prepare remote directory $TTPR_ftpDirForReadTests and $TTPR_ftpDirForWriteTests"
	printInfo "Login as $TTPR_ftpServerUser and remote files $TTPR_ftpDirForReadTests/1MB.zip and $TTPR_ftpDirForReadTests/20MB.zip"
	openssl rand -out ftpuser/1MB.zip 1048576
	openssl rand -out ftpuser/20MB.zip 20971520
	if lftp << END_SCRIPT
set ftp:ssl-allow false
open -u ${TTPR_ftpServerUser},${TTPR_ftpServerPasswd} $TTPR_ftpServerHost
ls
rm -f $TTPR_ftpDirForReadTests/1MB.zip
rm -f $TTPR_ftpDirForReadTests/20MB.zip
rm -f $TTPR_ftpDirForWriteTests/0/1MB.zip
rm -f $TTPR_ftpDirForWriteTests/0/20MB.zip
rm -f $TTPR_ftpDirForWriteTests/1/1MB.zip
rm -f $TTPR_ftpDirForWriteTests/1/20MB.zip
rmdir $TTPR_ftpDirForReadTests
rmdir ${TTPR_ftpDirForWriteTests}/0
rmdir ${TTPR_ftpDirForWriteTests}/1
rmdir $TTPR_ftpDirForWriteTests

mkdir $TTPR_ftpDirForReadTests
mkdir $TTPR_ftpDirForWriteTests
mkdir ${TTPR_ftpDirForWriteTests}/0
mkdir ${TTPR_ftpDirForWriteTests}/1
put ftpuser/1MB.zip -o $TTPR_ftpDirForReadTests/1MB.zip
put ftpuser/20MB.zip -o $TTPR_ftpDirForReadTests/20MB.zip
ls
bye
END_SCRIPT
	then
		printInfo "Transfer ok"
	else
		printError "Transfer failure"
	fi
	return 0
}

startStopFtpServer() {
	if [[ ( $1 != 'start' ) && ( $1 != 'stop' ) ]]; then
		printErrorAndExit "$FUNCNAME wrong argument '$1'" $errRt
	fi
	printInfo "$1 local ftp server"
	#echo "\$PATH=$PATH"
	printInfo "try : /sbin/service vsftpd $1"
	if ! /sbin/service vsftpd "$1"; then
		printInfo "try : sudo /sbin/service vsftpd $1"
		if ! /sbin/service vsftpd "$1"; then
			printError "Can not $1 local ftp server"
		fi
	fi
	return 0
}

startHttpServer() {
	if isNotExisting 'TTPR_httpServerHost'; then
		"$TTPR_httpServerDir/start.sh"
		setVar 'TTPR_httpServerHost' "$HOSTNAME"
		setVar 'TTRO_httpServerLocal' 'true'
	else
		printInfo "Property TTPR_httpServerHost exists -> no start of local http server"
	fi
	# http server definitions
	setVar 'TTPR_httpServerAddr'  "${TTPR_httpServerHost}:8097"
	setVar 'TTPR_httpsServerAddr' "${TTPR_httpServerHost}:1443"
}

stopHttpServer() {
	if isExistingAndTrue 'TTRO_httpServerLocal'; then
		"$TTPR_httpServerDir/stop.sh"
	else
		printInfo "no start of local http server -> no stop of local http server"
	fi
}

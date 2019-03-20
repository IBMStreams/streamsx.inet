#!/bin/bash

IFS=$' \t\n'
set -o posix;
set -o errexit; set -o errtrace; set -o pipefail

declare -r command="${0##*/}"
declare -r commandPath="${0%/*}"

usage() {
	cat <<-EOF

	usage: ${command} [option]

	Start or stop the inet toolkit http test server.

	OPTIONS:
	-h|--help                : display this help
	--noprompt               : No interactive user interaction
	-f                       : start one instance in foreground - ports: 8097 1443
	-c                       : start one instance in foreground with client certificate request - ports 8098 1444
	EOF
}

userPrompt() {
	local pr="Continue or not? y/n "
	local inputWasY=''
	while read -p "$pr"; do
		if [[ $REPLY == y* || $REPLY == Y* || $REPLY == c* || $REPLY == C* ]]; then
			inputWasY='true'
			break

		elif [[ $REPLY == e* || $REPLY == E* || $REPLY == n* || $REPLY == N* ]]; then
			inputWasY=''
			break
		fi
	done
	if [[ -n $inputWasY ]]; then
		return 0
	else
		return 1
	fi
}

declare noprompt=''
declare foreground=''
declare certrequest=''

if [[ $# -gt 0 ]]; then
	case "$1" in
	-h|--help)
		usage
		exit 0;;
	--noprompt)
		noprompt='true';;
	-f)
		foreground='true';;
	-c)
		foreground='true'
		certrequest='true';;
	*)
		echo "Wrong command line argument $1 exit" >&2
		exit 1;;
	esac
fi

if [[ -e $commandPath/.pid ]]; then
	"$commandPath/stop.sh"
fi

declare javacmd='java'
#if [[ -e /usr/bin/java ]]; then
#	echo "use standard java /usr/bin/java"
#	/usr/bin/java -version
#	javacmd='/usr/bin/java'
#else
#	echo "No standard java engine found here: /usr/bin/java"
#	echo "If the streams java engine is used, the https connector does not work"
#	if [[ -z $noprompt ]]; then
#		if ! userPrompt; then
#			exit 2
#		fi
#	fi
#fi

cd "$commandPath"
ant
rm -f nohup.out
if [[ -z $foreground ]]; then
	echo "Starting  http test server"
	nohup "$javacmd" "-cp" "bin:opt/jetty-all-9.4.12.v20180830-uber.jar" "com.ibm.streamsx.inet.test.httptestserver.HTTPTestServer" 8097 1443 &> nohup1.out &
	echo -n "$!" > .pid1
	nohup "$javacmd" "-cp" "bin:opt/jetty-all-9.4.12.v20180830-uber.jar" "com.ibm.streamsx.inet.test.httptestserver.HTTPTestServer" 8098 1444 --clientCert &> nohup2.out &
	echo -n "$!" > .pid2
else
	if [[ -z $certrequest ]]; then
		echo "Starting  http test server 8097 1443"
		eval "$javacmd" "-cp" "bin:opt/jetty-all-9.4.12.v20180830-uber.jar" "com.ibm.streamsx.inet.test.httptestserver.HTTPTestServer" 8097 1443
	else
		echo "Starting  http test server 8098 1444 --clientCert"
		eval "$javacmd" "-cp" "bin:opt/jetty-all-9.4.12.v20180830-uber.jar" "com.ibm.streamsx.inet.test.httptestserver.HTTPTestServer" 8098 1444 --clientCert
	fi
fi
exit 0;

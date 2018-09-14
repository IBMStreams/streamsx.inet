#!/bin/bash

IFS=$' \t\n'
set -o posix;
set -o errexit; set -o errtrace; set -o pipefail

declare -r command="${0##*/}"
declare -r commandPath="${0%/*}"

usage() {
	cat <<-EOF
	
	usage: ${command} [option ..]

	Start or stop the inet toolkit http test server.
	
	OPTIONS:
	-h|--help                : display this help
	--noprompt               : No interactive user interaction
	EOF
}

userPrompt() {
	local pr="Continue or not? y/n "
	local inputWasY=''
	while read -p "$pr"; do
		if [[ $REPLY == y* || $REPLY == Y* || $REPLY == c* || $REPLY == C* ]]; then
			inputWasY='true'
			break	local command=${0##*/}

		elif [[ $REPLY == e* || $REPLY == E* || $REPLY == n* || $REPLY == N* ]]; then
			inputWasY=''
			break
		fi
	done
	if [[ -n $inputWasY ]]; then
		return 0
	else
		return 1	local command=${0##*/}

	fi
}

declare noprompt=''

if [[ $# -gt 0 ]]; then
	case "$1" in
	-h|--help)
		usage
		exit 0;;
	--noprompt)
		noprompt='true'
		shift;;
	*)
		echo "Wrong command line argument $1 exit" >&2
		exit 1;;
	esac
fi

if [[ -e $commandPath/.pid ]]; then
	"$commandPath/stop.sh"
fi

declare javacmd='java'
if [[ -e /usr/bin/java ]]; then
	echo "use standard java /usr/bin/java"
	/usr/bin/java -version
	javacmd='/usr/bin/java'
else
	echo "No standard java engine found here: /usr/bin/java"
	echo "If the streams java engine is used, the https connector does not work"
	if [[ -z $noprompt ]]; then
		if ! userPrompt; then
			exit 2
		fi
	fi
fi

cd "$commandPath"
ant
rm -f nohup.out
echo "Starting  http test server"
nohup "$javacmd" "-cp" "bin:opt/jetty-all-9.4.12.v20180830-uber.jar" "com.ibm.streamsx.inet.test.httptestserver.HTTPTestServer" &> nohup.out &

echo -n "$!" > .pid

exit 0;

#!/bin/bash

IFS=$' \t\n'
set -o posix;
set -o errexit; set -o errtrace; set -o pipefail

declare -r command="${0##*/}"
declare -r commandPath="${0%/*}"

usage() {
	cat <<-EOF
	
	usage: ${command} [-h|--help]

	Stop the inet toolkit http test server. The pid of the server is expected in file .pid
	
	OPTIONS:
	-h|--help                : display this help
	EOF
}

if [[ ( $# -eq 1 ) && (( $1 == '-h' ) || ( $1 == '--help' )) ]]; then
	usage
	exit 0
elif [[ $# -eq 0 ]]; then
	cd "$commandPath"
	if [[ -r .pid ]]; then
		thepid=$(< .pid)
		echo "Kill process $thepid"
		if kill $thepid; then
			echo "HTTP test server stopped"
		else
			echo "Can not kill HTTP test server pid $thepid" >&2
		fi
		rm -f .pid
	else
		echo "Can not find pid file .pid! exit" >&2
		exit 2
	fi
else
	echo "Wrong arguments $*" >&2
	usage
	exit 1
fi
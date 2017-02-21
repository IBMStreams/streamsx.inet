#!/bin/bash

verbose=0

while getopts ":v" opt; do
	case $opt in
	v)
		verbose=1
		;;
	\?)
		printError "Wrong option $OPTARG"
		exit 1
		;;
	esac
done

set -o nounset;
shopt -s globstar

function printVerbose() {
	if [ $verbose ]; then
		echo "$1"
	fi
}

function printError() {
  echo "ERROR ERROR: $1"
}

languages=( de_DE fr_FR it_IT es_ES pt_BR ja_JP zh_CN ru_RU zh_TW ko_KR en_US )

startDirectory=$(pwd)
datestring=$(date +%Y%m%d-%H%M%S)
logdir="logs/${datestring}"
mkdir -p "$logdir"

#global preparation
declare -i runs=0
declare -i errors=0
declare -i failures=0


for testdir in *; do
	if [ -d "${testdir}" ] && [ -e "${testdir}/testProperties" ]; then
		testcase="${testdir##*/}"
		echo "**********************************************************************************************"
		echo "**********************************************************************************************"
		echo "Make test in testdir ${testdir} Testcase $testcase"
		echo "**********************************************************************************************"
		echo "**********************************************************************************************"
		printVerbose "testProperties found"
		
		#case preparation
		runs=$((runs+1))
		source "${testdir}/testProperties"
		
		for ((variant=0; variant<variants; variant++)); do
		  echo "**********************************************************************************************"
		  echo "Make test in dir ${testdir} variant=$variant"
		  echo "**********************************************************************************************"

		  #variant preparation
		  cd "${testdir}"

		  #prepare spl files
		  for splfile in **/*.spltpl; do
			dest="${splfile%%spltpl}spl"
			printVerbose "Convert spl template $splfile to $dest"
				sed -e "s/\/\/_${variant}//g" "$splfile" > "$dest"
			done
			
			#test case preparation
			case $typeOfCase in
			runStandalone)
				printVerbose "prepare runStandalone"
				make all
				result=$?
				if [ ! $result ]; then
					errors=$((errors+1))
					echo "Error in ${testdir} ${testcase} ${variant} make returns $result"
				fi
				;;
				
			esac;
			
			for i in "${languages[@]}"; do
				logname="${testdir}_${variant}-${i}"
				logfile="../${logdir}/${logname}.log"
				logtemp="../${logdir}/tmp.log"
				echo "** ${logname} ***************************************************"
				
				export LC_ALL=$i.UTF-8

				case $typeOfCase in
				compile)
					printVerbose "compile"
					if make all &> "${logtemp}"; then
					  if [ "$expectFailure" == 1 ]; then
						errors=$((errors+1))
						printError "Failure expected but make went fine "
					  fi
					else
						if [ "$expectFailure" == 0 ]; then
						  errors=$((errors+1))
						  printError "No failure expected but make went wrong"
						fi
					fi
					;;

				runStandalone)
					printVerbose "runStandalone"
					cmd="output/bin/standalone -t 5 url=http://www.snafu.de"
					echo "execute $cmd"
					#if output/bin/standalone -l 2 &> "${logtemp}"; then
					if $cmd &> "${logtemp}"; then
					if [ "$expectFailure" == 1 ]; then
							printError "Failure expected but standalone went fine "
							errors=$((errors+1))
						fi
					else
						if ! [ "$expectFailure" == 0 ]; then
							printError "No failure expected but standalone went wrong"
							errors=$((errors+1))
						fi
					fi
					;;
				*)
					printError "Wrong typeOfCase $typeOfCase"

				esac;
				echo "*****"
				grep CDIS "${logtemp}" &> "$logfile"
				cat "$logfile"
			done
			
			cd "$startDirectory"
			
		done
	fi
done

cd "$startDirectory"

echo "****************************************************************"
echo "Runs=$runs Errors=$errors Failures=$failures"
echo "****************************************************************"

# Distributed and standalone Run test for FTP

#--variantList='distributed standalone'
##--variantList:=distributed

setCategory 'quick'

PREPS='copyOnly splCompile'
STEPS='mySubmit myWaitForCompletion myCancelJob myEvaluate'

function mySubmit {
	if [[ $TTRO_variantCase == "standalone" ]]; then
		if echoAndExecute output/bin/standalone; then
			return 0
		else
			setFailure "Could not start standalone job"
		fi
	else
		if submitJob; then
			declare -p TTTT_jobno
			return 0
		else
			setFailure "Could not start distributed job"
		fi
	fi
}

function myWaitForCompletion {
	if [[ $TTRO_variantCase == "distributed" ]]; then
		until [[ -e data/BINDATA_FINAL ]]; do
			echo "wait for completion of job $TTTT_jobno"
			sleep 1
		done
		echo "Job $TTTT_jobno completed"
	fi
}

function myCancelJob {
	if [[ $TTRO_variantCase == "distributed" ]]; then
		cancelJob
	fi
}

function myEvaluate {
	local tmp=$(wc -c data/BINDATA_.txt | cut -d " " -f1)
	echo "Result has $tmp bytes"
	if [[ $tmp -gt 2000000 ]]; then
		return 0
	else
		setFailure "Result not expected"
	fi
}

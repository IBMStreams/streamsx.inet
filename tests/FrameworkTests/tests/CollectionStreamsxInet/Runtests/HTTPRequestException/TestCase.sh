#--variantCount=6

setCategory 'quick'

function myExplain {
	case "$TTRO_variantCase" in
	0)  echo "variant $TTRO_variantCase - PUT  method url httpx://httpbin.org/get outputBody";;
	1)  echo "variant $TTRO_variantCase - GET  method url httpx://httpbin.org/get outputDataLine";;
	2)  echo "variant $TTRO_variantCase - HEAD method url http://httpbin.orgx/get outputBody";;
	3)  echo "variant $TTRO_variantCase - POST  method url http://httpbin.orgx/get outputDataLine";;
	4)  echo "variant $TTRO_variantCase - get  method url http://httpbin.org/get outputBody";;
	5)  echo "variant $TTRO_variantCase - get  method url http://httpbin.org/get outputDataLine";;
	*) printErrorAndExit "invalid variant $TTRO_variantCase" $errRt;;
	esac
}

PREPS='myExplain copyAndTransformSpl'

STEPS=(
	'splCompile'
	'submitJob'
	'checkJobNo'
	'waitForFinAndHealth'
	'myEval'
	'myEval2'
)

FINS='cancelJob'

function myEval {
	case "$TTRO_variantCase" in
	0|1)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" '*status=""*' "*stat=-1*" "*ClientProtocolException*" '*respData=""*';;
	2|3)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" '*status=""*' "*stat=-1*" "*UnknownHostException*" '*respData=""*';;
	4|5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" '*status=""*' "*stat=-1*" "*IllegalArgumentException*" '*respData=""*';;
	esac
}

function myEval2 {
	getLineCount "$TT_dataDir/Tuples"
	if [[ $TTTT_lineCount -ne 1 ]]; then
		setFailure "Invalid line count $TTTT_lineCount"
	fi
}

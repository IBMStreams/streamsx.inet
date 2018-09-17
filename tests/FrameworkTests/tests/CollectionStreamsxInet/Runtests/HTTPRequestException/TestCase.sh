#--variantCount=6

setCategory 'quick'

function myExplain {
	case "$TTRO_variantCase" in
	0)
		myUrl="httpx://${TTPR_httpServerAddr}/get"
		echo "variant $TTRO_variantCase - PUT  method url $myUrl outputBody";;
	1)
		myUrl="httpx://${TTPR_httpServerAddr}/get"
		echo "variant $TTRO_variantCase - GET  method url $myUrl outputDataLine";;
	2)
		myUrl="http://xx${TTPR_httpServerAddr}/get"
		echo "variant $TTRO_variantCase - HEAD method url $myUrl outputBody";;
	3)
		myUrl="http://xx${TTPR_httpServerAddr}/get"
		echo "variant $TTRO_variantCase - POST  method url $myUrl outputDataLine";;
	4)
		myUrl="http://${TTPR_httpServerAddr}/get"
		echo "variant $TTRO_variantCase - get  method url $myUrl outputBody";;
	5)
		myUrl="http://${TTPR_httpServerAddr}/get"
		echo "variant $TTRO_variantCase - get  method url $myUrl outputDataLine";;
	*)
		printErrorAndExit "invalid variant $TTRO_variantCase" $errRt;;
	esac
}

PREPS='myExplain copyAndMorphSpl'

STEPS=(
	'splCompile url=$myUrl'
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
		if linewisePatternMatch "$TT_dataDir/Tuples" "true" "*id=0*" '*status=""*' "*stat=-1*" "*ClientProtocolException*" '*respData=""*'; then
			return 0
		elif linewisePatternMatch "$TT_dataDir/Tuples" "true" "*id=0*" '*status=""*' "*stat=-1*" "*UnsupportedSchemeException*" '*respData=""*'; then
			return 0
		else
			setFailure "None of the above matches"
		fi;;
		
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

#--variantCount=16

function myExplain {
	case "$TTRO_variantCase" in
	0)  echo "variant $TTRO_variantCase - GET  method url http://httpbin.org/getx outputBody";;
	1)  echo "variant $TTRO_variantCase - GET  method url http://httpbin.org/getx outputDataLine";;
	2)  echo "variant $TTRO_variantCase - GET  method url httpx://httpbin.org/status/418 outputBody";;
	3)  echo "variant $TTRO_variantCase - GET  method url httpx://httpbin.org/status/418 outputDataLine";;
	4)  echo "variant $TTRO_variantCase - HEAD method url http://httpbin.org/getx outputBody";;
	5)  echo "variant $TTRO_variantCase - HEAD method url http://httpbin.org/getx outputDataLine";;
	6)  echo "variant $TTRO_variantCase - HEAD method url httpx://httpbin.org/status/418 outputBody";;
	7)  echo "variant $TTRO_variantCase - HEAD method url httpx://httpbin.org/status/418 outputDataLine";;
	8)  echo "variant $TTRO_variantCase - POST method url http://httpbin.org/getx outputBody";;
	9)  echo "variant $TTRO_variantCase - POST method url http://httpbin.org/getx outputDataLine";;
	10) echo "variant $TTRO_variantCase - POST method url httpx://httpbin.org/status/418 outputBody";;
	11) echo "variant $TTRO_variantCase - POST method url httpx://httpbin.org/status/418 outputDataLine";;
	12)  echo "variant $TTRO_variantCase - PUT  method url http://httpbin.org/getx outputBody";;
	13)  echo "variant $TTRO_variantCase - PUT  method url http://httpbin.org/getx outputDataLine";;
	14) echo "variant $TTRO_variantCase - PUT  method url httpx://httpbin.org/status/418 outputBody";;
	15) echo "variant $TTRO_variantCase - PUT  method url httpx://httpbin.org/status/418 outputDataLine";;
	esac
}

PREPS='myExplain copyAndTransformSpl'

STEPS=(
	'splCompile'
	'submitJob'
	'checkJobNo'
	'waitForFin'
	'myEval'
)

FINS='cancelJob'

function myEval {
	case "$TTRO_variantCase" in
	0|1)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=404*" "*404 Not Found*";;
	2|3)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=418*" "*teapot*";;
	4|5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=404*" '*respData=""*';;
	6|7)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=418*" '*respData=""*';;
	8|9)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=404*" "*404 Not Found*";;
	10|11)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=418*" "*teapot*";;
	12|13)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=404*" "*404 Not Found*";;
	14|15)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=418*" "*teapot*";;
	*)
		PrintErrorAndExit "Wrong variant $TTRO_variantCase" $errRt
	esac
	
	case "$TTRO_variantCase" in
	0|2|4|6|8|10|12|14)
		# cases with outputBody must not contain more than one output tuple
		getLineCount "$TT_dataDir/Tuples"
		if [[ $TTTT_lineCount -ne 1 ]]; then
			setFailure "Invalid line count $TTTT_lineCount"
		fi;;
	1|3|9|11|13|15)
		# cases with outputDataLine must not contain empty respData except in case of HEAD method
		linewisePatternMatchInterceptAndError "$TT_dataDir/Tuples" "" '*respData=""*';;
	5|7)
		echo "empty test in case of HEAD method";;
	esac
}

#--variantCount=12

setSkip 'Proxy may change the result'

function myExplain {
	case "$TTRO_variantCase" in
	0)  echo "variant $TTRO_variantCase - GET  method url http://httpbin.org/redirect-to?url=http://httpbin.org/get&status_code=307";;
	1)  echo "variant $TTRO_variantCase - HEAD method url http://httpbin.org/redirect-to?url=http://httpbin.org/get&status_code=307";;
	2)  echo "variant $TTRO_variantCase - POST method url http://httpbin.org/redirect-to?url=http://httpbin.org/post&status_code=307";;
	3)  echo "variant $TTRO_variantCase - PUT  method url http://httpbin.org/redirect-to?url=http://httpbin.org/put&status_code=307";;
	4)  echo "variant $TTRO_variantCase - GET  method url http://httpbin.org/redirect-to?url=http://httpbin.org/get&status_code=307  RELAXED";;
	5)  echo "variant $TTRO_variantCase - HEAD method url http://httpbin.org/redirect-to?url=http://httpbin.org/get&status_code=307  RELAXED";;
	6)  echo "variant $TTRO_variantCase - POST method url http://httpbin.org/redirect-to?url=http://httpbin.org/post&status_code=307 RELAXED";;
	7)  echo "variant $TTRO_variantCase - PUT  method url http://httpbin.org/redirect-to?url=http://httpbin.org/put&status_code=307  RELAXED";;
	8)  echo "variant $TTRO_variantCase - GET  method url http://httpbin.org/redirect-to?url=http://httpbin.org/get&status_code=307  NONE";;
	9)  echo "variant $TTRO_variantCase - HEAD method url http://httpbin.org/redirect-to?url=http://httpbin.org/get&status_code=307  NONE";;
	10) echo "variant $TTRO_variantCase - POST method url http://httpbin.org/redirect-to?url=http://httpbin.org/post&status_code=307 NONE";;
	11) echo "variant $TTRO_variantCase - PUT  method url http://httpbin.org/redirect-to?url=http://httpbin.org/put&status_code=307  NONE";;
	*) printErrorAndExit "invalid variant $TTRO_variantCase" $errRt;;
	esac
}

PREPS='myExplain copyAndMorphSpl'

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
	0|4|6)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*Host\\": \\"httpbin.org*' '*err=""*';;
	1|5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*respData=""*' '*err=""*';;
	2|3|7|8|9|10|11)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=307*" '*respData=""*' '*err=""*';;
	esac
}

function myEval2 {
	getLineCount "$TT_dataDir/Tuples"
	if [[ $TTTT_lineCount -ne 1 ]]; then
		setFailure "Invalid line count $TTTT_lineCount"
	fi
}

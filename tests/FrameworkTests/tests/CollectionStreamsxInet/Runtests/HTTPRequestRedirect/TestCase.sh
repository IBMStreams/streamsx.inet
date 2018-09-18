#--variantCount=12

function myExplain {
	case "$TTRO_variantCase" in
	0)  echo "variant $TTRO_variantCase - GET  method DEFAULT redirectStrategy : yes url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/get&status_code=307";;
	1)  echo "variant $TTRO_variantCase - HEAD method DEFAULT redirectStrategy : yes url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/head&status_code=307";;
	2)  echo "variant $TTRO_variantCase - POST method DEFAULT redirectStrategy : no  url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/post&status_code=307";;
	3)  echo "variant $TTRO_variantCase - PUT  method DEFAULT redirectStrategy : no  url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/put&status_code=307";;
	4)  echo "variant $TTRO_variantCase - GET  method RELAXED redirectStrategy : yes url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/get&status_code=307";;
	5)  echo "variant $TTRO_variantCase - HEAD method RELAXED redirectStrategy : yes url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/head&status_code=307";;
	6)  echo "variant $TTRO_variantCase - POST method RELAXED redirectStrategy : yes url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/post&status_code=307";;
	7)  echo "variant $TTRO_variantCase - PUT  method RELAXED redirectStrategy : no  url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/put&status_code=307";;
	8)  echo "variant $TTRO_variantCase - GET  method NONE redirectStrategy    : no  url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/get&status_code=307";;
	9)  echo "variant $TTRO_variantCase - HEAD method NONE redirectStrategy    : no  url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/head&status_code=307";;
	10) echo "variant $TTRO_variantCase - POST method NONE redirectStrategy    : no  url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/post&status_code=307";;
	11) echo "variant $TTRO_variantCase - PUT  method NONE redirectStrategy    : no  url http://$TTPR_httpServerAddr/redirect-to?url=http://$TTPR_httpServerAddr/put&status_code=307";;
	*) printErrorAndExit "invalid variant $TTRO_variantCase" $errRt;;
	esac
}

PREPS='myExplain copyAndMorphSpl'

STEPS=(
	"splCompile host=$TTPR_httpServerAddr"
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
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" "*Host: $TTPR_httpServerAddr*" '*err=""*';;
	1|5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*respData=""*' '*err=""*';;
	2|3|7|8|9|10|11)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=307*" '*err=""*';;
	esac
}

function myEval2 {
	getLineCount "$TT_dataDir/Tuples"
	if [[ $TTTT_lineCount -ne 1 ]]; then
		setFailure "Invalid line count $TTTT_lineCount"
	fi
}

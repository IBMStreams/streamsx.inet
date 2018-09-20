#--variantCount=12

function myExplain {
	echo "Cases that return a response"
	case "$TTRO_variantCase" in
	0) echo "variant $TTRO_variantCase - GET fixed method";;
	1) echo "variant $TTRO_variantCase - GET dynamic method";;
	2) echo "variant $TTRO_variantCase - GET fixed method redirect ";;
	3) echo "variant $TTRO_variantCase - GET dynamic method redirect ";;
	4) echo "variant $TTRO_variantCase - POST fixed method";;
	5) echo "variant $TTRO_variantCase - POST dynamic method";;
	6) echo "variant $TTRO_variantCase - PUT fixed method";;
	7) echo "variant $TTRO_variantCase - PUT dynamic method";;
	8) echo "variant $TTRO_variantCase - DELETE fixed method";;
	9) echo "variant $TTRO_variantCase - DELETE dynamic method";;
	10) echo "variant $TTRO_variantCase - NONE fixed method";;
	11) echo "variant $TTRO_variantCase - NONE dynamic method";;
	esac
}

declare -a urlList=( "http://$TTPR_httpServerAddr/getx" "http://$TTPR_httpServerAddr/redirectx/3" "http://$TTPR_httpServerAddr/postx" "http://$TTPR_httpServerAddr/putx" "http://$TTPR_httpServerAddr/deletex" 'x' )

PREPS=(
	'myExplain'
	'copyAndMorphSpl'
	'mkdir -p "$TT_dataDir"'
)

STEPS=(
	'splCompile'
	'urlIndex=$(($TTRO_variantCase / 2))'
	'executeLogAndSuccess output/bin/standalone -t 2 url="${urlList[$urlIndex]}"'
	'linewisePatternMatchInterceptAndError "$TT_evaluationFile" "" "*ERROR*"'
	'myEval2'
)

function myEval2 {
	case "$TTRO_variantCase" in
	10|11)
		return 0;;
	*)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*WARN*HTTPRequestOper*status=HTTP*404*" "*WARN*HTTPRequestOper*status=HTTP*405*";;
	esac
}

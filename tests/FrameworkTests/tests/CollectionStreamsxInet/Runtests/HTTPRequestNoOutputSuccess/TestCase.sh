#--variantCount=12

setCategory 'quick'

function myExplain {
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

declare -a urlList=(
	"http://$TTPR_httpServerAddr/get"
	"http://$TTPR_httpServerAddr/redirect/3"
	"http://$TTPR_httpServerAddr/post"
	"http://$TTPR_httpServerAddr/put"
	"http://$TTPR_httpServerAddr/delete"
	''
)

PREPS=(
	'myExplain'
	'copyAndMorphSpl'
	'mkdir -p "$TT_dataDir"'
)

STEPS=(
	'splCompile'
	'urlIndex=$(($TTRO_variantCase / 2))'
	'executeLogAndSuccess output/bin/standalone -t 5 url="${urlList[$urlIndex]}"'
	'linewisePatternMatchInterceptAndError "$TT_evaluationFile" "" "*ERROR*"'
	'myEval2'
)

function myEval2 {
	if [[ ( $TTRO_variantCase -eq 10 ) || ( $TTRO_variantCase -eq 11 ) ]]; then
		return 0
	else
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*HTTPRequestOper*status=HTTP*200 OK"
	fi
}

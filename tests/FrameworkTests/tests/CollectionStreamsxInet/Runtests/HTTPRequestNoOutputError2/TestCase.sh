#--variantCount=12

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

declare -a urlList=( 'httpx://httpbin.org/get' 'httphttpbin.org/redirect/3' 'httpx://httpbin.org/post' 'httpx://httpbin.org/put' 'httxp://httpbin.org/delete' '' )

PREPS=(
	'myExplain'
	'copyAndTransformSpl'
	'mkdir -p "$TT_dataDir"'
)

STEPS=(
	'splCompile'
	'urlIndex=$(($TTRO_variantCase / 2))'
	'executeLogAndSuccess output/bin/standalone -t 2 url="${urlList[$urlIndex]}"'
	'myEval2'
)

function myEval2 {
	case "$TTRO_variantCase" in
	10|11)
		linewisePatternMatchInterceptAndError "$TT_evaluationFile" "" "*ERROR*";;
	*)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*ERROR*HTTPRequestOper*ClientProtocolException*";;
	esac
}
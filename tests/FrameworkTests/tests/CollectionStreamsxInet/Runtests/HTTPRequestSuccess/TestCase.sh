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

PREPS='myExplain copyAndMorphSpl'

STEPS=(
	"splCompile host=$TTPR_httpServerAddr"
	'submitJob'
	'checkJobNo'
	'waitForFinAndHealth'
	'myEval'
)

FINS='cancelJob'

function myEval {
	case "$TTRO_variantCase" in
	0)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*method: GET*scheme: http*uri: /get*';;
	1)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*method="GET"*stat=200*method: GET*scheme: http*uri: /get*';;
	2)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*method: GET*scheme: http*uri: /get*';;
	3)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*method="GET"*stat=200*method: GET*scheme: http*uri: /get*';;
	4)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*method: POST*scheme: http*uri: /post*<h2>body</h2><p>My post data*';;
	5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*method="POST"*stat=200*method: POST*scheme: http*uri: /post*<h2>body</h2><p>My post data*';;
	6)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*method: PUT*scheme: http*uri: /put*<h2>body</h2><p>My put data*';;
	7)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*method="PUT"*stat=200*method: PUT*scheme: http*uri: /put*<h2>body</h2><p>My put data*';;
	8)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*method: DELETE*scheme: http*uri: /delete*';;
	9)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*method="DELETE"*stat=200*method: DELETE*scheme: http*uri: /delete*';;
	10)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*';;
	11)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*method="NONE"*';;
	esac
}

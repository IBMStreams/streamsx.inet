#--variantCount=4

setCategory 'quick'

function myExplain {
	case "$TTRO_variantCase" in
	0) echo "variant $TTRO_variantCase - HTTPRequest GET fixed method hello";;
	1) echo "variant $TTRO_variantCase - HTTPRequest GET fixed method hello2 - Document is without final newline";;
	2) echo "variant $TTRO_variantCase - InetSource GET fixed method hello";;
	3) echo "variant $TTRO_variantCase - InetSource GET fixed method hello2 - Document is without final newline";;
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

FINS='cancelJobAndLog'

function myEval {
	case "$TTRO_variantCase" in
	0)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*method: GET*scheme: http*uri: /hello*</body>\\n</html>\\n"*';;
	1)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*method: GET*scheme: http*uri: /hello*</body>\\n</html>"*';;
	2)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*<h1>Hello from HelloServlet</h1>*' '*respData="</body>"*' '*respData="</html>"*';;
	3)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*<h1>Hello from HelloServlet2</h1>*' '*respData="</body>"*' '*respData="</html>"*';;
	esac
}

#--variantCount=6

if [[ ( $TTRO_variantCase == 2 ) || ( $TTRO_variantCase == 5 ) ]]; then
	setSkip 'Jetty HTTP test server has no deflate implementation'
fi

function myExplain {
	case "$TTRO_variantCase" in
	0) echo "variant $TTRO_variantCase - GET uncompressed";;
	1) echo "variant $TTRO_variantCase - GET gzip";;
	2) echo "variant $TTRO_variantCase - GET deflate";;
	3) echo "variant $TTRO_variantCase - GET uncompressed disableContentCompression";;
	4) echo "variant $TTRO_variantCase - GET gzip         disableContentCompression";;
	5) echo "variant $TTRO_variantCase - GET deflate      disableContentCompression";;
	*) printErrorAndExit "Wrong variant $TTRO_variantCase" $errRt
	esac
}

PREPS='myExplain copyAndMorphSpl'

STEPS=(
	"splCompile host=$TTPR_httpServerAddr"
	'submitJob'
	'checkJobNo'
	'waitForFinAndHealth'
	'cancelJob'
	'myEval'
)

FINS='cancelJob'

function myEval {
	case "$TTRO_variantCase" in
	0|1|2)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*Hello from HelloServlet*' '*Accept-Encoding: gzip,deflate*';;
	3|4|5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*Hello from HelloServlet*'
		linewisePatternMatchInterceptAndError   "$TT_dataDir/Tuples" "" '*Accept-Encoding: gzip,deflate*';;
	esac
}

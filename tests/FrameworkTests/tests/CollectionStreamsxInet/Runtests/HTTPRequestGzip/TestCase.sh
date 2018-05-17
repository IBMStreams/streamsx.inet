#--variantCount=6

function myExplain {
	case "$TTRO_variantCase" in
	0) echo "variant $TTRO_variantCase - GET uncompressed";;
	1) echo "variant $TTRO_variantCase - GET gzip";;
	2) echo "variant $TTRO_variantCase - GET deflate";;
	3) echo "variant $TTRO_variantCase - GET uncompressed disableContentCompression";;
	4) echo "variant $TTRO_variantCase - GET gzip disableContentCompression";;
	5) echo "variant $TTRO_variantCase - GET deflate disableContentCompression";;
	*) printErrorAndExit "Wrong variant $TTRO_variantCase" $errRt
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
	0|1|2)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*\\"Host\\":\\"httpbin.org\\"*' '*\\"Accept-Encoding\\":\\"gzip,deflate\\"*';;
	3)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*\\"Host\\":\\"httpbin.org\\"*'
		linewisePatternMatchInterceptAndError   "$TT_dataDir/Tuples" "" '*\\"Accept-Encoding\\":\\"gzip,deflate\\"*';;
	4|5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*respData="*"*';;
	esac
}

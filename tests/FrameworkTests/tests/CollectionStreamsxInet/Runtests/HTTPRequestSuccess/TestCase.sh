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
	'splCompile'
	'submitJob'
	'checkJobNo'
	'waitForFinAndHealth'
	'myEval'
)

FINS='cancelJob'

function myEval {
	case "$TTRO_variantCase" in
	0|1)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*\\"Host\\": \\"httpbin.org\\"*';;
	2|3)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*\\"Host\\": \\"httpbin.org\\"*';;
	4|5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*\\"Host\\": \\"httpbin.org\\"*' '*\\"data\\": \\"My post data\\"*';;
	6|7)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*\\"Host\\": \\"httpbin.org\\"*' '*\\"data\\": \\"My put data\\"*';;
	8|9)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*" "*stat=200*" '*\\"Host\\": \\"httpbin.org\\"*';;
	10|11)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0*";;
	esac
}

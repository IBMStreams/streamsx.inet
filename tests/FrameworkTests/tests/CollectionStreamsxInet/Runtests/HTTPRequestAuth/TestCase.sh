#--variantCount=8

function myExplain {
	case "$TTRO_variantCase" in
	0) echo "variant $TTRO_variantCase - GET basic auth - no auth file and prop";;
	1) echo "variant $TTRO_variantCase - GET basic auth - with auth file and no prop";;
	2) echo "variant $TTRO_variantCase - GET basic auth - with auth file and overwriting props";;
	3) echo "variant $TTRO_variantCase - GET basic auth - with with props and authenticationType: STANDARD";;
	4) echo "variant $TTRO_variantCase - GET oauth1 - with authenticationType: OAUTH1 and file";;
	5) echo "variant $TTRO_variantCase - GET oauth1 - with authenticationType: OAUTH1 and props";;
	6) echo "variant $TTRO_variantCase - GET oauth2 - with authenticationType: OAUTH2 and file";;
	7) echo "variant $TTRO_variantCase - GET oauth2 - with authenticationType: OAUTH2 and props";;
	*) printErrorAndExit "Wrong variant $TTRO_variantCase" $errRt
	esac
}

PREPS='myExplain copyAndTransformSpl'

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
	0)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "" "*id=0,stat=401*";;
	1|2|3)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "" "*id=0,stat=200*";;
	4|5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0,stat=200*" '*oauth_token=\\\\\\"zzzz\\\\\\"*' '*oauth_consumer_key=\\\\\\"xxxx\\\\\\"*';;
	6)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0,stat=200*" '*\\"Authorization\\":\\"Bearer zzzz\\"*';;
	7)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0,stat=200*" '*\\"Authorization\\":\\"Bearer Propzzzz\\"*';;
	esac
}

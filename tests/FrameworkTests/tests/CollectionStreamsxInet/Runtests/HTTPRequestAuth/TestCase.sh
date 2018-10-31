#--variantCount=16

myExplain() {
	case "$TTRO_variantCase" in
	0) echo "variant $TTRO_variantCase  - GET  basic auth - no auth file and prop";;
	1) echo "variant $TTRO_variantCase  - GET  basic auth - with auth file and no prop";;
	2) echo "variant $TTRO_variantCase  - GET  basic auth - with auth file and overwriting props";;
	3) echo "variant $TTRO_variantCase  - GET  basic auth - with with props and authenticationType: STANDARD";;
	4) echo "variant $TTRO_variantCase  - GET  oauth1 - with authenticationType: OAUTH1 and file";;
	5) echo "variant $TTRO_variantCase  - GET  oauth1 - with authenticationType: OAUTH1 and props";;
	6) echo "variant $TTRO_variantCase  - GET  oauth2 - with authenticationType: OAUTH2 and file";;
	7) echo "variant $TTRO_variantCase  - POST oauth2 - with authenticationType: OAUTH2 and props";;
	8) echo "variant $TTRO_variantCase  - GET  oauth2 - with authenticationType: OAUTH2 and file and accessToken attribute";;
	9) echo "variant $TTRO_variantCase  - POST oauth2 - with authenticationType: OAUTH2 and props and accessToken attribute";;
	10) echo "variant $TTRO_variantCase - GET  oauth2 - with authenticationType: OAUTH2 and file, accessToken attribute and tokenType attribute";;
	11) echo "variant $TTRO_variantCase - POST oauth2 - with authenticationType: OAUTH2 and props, accessToken attribute and tokenType attribute";;
	12) echo "variant $TTRO_variantCase  - POST basic auth - no auth file and prop";;
	13) echo "variant $TTRO_variantCase  - POST basic auth - with auth file and no prop";;
	14) echo "variant $TTRO_variantCase  - POST basic auth - with auth file and overwriting props";;
	15) echo "variant $TTRO_variantCase  - POST basic auth - with with props and authenticationType: STANDARD";;
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
	'evalRequest'
	'evalBody'
)

FINS='cancelJob'

evalRequest() {
	case "$TTRO_variantCase" in
	0)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "" '*id=0,stat=401,method="GET"*';;
	1)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "" '*id=0,stat=200,method="GET"*' "*user: user1*";;
	2|3)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "" '*id=0,stat=200,method="GET"*' "*user: user2*";;
	12)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "" '*id=0,stat=401,method="POST"*';;
	13)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "" '*id=0,stat=200,method="POST"*' "*user: user1*";;
	14|15)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "" '*id=0,stat=200,method="POST"*' "*user: user2*";;
	4|5)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" "*id=0,stat=200*" '*oauth_token=\\"zzzz\\"*' '*oauth_consumer_key=\\"xxxx\\"*';;
	6)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,stat=200,method="GET"*' '*Authorization: Bearer zzzz*';;
	7)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,stat=200,method="POST"*' '*Authorization: Bearer Propzzzz*';;
	8)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,stat=200,method="GET"*' '*Authorization: Bearer1 tokenFromAttr*';;
	9)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,stat=200,method="POST"*' '*Authorization: Bearer1 tokenFromAttr*';;
	10)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,stat=200,method="GET"*' '*Authorization: typevariable tokenFromAttr*';;
	11)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,stat=200,method="POST"*' '*Authorization: typevariable tokenFromAttr*';;
	esac
}

evalBody() {
	case "$TTRO_variantCase" in
	7|13|14|15)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*\\"tok\\":\\"tokenFromAttr\\"*' '*\\"id\\":0*' '*\\"tktype\\":\\"typevariable\\"*';;
	9)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*\\"id\\":0*' '*\\"tktype\\":\\"typevariable\\"*'
		linewisePatternMatchInterceptAndError "$TT_dataDir/Tuples" "" '*\\"tok\\":\\"tokenFromAttr\\"*';;
	11)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*\\"id\\":0*'
		linewisePatternMatchInterceptAndError "$TT_dataDir/Tuples" "" '*\\"tok\\":\\"tokenFromAttr\\"*' '*\\"tktype\\":\\"typevariable\\"*';;
	*)
	 :;;
	esac
}
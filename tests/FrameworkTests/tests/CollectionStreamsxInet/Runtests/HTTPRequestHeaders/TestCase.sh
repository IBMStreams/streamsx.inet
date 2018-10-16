#--variantList='none \
#--	headersNoAttribute headersEmptyAttribute headersAndAttribute \
#--	noHeadersAndAttribute noHeadersEmptyAttribute \
#--	invalid'

myExplain() {
	case "$TTRO_variantCase" in
	none)                  echo "variant $TTRO_variantCase - No Extra headers";;
	headersNoAttribute)    echo "variant $TTRO_variantCase - ExtraHeaders but no extraHeaderAttribute parameter";;
	headersEmptyAttribute) echo "variant $TTRO_variantCase - ExtraHeaders and empty extraHeaderAttribute parameter";;
	headersAndAttribute)   echo "variant $TTRO_variantCase - ExtraHeaders and extraHeaderAttribute parameter";;
	noHeadersAndAttribute) echo "variant $TTRO_variantCase - No extraHeaders and extraHeaderAttribute parameter";;
	noHeadersEmptyAttribute)echo "variant $TTRO_variantCase - No extraHeaders and empty extraHeaderAttribute parameter";;
	invalid)               echo "variant $TTRO_variantCase - ExtraHeaders and invalid header string in extraHeaderAttribute parameter";;
	*) printErrorAndExit "Wrong variant" $errRt;
	esac
}

PREPS='myExplain copyAndMorphSpl'

STEPS=(
	'TT_mainComposite=HTTPRequestExtraHeaders'
	"splCompile host=$TTPR_httpServerAddr"
	'TT_sabFile=./output/HTTPRequestExtraHeaders.sab'
	'submitJob'
	'checkJobNo'
	'waitForFinAndHealth'
	'cancelJob'
	'myEval'
	'myEval2'
)

FINS='cancelJob'

myEval() {
	case "$TTRO_variantCase" in
	none|noHeadersEmptyAttribute)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,method="GET",status="HTTP/1.1 200 OK",stat=200,respData*,err=""*';;
	extraHeaderAttribute|headersEmptyAttribute)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,method="GET",status="HTTP/1.1 200 OK",stat=200,respData*,err=""*'
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*header-stat1: bla<br>*'
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*header-stat2: blabla<br>*';;
	headersAndAttribute)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,method="GET",status="HTTP/1.1 200 OK",stat=200,respData*,err=""*'
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*header-stat1: bla<br>*'
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*header-stat2: blabla<br>*'
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*header-dyn: avalue<br>*';;
	noHeadersAndAttribute)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,method="GET",status="HTTP/1.1 200 OK",stat=200,respData*,err=""*'
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0*stat=200*header-dyn: avalue<br>*';;
	invalid)
		linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*id=0,method="GET",status="",stat=-1*'
	esac
}

myEval2() {
	case "$TTRO_variantCase" in
	none|noHeadersEmptyAttribute)
		linewisePatternMatchInterceptAndError "$TT_dataDir/Tuples" "" '*header-stat2: blabla<br>*' '*header-stat1: bla<br>*' '*header-dyn: avalue<br>*';;
	extraHeaderNoAttribute|headersEmptyAttribute)
		linewisePatternMatchInterceptAndError "$TT_dataDir/Tuples" "" '*header-dyn: avalue<br>*';;
	headersAndAttribute)
		:;;
	noHeadersAndAttribute)
		linewisePatternMatchInterceptAndError "$TT_dataDir/Tuples" "" '*header-stat2: blabla<br>*' '*header-stat1: bla<br>*';;
	invalid)
		:;;
	esac
}

#Translation

PREPS=(
	'copyOnly'
	'TT_mainComposite=com.ibm.streamsx.inet.http.sample::HTTPStreamSample'
)
                         
STEPS=(
	'splCompile'
	'executeLogAndSuccess output/bin/standalone -t 3'
	'echo "The result is $TTTT_result"'
	'linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "$codes"'
)

codes='*CDIST0241I*'

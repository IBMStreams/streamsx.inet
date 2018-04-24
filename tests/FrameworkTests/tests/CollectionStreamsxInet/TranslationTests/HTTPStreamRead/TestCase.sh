#Translation

PREPS=(
	'copyOnly'
	'TT_mainComposite=com.ibm.streamsx.inet.http.sample::HTTPStreamSample'
)
                         
STEPS=(
	'compile'
	'echoExecuteInterceptAndSuccess output/bin/standalone -t 3 2>&1 | tee "$TT_evaluationFile"'
	'linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "$codes"'
)

codes='*CDIST0241I*'

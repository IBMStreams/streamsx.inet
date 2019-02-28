setCategory 'quick'

PREPS='copyOnly'

STEPS=(
	"setVar 'TT_mainComposite' 'com.ibm.streamsx.inet.http.tests::URLEncodeTestMain'"
	"splCompile"
	"setVar 'TT_sabFile' 'output/com.ibm.streamsx.inet.http.tests.URLEncodeTestMain.sab'"
	'submitJob'
	'checkJobNo'
	'waitForFinAndHealth'
	'cancelJobAndLog'
	'checkLogsNoError2'
)

FINS='cancelJobAndLog'

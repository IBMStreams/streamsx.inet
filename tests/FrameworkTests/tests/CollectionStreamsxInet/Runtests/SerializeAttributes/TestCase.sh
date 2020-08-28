
setCategory 'quick'

PREPS='copyOnly'

STEPS=(
	"splCompile"
	'submitJob'
	'checkJobNo'
	'waitForFinAndHealth'
	'cancelJobAndLog'
	'myEval'
)

FINS='cancelJobAndLog'

function myEval {
	linewisePatternMatchInterceptAndSuccess "$TT_dataDir/Tuples" "true" '*seq_=0* success *' '*seq_=1* success *' '*seq_=2* success *' '*seq_=3* success *' '*seq_=4* success *' '*seq_=5* success *' '*seq_=6* success *' '*seq_=7* success *'
}

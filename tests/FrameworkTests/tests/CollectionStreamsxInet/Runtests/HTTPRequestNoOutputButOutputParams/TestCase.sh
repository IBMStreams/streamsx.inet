#--variantCount=8

function myExplain {
	case "$TTRO_variantCase" in
	0) echo "variant $TTRO_variantCase - no output port but use outputDataLine parameter";;
	1) echo "variant $TTRO_variantCase - no output port but use outputBody parameter";;
	2) echo "variant $TTRO_variantCase - no output port but use outputContentEncoding parameter";;
	3) echo "variant $TTRO_variantCase - no output port but use outputContentType parameter";;
	4) echo "variant $TTRO_variantCase - no output port but use outputHeader parameter";;
	5) echo "variant $TTRO_variantCase - no output port but use outputStatus parameter";;
	6) echo "variant $TTRO_variantCase - no output port but use outputStatusCode parameter";;
	7) echo "variant $TTRO_variantCase - no output port but use errorDiagnostics parameter";;
	esac
}

PREPS=(
	'myExplain'
	'copyAndMorphSpl'
)

STEPS=(
	"splCompile host=$TTPR_httpServerAddr"
	'executeLogAndError output/bin/standalone -t 2'
	'linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST0226E Operator has output attribute name parameter but has no output port*"'
)

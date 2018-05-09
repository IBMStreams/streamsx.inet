#--variantCount=8

function myExplain {
	case "$TTRO_variantCase" in
	0) echo "variant $TTRO_variantCase - no output port but use outputDataLine parameter wrong type";;
	1) echo "variant $TTRO_variantCase - no output port but use outputBody parameter wrong type";;
	2) echo "variant $TTRO_variantCase - no output port but use outputContentEncoding parameter wrong type";;
	3) echo "variant $TTRO_variantCase - no output port but use outputContentType parameter wrong type";;
	4) echo "variant $TTRO_variantCase - no output port but use outputHeader parameter wrong type";;
	5) echo "variant $TTRO_variantCase - no output port but use outputHeader parameter wrong list type";;
	6) echo "variant $TTRO_variantCase - no output port but use outputStatus parameter wrong type";;
	7) echo "variant $TTRO_variantCase - no output port but use outputStatusCode parameter wrong type";;
	esac
}

PREPS=(
	'myExplain'
	'copyAndTransformSpl'
)

STEPS=(
	'splCompile'
	'executeLogAndError output/bin/standalone -t 2'
	'linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes[$TTRO_variantCase]}"'
)

errorCodes=(
	"*CDIST0223E Only types 'USTRING' and 'RSTRING' are allowed for attribute 'myAttribute'*"
	"*CDIST0223E Only types 'USTRING' and 'RSTRING' are allowed for attribute 'myAttribute'*"
	"*CDIST0223E Only types 'USTRING' and 'RSTRING' are allowed for attribute 'myAttribute'*"
	"*CDIST0223E Only types 'USTRING' and 'RSTRING' are allowed for attribute 'myAttribute'*"
	"*CDIST0222E Only type 'LIST' is allowed for attribute 'myAttribute'*"
	"*CDIST0224E Only element type 'RSTRING' is allowed for attribute 'myAttribute'*"
	"*CDIST0223E Only types 'USTRING' and 'RSTRING' are allowed for attribute 'myAttribute'*"
	"*CDIST0222E Only type 'INT32' is allowed for attribute 'stat'*"
)
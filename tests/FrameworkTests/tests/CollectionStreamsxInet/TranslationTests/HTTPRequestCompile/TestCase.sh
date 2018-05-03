#The first version should compile with success
#the 2 should produce the compiler error

#--variantCount=2

PREPS=(
        copyAndTransformSpl
        TT_mainComposite='Main'
)
        
STEPS='myCompile myEvaluate'

errorCodes=( '' '*CDIST0200E*' )

function myCompile {
	if [[ TTRO_variantCase -eq 0 ]]; then
		splCompileInterceptAndSuccess
	else
		splCompileInterceptAndError
	fi
}

function myEvaluate {
	if [[ TTRO_variantCase -eq 1 ]]; then
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" '' "${errorCodes[$TTRO_variantCase]}"
	fi
}
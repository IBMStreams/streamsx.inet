#The first version should compile with success
#the 2 should produce the compiler error

#--variantCount=2

PREPS=(
        copyAndTransformSpl
        TT_mainComposite='com.ibm.streamsx.inet.http.sample::HTTPStreamSample'
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
		if ! linewisePatternMatch "$TT_evaluationFile" '' "${errorCodes[$TTRO_variantCase]}"; then
			setFailure 'no match'
		fi
	fi
}
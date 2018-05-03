#The first version should compile with success
#the 2,3 and 4 should produce the compiler error

#--variantCount=4

setVar 'TTRO_prepsCase' 'copyAndTransformSpl TT_mainComposite=com.ibm.streamsx.inet.sample::GetWeather'
setVar 'TTRO_stepsCase' 'myCompile myEvaluate'

errorCodes=( '' '*CDIST0209E*' '*CDIST0210E*' '*CDIST0200E*' )

function myCompile {
	if [[ TTRO_variantCase -eq 0 ]]; then
		splCompileInterceptAndSuccess
	else
		splCompileInterceptAndError
	fi
}

function myEvaluate {
	if [[ TTRO_variantCase -ne 0 ]]; then
		if ! linewisePatternMatch "$TT_evaluationFile" '' "${errorCodes[$TTRO_variantCase]}"; then
			failureOccurred='true'
		fi
	fi
}
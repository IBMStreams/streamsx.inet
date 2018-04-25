#--variantList:="$TTRO_streamsxInetSamples"

function testStep {
	local save="$PWD"
	cd "$TTRO_streamsxInetSamplesPath/$TTRO_variantCase"
	export SPL_CMD_ARGS=''
	echoExecuteAndIntercept2 'success' 'make'
	cd "$save"
	return 0
}
#--variantList:="$TTRO_streamsxInetSamples"

function testStep {
	local save="$PWD"
	cd "$TTRO_streamsxInetSamplesPath/$TTRO_variantCase"
	export SPL_CMD_ARGS=''
	export STREAMS_INET_TOOLKIT="$TTPR_streamsxInetToolkit"
	echoExecuteAndIntercept2 'success' 'make'
	cd "$save"
	return 0
}
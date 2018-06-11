#--variantList:="$TTRO_streamsxInetSamples"

setCategory 'quick'

function testStep {
	local save="$PWD"
	cd "$TTRO_streamsxInetSamplesPath/$TTRO_variantCase"
	export SPL_CMD_ARGS=''
	export STREAMS_INET_TOOLKIT="$TT_toolkitPath"
	echoExecuteAndIntercept2 'success' 'make'
	cd "$save"
	return 0
}
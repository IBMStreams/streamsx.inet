#samples path
setVar 'TTRO_streamsxInetSamplesPath' "$TTRO_inputDir/../../../samples"
#setVar 'TTRO_streamsxInetSamplesPath' "$STREAMS_INSTALL/samples/com.ibm.streamsx.inet"

#toolkit path
setVar 'TTPR_streamsxInetToolkit' "$TTRO_inputDir/../../../com.ibm.streamsx.inet"
#setVar 'TTPR_streamsxInetToolkit' "$STREAMS_INSTALL/toolkits/com.ibm.streamsx.inet"

#Some sample need json toolkit to compile
setVar 'TTPR_streamsxJsonToolkit' "$STREAMS_INSTALL/toolkits/com.ibm.streamsx.json"

setVar 'TT_toolkitPath' "${TTPR_streamsxInetToolkit}:${TTPR_streamsxJsonToolkit}" #consider more than one tk...

setVar 'TTPR_timeout' 240

# The common test suite for inet toolkit tests
import "$TTRO_scriptDir/streamsutils.sh"

#collect all samples as variant string for case Samples
all=''
short=''
cd "$TTRO_streamsxInetSamplesPath"
for x in $TTRO_streamsxInetSamplesPath/*; do
	if [[ -f $x/Makefile ]]; then
		short="${x#$TTRO_streamsxInetSamplesPath/}"
		all="$all $short"
	fi
done
printInfo "All samples are: $all"
setVar 'TTRO_streamsxInetSamples' "$all"

PREPS=(
	'export'
	'ps'
)

#!/bin/bash

installer=$(echo -n testframeInstaller_*.sh)
echo "Testframework installer is $installer"
if [[ ( ! -e scripts ) || ( scripts -ot $installer ) ]]; then
	if ! eval ./$installer "$PWD/scripts"; then
		echo "Error during testframework installation" >&2
		exit 1
	fi
fi
if [[ -e ./scripts/bin/runTTF ]]; then
	./scripts/bin/runTTF -i tests "$@"
else
	echo "./scripts/bin/runTTF is not existing! Run the script './installTestFramework.sh' first" >&2
	exit 1
fi

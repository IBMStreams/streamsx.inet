#!/bin/bash

if [[ -e ./scripts/bin/runTTF ]]; then
	./scripts/bin/runTTF -i tests "$@"
else
	echo "./scripts/bin/runTTF is not existing! Run the script './installTestFramework.sh' first"
	exit 1
fi

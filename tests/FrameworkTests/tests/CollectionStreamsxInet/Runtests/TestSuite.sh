#--variantList="distributed standalone"

if [[ $TTRO_variantSuite == standalone ]]; then skip; fi

#Make sure instance and domain is running
PREPS='cleanUpInstAndDomain mkDomain startDomain mkInst startInst'
FINS='cleanUpInstAndDomain'

#--variantList="distributed standalone"

if [[ $TTRO_variantSuite == standalone ]]; then skip; fi

setVar 'TTPR_timeout' 240
#Make sure instance and domain is running
PREPS='cleanUpInstAndDomain mkDomain startDomain mkInst startInst'
FINS='cleanUpInstAndDomain'

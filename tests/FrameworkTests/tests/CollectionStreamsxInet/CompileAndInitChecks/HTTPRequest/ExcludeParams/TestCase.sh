#--variantCount=12

function explain {
	case "$TTRO_variantCase" in
	0)  echo "error: use fixedUrl and url";;
	1)  echo "error: use fixedMethod and method";;
	2)  echo "error: use fixedContentType and contentType";;
	3)  echo "error: no method and no fixedMethod but with url";;
	4)  echo "error: no method and no fixedMethod but with fixedUrl";;
	5)  echo "error: no url and no fixedUrl but with mehod";;
	6)  echo "error: no url and no fixedUrl but with fixedMethod";;
	7)  echo "error: use outputBody and outputDataLine";;
	8)  echo "error: use sslTrustStoreFile and sslAcceptAllCertificates=true";;
	9)  echo "error: use sslTrustStoreFile and sslAcceptAllCertificates=false";;
	10) echo "error: use sslTrustStoreFile only";;
	11) echo "error: use sslTrustStorePassword only";;
	esac
}

PREPS='explain copyAndTransformSpl TT_mainComposite=Main'
        
STEPS=(
	'splCompileInterceptAndError'
	'linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes[$TTRO_variantCase]}"'
)

errorCodes=(
	'ERROR: CDISP7303E Operator parameter fixedUrl cannot be set when parameter url is set*'
	'ERROR: CDISP7303E Operator parameter fixedMethod cannot be set when parameter method is set*'
	'ERROR: CDISP7303E Operator parameter fixedContentType cannot be set when parameter contentType is set*'
	'ERROR: CDIST0215E HTTPRequest operator requires parameter method or fixedMethod*'
	'ERROR: CDIST0215E HTTPRequest operator requires parameter method or fixedMethod*'
	'ERROR: CDIST0216E HTTPRequest operator requires parameter url or fixedUrl*'
	'ERROR: CDIST0216E HTTPRequest operator requires parameter url or fixedUrl*'
	'ERROR: CDISP7303E Operator parameter outputBody cannot be set when parameter outputDataLine is set*'
	'ERROR: CDISP7303E Operator parameter sslTrustStoreFile cannot be set when parameter sslAcceptAllCertificates is set*'
	'ERROR: CDISP7303E Operator parameter sslTrustStoreFile cannot be set when parameter sslAcceptAllCertificates is set*'
	'ERROR: CDIST0217E HTTPRequest operator: Invalid trust store parameters, provide both a sslTrustStoreFile and a sslTrustStorePassword or provide neither*'
	'ERROR: CDIST0217E HTTPRequest operator: Invalid trust store parameters, provide both a sslTrustStoreFile and a sslTrustStorePassword or provide neither*'
)

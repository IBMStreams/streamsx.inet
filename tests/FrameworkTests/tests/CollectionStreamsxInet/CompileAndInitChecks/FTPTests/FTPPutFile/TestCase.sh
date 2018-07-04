# Compiler checks for FTPPutFile

#--variantList='success noParmProt noParmUser noParmHost noPath noParamLocalFile noOutFunc1 wrongDefaultErrAtt'

PREPS=copyAndMorphSpl
STEPS=(
	myCompile
	'linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${myComparePattern[$TTRO_variantCase]}"'
)

function myCompile {
	if [[ ( $TTRO_variantCase == "success" ) || ( $TTRO_variantCase == "noOutFunc1" ) ]]; then
		splCompileInterceptAndSuccess
	else
		splCompileInterceptAndError
	fi
}

declare -Ar myComparePattern=(
	['success']='*\[Bundle\] Main.sab*'
	['noParmProt']='*CDISP0208E ERROR: The protocol parameter is required by the operator model for the FilenameStream operator, but is not found*'
	['noParmUser']='*CDISP0208E ERROR: The username parameter is required by the operator model for the FilenameStream operator, but is not found*'
	['noParmHost']='*CDISP0208E ERROR: The host parameter is required by the operator model for the FilenameStream operator, but is not found*'
	['noPath']='*CDISP0208E ERROR: The path parameter is required by the operator model for the FilenameStream operator, but is not found*'
	['noParamLocalFile']='*CDISP0208E ERROR: The localFilename parameter is required by the operator model for the FilenameStream operator, but is not found.*'
	['noOutFunc1']='*\[Bundle\] Main.sab*'
	['wrongDefaultErrAtt']='*CDIST0214E: The error output port must have one attribute of type rstring*'
)
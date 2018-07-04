# Compiler checks for FTPCommand operator

#--variantList='success noParmProt noParmUser noParmHost noPath noParamCommand paramDirScan wrongDefaultErrAtt'

PREPS=copyAndMorphSpl
STEPS=(
	myCompile
	'linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${myComparePattern[$TTRO_variantCase]}"'
)

function myCompile {
	if [[ $TTRO_variantCase == "success" ]]; then
		splCompileInterceptAndSuccess
	else
		splCompileInterceptAndError
	fi
}

declare -Ar myComparePattern=(
	['success']='*\[Bundle\] Main.sab*'
	['noParmProt']='*CDISP0208E ERROR: The protocol parameter is required by the operator model for the ResultStream operator, but is not found*'
	['noParmUser']='*CDISP0208E ERROR: The username parameter is required by the operator model for the ResultStream operator, but is not found*'
	['noParmHost']='*CDISP0208E ERROR: The host parameter is required by the operator model for the ResultStream operator, but is not found*'
	['noPath']='*CDISP0208E ERROR: The path parameter is required by the operator model for the ResultStream operator, but is not found*'
	['noParamCommand']='*CDISP0208E ERROR: The command parameter is required by the operator model for the ResultStream operator, but is not found*'
	['paramDirScan']='*CDISP0054E ERROR: The isDirReader parameter is unknown and was not defined for the FTPCommand operator*'
	['wrongFunctionSet']="*CDIST0213E: The custom output functions: 'FileName()', 'FileSize()', 'FileDate()', 'FileUser()', 'FileGroup()', 'FileInfo()' and 'IsFile()' can be used only in directory reader function mode*"
	['wrongDefaultErrAtt']='*CDIST0214E: The error output port must have one attribute of type rstring*'
	)
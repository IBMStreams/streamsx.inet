# Compiler checks for FTP Reader operator as directory reader

#--variantList='success noInpPort noParmProt noParmUser noParmHost noPath wrongFunctionSet noOutFunc1 wrongDefaultErrAtt'

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
	['noInpPort']='*CDISP0203E ERROR: The number of input ports in the FilenameStream operator does not match the number that is specified by the operator model*'
	['noParmProt']='*CDISP0208E ERROR: The protocol parameter is required by the operator model for the FilenameStream operator, but is not found*'
	['noParmUser']='*CDISP0208E ERROR: The username parameter is required by the operator model for the FilenameStream operator, but is not found*'
	['noParmHost']='*CDISP0208E ERROR: The host parameter is required by the operator model for the FilenameStream operator, but is not found*'
	['noPath']='*CDISP0208E ERROR: The path parameter is required by the operator model for the FilenameStream operator, but is not found*'
	['wrongFunctionSet']="*CDIST0213E: The custom output functions: 'FileName()', 'FileSize()', 'FileDate()', 'FileUser()', 'FileGroup()', 'FileInfo()' and 'IsFile()' can be used only in directory reader function mode*"
	['noOutFunc1']='*CDISP0206E ERROR: An assignment cannot be generated for the*'
	['wrongDefaultErrAtt']='*CDIST0214E: The error output port must have one attribute of type rstring*'
)
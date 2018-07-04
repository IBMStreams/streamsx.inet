# Compiler checks for FTP Reader operator as directory reader

#--variantList='success success2 noInpPort noParmProt noParmUser noParmHost noPath wrongFunctionSet noOutFunc1 wrongDefaultErrAtt'

declare -Ar myComparePattern=(
	['success']='*\[Bundle\] Main.sab*'
	['success2']='*\[Bundle\] Main.sab*'
	['noInpPort']='*CDISP0203E ERROR: The number of input ports in the BinFileStream operator does not match the number that is specified by the operator model*'
	['noParmProt']='*CDISP0208E ERROR: The protocol parameter is required by the operator model for the BinFileStream operator, but is not found*'
	['noParmUser']='*CDISP0208E ERROR: The username parameter is required by the operator model for the BinFileStream operator, but is not found*'
	['noParmHost']='*CDISP0208E ERROR: The host parameter is required by the operator model for the BinFileStream operator, but is not found*'
	['noPath']='*CDISP0208E ERROR: The path parameter is required by the operator model for the BinFileStream operator, but is not found*'
	['wrongFunctionSet']="*ERROR: CDIST0211E: The custom output function * is not available in operation mode 'isDirReader*"
	['noOutFunc1']='*CDISP0206E ERROR: An assignment cannot be generated for the*'
	['wrongDefaultErrAtt']='*CDIST0214E: The error output port must have one attribute of type rstring*'
)

PREPS=copyAndMorphSpl
STEPS=(
	myCompile
	"linewisePatternMatchInterceptAndSuccess \"$TT_evaluationFile\" \"\" \"${myComparePattern[$TTRO_variantCase]}\""
)

function myCompile {
	if [[ $TTRO_variantCase == "success" || $TTRO_variantCase == "success2" ]]; then
		splCompileInterceptAndSuccess
	else
		splCompileInterceptAndError
	fi
}

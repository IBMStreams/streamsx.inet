#--variantList='de_DE fr_FR it_IT es_ES pt_BR ja_JP zh_CN zh_TW en_US'
##--variantList='en_US'

function testPreparation {
	local tmp="${TTRO_variantSuite}.UTF-8"
	echo "Set language $tmp"
	export LC_ALL="$tmp"
}
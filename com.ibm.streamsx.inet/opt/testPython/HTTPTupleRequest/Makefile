all:	basic

cruft:
	rm -rf tk[0-9]*
	rm -rf *.sab

header:	cruft
	python -m unittest headerTests.py

basic:	cruft
	python -m unittest basicTests.py

tuple:	cruft
	python -m unittest tupleTests.py

size:	cruft
	python -m unittest sizeTests.py

asyn:	cruft
	python -m unittest asynTests.py


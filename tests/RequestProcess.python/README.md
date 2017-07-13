## README -- Tests for HTTPResponseProcess operator
The operator enables Streams's applications to process HTML forms and REST calls.


The tests use the Python's unittest modules, which is accessed using the Streams topology toolkit. 

Prerequisite for tests: 

* Python 3.5, the Anaconda version of Python is recommended. As of 4/10/2017 a version could be gotten from https://repo.continuum.io/archive/index.html. 

* Streams topology toolkit. 


The Streams topology toolkit can be installed using Python pip command. For example, to install the Streams' topology toolkit with pip, execute : 

```> pip install streamsx```

The PYTHONHOME property is used by Streams to locate the version of Python to use, this is done using the streamtool's 'setproperty' command.  For example, if your Python is installed in */home/streamsadmin/anaconda3* execute the following command to set the property. 

```> streamtool setproperty --application-ev PYTHONHOME=/home/streamsadmin/anaconda3```

## Notes 


### running all the tests

```make all```


### asyncTests.py - 
This test runs into issues since it's is testing that messages are not ordered on 
arrival. Unfortunaly at time the the network changes order and the appearance
of ordered on arrival occurs. 


# README --  FrameworkTests

This directory provides an automatic test for a number of operators of the inet toolkit.

## Test Execution

To start the full test execute:  
`./runTest.sh`

To start a quick test, execute:  
`./runTest.sh --category quick`

This script installs the test framework in directory `scripts` and starts the test execution. The script delivers the following result codes:  
0     : all tests Success  
20    : at least one test fails  
25    : at least one test error  
26    : Error during suite execution  
130   : SIGINT received  
other : another fatal error has occurred  

More options are available and explained with command:  
`./runTest.sh --help`

## Test Sequence

The `runTest.sh` installs the test framework into directory `scripts` and starts the test framework. The test framework 
checks if there is a running Streams instance and starts the ftp test server and the the http test server automatically. 

If the Streams instance is not running, a domain and an instance is created from the scratch and started. You can force the 
creation of instance and domain with command line option `--clean`

In the standard environment the ftp-test server is started automatically from the test script. If the 
property `TTPR_ftpServerHost` is set to a non empty value, the automatic start is skipped and the provided server is used. 
In this case the properties `TTPR_ftpServerUser` and `TTPR_ftpServerPasswd` must provide the username and password for the ftp-tests. 
You can skip the ftp test cases if the property `TTPR_ftpServerHost` is set to an empty value.

In the standard environment the http-test server is started automatically from the test script. If the 
property `TTPR_httpServerHost` is set, the automatic start is skipped and the provided server is used. In this case you must also 
provide `TTPR_httpServerAddr` and `TTPR_httpsServerAddr`. This values must have the form <hostname>:<port>

The inet toolkit is expected in directory `../../com.ibm.streamsx.inet/` and must be built with the current Streams version. 
The inet toolkit samples are expected in `TTRO_streamsxInetSamplesPath`. 

Use command line option `-D <name>=<value>` to set external variables or provide a new properties file with command line option 
`--properties <filename>`. The standard properties file is `tests/TestProperties.sh`.

## Requirements

The test framework requires an valid Streams installation and environment.

The ftp test server requires the packages **vsftpd** and **ftp**. Additionally the current user must be able to execute 
`service vsftpd start` without password interaction (sudoers configuration). Alternatively you can start the ftp server by 
hand and provide property `TTPR_ftpServerHost`.
If the standard setup of the ftp server is used, an user `ftpuser` with password `streams` is required.

The http test server requires a standard java 1.8 runtime. With the streams java runtime the ssl connections are not possible.
The http test server requires that ports 8097 and 1443 are available at the local host.

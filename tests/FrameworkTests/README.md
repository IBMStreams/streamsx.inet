# README --  FrameworkTests

This directory provides an automatic test for a number of operators of the inet toolkit.

## Prerequisites

### Installation and Environment
This test suite requires an local Streams installation (minimum v4.0.1) and a propperly setup of the Streams environment. 
This environments are required:
* STREAMS_INSTALL
* JAVA_HOME
* STREAMS_DOMAIN_ID
* STREAMS_INSTANCE_ID
* STREAMS_ZKCONNECT - if an external zookeeper has to be used

Additionaly this test collection requires that the packages `curl`, `ftp` and `lftp` are installed.

### FTP Test Server
The script framework automatically tries to start a local ftp-server, which requires package `vsftp`. Additionally the current user must be able to execute 
`service vsftpd start` without password interaction (sudoers configuration).

Alternatively you can start the ftp server by hand and provide property `TTPR_ftpServerHost` with the value of the ftp-servers hostname.

If the standard setup of the ftp server is used, an user `ftpuser` with password `streams` is required. This standart setup can be overwritten
with properties `TTPR_ftpServerUser` and `TTPR_ftpServerPasswd` in TestProperties.sh file.

If the property `TTPR_ftpServerHost` is set to an empty value, all ftp test are skipped.

**Note:** The FtpTest requires an running ssh daemon at the ftp server host.

To check the ssh deamon and to start the sftp-server use:
    sudo service sshd status
    sudo service sshd start

### HTTP Test Server
In the standard environment the http-test server is started automatically from the test script.

This HTTP Test Server requires the java 1.8 runtime from the streams installation and that ports **8097** and **1443** are available at the local host.

If the property `TTPR_httpServerHost` is provided, the automatic start is skipped and the provided server is used.
In this case you must also provide `TTPR_httpServerAddr` and `TTPR_httpsServerAddr`. This values must have the form : *hostname:port*

### Toolkit and Toolkit Samples 
The standard setup expects the inet toolkit in directory *../../com.ibm.streamsx.inet/* and the toolkit samples in *../../samples/*.
You can change this setting with properties `TTPR_streamsxInetToolkit` and `TTRO_streamsxInetSamplesPath`.

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

Use command line option `-D <name>=<value>` to set external variables or provide a new properties file with command line option 
`--properties <filename>`. The standard properties file is `tests/TestProperties.sh`.


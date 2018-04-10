# Running applications that use the Internet Toolkit

To create applications that use the Internet Toolkit, you must configure either Streams Studio
or the SPL compiler to be aware of the location of the toolkit. 

## Before you begin

Install IBM InfoSphere Streams. Configure the product environment variables by entering the following command: 
    source product-installation-root-directory/product-version/bin/streamsprofile.sh

## About this task

After the location of the toolkit is communicated to the compiler, the SPL artifacts that are specified
in the toolkit can be used by an application. The application can include a use directive to bring the necessary namespaces into scope.
Alternatively, you can fully qualify the operators that are provided by toolkit with their namespaces as prefixes.

## Procedure

1. Configure the SPL compiler to find the toolkit root directory. Use one of the following methods:
  * Set the **STREAMS_SPLPATH** environment variable to the root directory of a toolkit
    or multiple toolkits (with : as a separator).  For example:
      export STREAMS_SPLPATH=$STREAMS_INSTALL/toolkits/com.ibm.streamsx.inet
  * Specify the **-t** or **--spl-path** command parameter when you run the **sc** command. For example:
      sc -t $STREAMS_INSTALL/toolkits/com.ibm.streamsx.inet -M MyMain
    where MyMain is the name of the SPL main composite.
    **Note**: These command parameters override the **STREAMS_SPLPATH** environment variable.
  * Add the toolkit location in InfoSphere Streams Studio.
2. Develop your application. To avoid the need to fully qualify the operators, add a use directive in your application. 
  * For example, you can add the following clause in your SPL source file:
      use com.ibm.streamsx.inet::*;
      use com.ibm.streamsx.inet.http::*;
    You can also specify a use clause for individual operators by replacing the asterisk (\*) with the operator name. For example: 
      use com.ibm.streamsx.inet::InetSource;
3. Build your application.  You can use the **sc** command or Streams Studio.  
4. Start the InfoSphere Streams instance. 
5. Run the application. You can submit the application as a job by using the **streamtool submitjob** command or by using Streams Studio. 



# Developing operators of this toolkit:

This toolkit uses Apache Ant 1.8 (or later) to build.

The FTP operators in namespace com.ibm.streamsx.inet.ftp need the system library libcurl (version 7.19.7 or higher) installed. Developers needs additionally the libcurl-devel package.

The top-level build.xml contains the main targets:

* all - Builds and creates SPLDOC for the toolkit and samples. Developers should ensure this target is successful when creating a pull request.
* toolkit - Build the complete toolkit code
* build-all-samples - Builds all samples. Developers should ensure this target is successful when creating a pull request.
* release - Builds release artifacts, which is a tar bundle containing the toolkits and samples. It includes stamping the SPLDOC and toolkit version numbers with the git commit number (thus requires git to be available).

The release should use Java 8 for the Java compile to allow the widest use of the toolkit (with Streams 4.0.1 or later). (Note Streams 4.0.1 ships Java 8).
The build script inserts the commit hash into the toolkit version if the version number has a form like X.Y.Z.__dev__ 
This change in the info.xml file can be removed with ant target revertversion.


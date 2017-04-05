For developers of this toolkit:

This toolkit uses Apache Ant 1.8 (or later) to build.

Internally Apache Maven 3.2 (or later) and Make are used.

Download and setup directions for Apache Maven can be found here:
http://maven.apache.org/download.cgi#Installation

The top-level build.xml contains two main targets:

* all - Builds and creates SPLDOC for the toolkit and samples. Developers should ensure this target is successful when creating a pull request.
* release - Builds release artifacts, which is a tar bundle containing the toolkits and samples. It includes stamping the SPLDOC and toolkit version numbers with the git commit number (thus requires git to be available). The release should use Java 8 for the Java compile to allow the widest use of the toolkit (with Streams 4.0.1 or later). (Note Streams 4.0.1 ships Java 8).
* build-all-samples - Builds all samples. Developers should ensure this target is successful when creating a pull request.

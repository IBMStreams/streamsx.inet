For developers of this toolkit:

This toolkit uses Apache Ant 1.8 (or later).

The top-level build.xml contains two main targets:

* all - Builds and creates SPLDOC for the toolkit and samples. Developers should ensure this target is successful when creating a pull request.
* release - Builds release artifacts, which is a tar bundle containing the toolkits and samples. It includes stamping the SPLDOC and toolkit version numbers with the git commit number (thus requires git to be available). The release should use Java 6 for the Java compile to allow the widest use of the toolkit. (Note Streams 3.2 ships Java 7).

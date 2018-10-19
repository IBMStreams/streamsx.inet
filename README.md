## README --  IBMStreams/streamsx.inet

The IBMStreams/streamsx.inet toolkit project is an open source IBM InfoSphere Streams toolkit project supporting common internet protocols, such as HTTP, WebSockets, etc.

Note that development is proceeding on two branches: 
* The `master` branch is the main development stream and contains operators and functions which are available on all supported platforms. A subset of this operators is shipped with the Streams product.
* The branch `HTTPRequestAsync` contains a new operator which is designed to work well with HTTP(S) (REST) services in asynchronous manner. It should be considered pre-release.

This toolkit implements the NLS feature. Use the guidelines for the message bundle that are described in [The messages and the NLS for toolkits](https://github.com/IBMStreams/administration/wiki/Messages-and-National-Language-Support-for-toolkits)

Support for Streams 4.0 Now Available!
https://github.com/IBMStreams/streamsx.inet/releases

Project Overview at: http://ibmstreams.github.io/streamsx.inet/

Operators, functions & types are described using SPLDOC at

http://ibmstreams.github.io/streamsx.inet/com.ibm.streamsx.inet/doc/spldoc/html/index.html

The http rest functions and the WebSocket server functions are now moved into [streamsx.inetserver toolkit](https://github.com/IBMStreams/streamsx.inetserver/releases)

To learn more about Streams:
* [IBM Streams on Github](http://ibmstreams.github.io)
* [Introduction to Streams Quick Start Edition](http://ibmstreams.github.io/streamsx.documentation/docs/4.1/qse-intro/)
* [Streams Getting Started Guide](http://ibmstreams.github.io/streamsx.documentation/docs/4.1/qse-getting-started/)
* [StreamsDev](https://developer.ibm.com/streamsdev/)

The FTP operators in namespace com.ibm.streamsx.inet.ftp need the system library libcurl (version 7.19.7 or higher) installed.
Developers needs additionally the libcurl-devel package.

Steps to use the `HTTPRequestAsync` operator:
* Checkout branch HTTPRequestAsync
* change into directory com.ibm.streamsx.inet.async
* build the toolkit `ant clean all`
* a sample is available in directrory samples/HTTPClientAsync
* build the sample with `make`

## README --  HTTPTestServer

This project implements a http test server which can be used in test for http operators/functions.
The server is automatically started from the framework test script.

to build the server by hand execute:
`ant`

to clean up execute:
`ant clean `

to start the server by hand:
`start.sh`

to stop the server by hand:
`stop.sh`

Note: This server must run with a standard java 1.8 runtime. With the streams java runtime the ssl connections are not possible


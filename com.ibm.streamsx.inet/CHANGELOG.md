# Changes
=======

## v3.2.2
* Resolved [#335 Operator HTTPRequest throws exception when used with content type: application/octet-stream](https://github.com/IBMStreams/streamsx.inet/issues/355)

## v3.2.1
* [#351](https://github.com/IBMStreams/streamsx.inet/issues/351) Update apache commons-codec to v1.14

## v3.2.0
* Update apache http client library to v 4.5.12
* HTTPRequest operator: Introduce parameter socketTimeout

## v3.1.2:
* Update globalization messages
* Removed languages ko_KR, ru_RU

## v3.1.1:
* Remove opt/downloaded from all lib enties to avoid classpath warnings
* Corrections in tests 

## v3.1.0:
* Update apache http client library to v 4.5.7
* HTTPRequest operator: Allows tls-client authentication
* HTTPRequest operator: Enable TLS1.2 for all connections
* HTTPRequest operator: Relaxed requirements for parameter sslTrustStorePassword
* HTTPRequest operator: server certificate does not require host match
* Description added: how to create https certificates
* Faster build
* One common spl doc for samples generated
* HTTP Test server works now also with streams java ssl engine
* Added tests: HTTPLineTest, URLEncodeDecode
* Removed old script tests
* Cloud test suite added
* Samples: Makefiles take the streams studio settings into account

## v3.0.0:
* The http rest functions and the WebSocket server functions are now moved into [streamsx.inetserver toolkit](https://github.com/IBMStreams/streamsx.inetserver/releases)
* Operator HTTPRequestAdd - new parameters extraHeaderAttribute, accessTokenAttribute and tokenTypeAttribute

## v2.9.6:
* Some minor fixes
* Enhanced test coverage

## v2.9.5:
* Internationalization of HTTPRequest Operator

## v2.9.5:
* Fixes for optional type
* Enhancement #324: HTTPTupleView Windowing does not support multiple partition keys in a comma-delimited string

## v2.9.4:
* Internationalization of HTTPRequest Operator
* Bug fixes in HTTPRequest Operator : improved redirection handling, improved diagnostics
* Enable binary output of HTTPRequest operator
* Deprecation hint in spl doc for HTTPPost, HTTPStreamRead and HTTPGetJSONContent
* spl type com.ibm.streamsx.inet.http::HTTPRequest moved to namespace com.ibm.streamsx.inet.rest to avoid name conflict with HTTPRequest operator
* Update apache http client library to 4.5.5
* New sample HTTPPostDemo
* New Operator HTTPRequest
* New sample HTTPRequestDemo

# Changes
=======

## v3.0.1:
* HTTPRequest operator allows tls-client authentication
* Relaxed requirements for parameter sslTrustStorePassword

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

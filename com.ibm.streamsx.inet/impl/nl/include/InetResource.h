// This is a generated header.  Any modifications will be lost.
#ifndef NL_INETRESOURCE_H
#define NL_INETRESOURCE_H

#include <SPL/Runtime/Utility/FormattableMessage.h>

#define INET_MALFORMED_URI(p0) \
   (::SPL::FormattableMessage1<typeof(p0)>("com.ibm.streamsx.inet", "InetResource", "en_US/InetResource.xlf", "CDIST0200E", "The Uniform Resource Identifier string {0} specified in the URIList parameter contains a syntax error.  The Processing Element will shut down now.", p0))

#define INET_NONZERO_LIBCURL_RC(p0, p1, p2) \
   (::SPL::FormattableMessage3<typeof(p0),typeof(p1),typeof(p2)>("com.ibm.streamsx.inet", "InetResource", "en_US/InetResource.xlf", "CDIST0201W", "A Uniform Resource Identifier retrieval from {0} was not successful.  The numeric return code from the libcurl agent was {1,number,integer} and the error message text was: {2}.  The Processing Element will continue running.", p0, p1, p2))

#define INET_NONZERO_LIBCURL_RC_REPEATED(p0, p1, p2, p3) \
   (::SPL::FormattableMessage4<typeof(p0),typeof(p1),typeof(p2),typeof(p3)>("com.ibm.streamsx.inet", "InetResource", "en_US/InetResource.xlf", "CDIST0202W", "A Uniform Resource Identifier retrieval from {0} has not been successful for the last {3,number,integer} attempts.  The numeric return code from the libcurl agent was {1,number,integer} and the error message text was: {2}.  The Processing Element will continue running.", p0, p1, p2, p3))

#endif  // NL_INETRESOURCE_H

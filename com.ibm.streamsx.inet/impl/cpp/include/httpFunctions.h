#ifndef HTTP_FOR_STREAMS
#define HTTP_FOR_STREAMS
#include "curl/curl.h"
#include <SPL/Runtime/Type/Blob.h>
#include <SPL/Runtime/Type/List.h>

namespace com_ibm_streamsx_inet_http {


// We're just writing bytes.
size_t populate_rstring(char *ptr,size_t size, size_t nmemb, void*userdata);

SPL::rstring httpGet(const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password,SPL::int32 & error);

SPL::rstring httpPut(const SPL::rstring &  data, const  SPL::rstring &  url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring &  username, const SPL::rstring & password, SPL::list<SPL::rstring>& headers, SPL::int32 & error);

SPL::rstring httpPost(const SPL::rstring &  data, const  SPL::rstring &  url, const SPL::list<SPL::rstring> & extraHeaders,  const SPL::rstring &  username, const SPL::rstring & password, SPL::list<SPL::rstring>& headers, SPL::int32 & error);


}
#endif

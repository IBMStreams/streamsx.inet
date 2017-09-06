/*
#######################################################################
# Copyright (C)2017, International Business Machines Corporation and
# others. All Rights Reserved.
#######################################################################
*/

#ifndef HTTP_CLIENT
#define HTTP_CLIENT

#include <map>
#include <tr1/unordered_map>
#include <streams_boost/function.hpp>
#include <streams_boost/range/iterator_range.hpp>
#include <streams_boost/scoped_ptr.hpp>
#include <streams_boost/shared_ptr.hpp>

using streams_boost::make_iterator_range;
using streams_boost::scoped_ptr;

template <class T>
struct headers_map {
	typedef streams_boost::iterator_range<typename T::const_iterator> type;
};

typedef std::string S;
typedef streams_boost::shared_ptr<void> void_ptr;
typedef std::multimap<std::string,std::string> multi_map;
typedef std::tr1::unordered_map<std::string,std::string> unordered_map;
typedef headers_map<multi_map>::type resp_headers;
typedef headers_map<unordered_map>::type req_headers;
typedef headers_map<unordered_map>::type req_params;

namespace httpClient {

//	typedef streams_boost::function<void()> MetricsHandler;
typedef streams_boost::function<void(void_ptr const&, std::string const&, resp_headers const&, std::string const&, uint16_t, uint32_t)> SubmitHandler;

enum Method {del, get, head, post, put};

class HttpClient {
public:

    // Construct the http client to hadle multiple http requests asynchronously
    // @param sHandler that will submit the data
    // @param mHandler that will update operator metrics
	HttpClient( SubmitHandler submitHandler /*, MetricsHandler metricsHandler*/, int timeout);
	~HttpClient();

	// Run http get on client
	void httpReq(Method, void_ptr const& ituple, std::string const& url, req_headers const&, req_params const&);
	void httpReq(Method, void_ptr const& ituple, std::string const& url, req_headers const&, std::string const&);

	// Run the client
	void run();

	// Stop the client
	void stop();

private:
	class HttpClientImpl;
	scoped_ptr<HttpClientImpl> pImpl;
};
}

#endif /* HTTP_CLIENT */

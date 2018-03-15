/*
#######################################################################
# Copyright (C)2017, International Business Machines Corporation and
# others. All Rights Reserved.
#######################################################################
*/

#define BOOST_NETWORK_ENABLE_HTTPS

#include "http/client.hpp"
#include "boost/network/include/http/client.hpp"
#include "boost/asio/io_service.hpp"

namespace network = boost::network;
namespace http = network::http;
namespace uri = network::uri;
namespace asio = boost::asio;

using asio::io_service;
using http_client = http::basic_client<http::tags::http_async_8bit_tcp_resolve, 1, 1>;
using http_options = http::client_options<http::tags::http_async_8bit_tcp_resolve>;
using http_request = http::basic_request<http::tags::http_async_8bit_tcp_resolve>;
using http_response = http::basic_response<http::tags::http_async_8bit_tcp_resolve>;
using io_service_ptr = std::shared_ptr<io_service>;

namespace httpClient {

std::string parse_chunk_encoding(std::string const& body_string) {
	std::string body;
	const std::string crlf = "\r\n";

	auto begin = body_string.begin();
	for (auto iter = std::search(begin, body_string.end(), crlf.begin(), crlf.end());
		iter != body_string.end(); iter = std::search(begin, body_string.end(), crlf.begin(), crlf.end())) {
		std::string line(begin, iter);
		if (line.empty()) {
			break;
		}
		std::stringstream stream(line);
		int len;
		stream >> std::hex >> len;
		std::advance(iter, 2);
		if (len == 0) {
			break;
		}
		if (len <= body_string.end() - iter) {
			body.insert(body.end(), iter, iter + len);
			std::advance(iter, len + 2);
		}
		begin = iter;
	}

	return body;
}

	struct BodyHandler {
		explicit BodyHandler(void_ptr const& ituple, std::shared_ptr<http_response> const& response, SubmitHandler const& submitHandler) :
							 ituple_(ituple), response_(response), submitHandler_(submitHandler) {}

		BOOST_NETWORK_HTTP_BODY_CALLBACK(operator(), range, error) {
			// in here, range is the Boost.Range iterator_range, and error is the Boost.System error code
			if (error) {
				if(error == asio::error::eof || error == asio::ssl::error::stream_truncated) { // not a real error - the whole body received or ssl conn truncated
					auto chunkedIter = response_->headers().find("Transfer-Encoding");
					auto endIter = response_->headers().cend();
					if(chunkedIter != endIter && chunkedIter->second == "chunked")
						body_ = parse_chunk_encoding(body_);

					submitHandler_(ituple_, body_, make_iterator_range(response_->headers()), response_->status_message(), response_->status(), 0);
				}
				else {
					submitHandler_(ituple_, "", make_iterator_range(multi_map()), error.category().name() + S(": ") + error.message(), error.value(), 1);
				}
			}
			else {
				body_.append(boost::begin(range), boost::end(range));
			}
		}

		std::string body_;
		void_ptr ituple_;
		std::shared_ptr<http_response> response_;
		SubmitHandler const& submitHandler_;
	};

	class HttpClient::HttpClientImpl {
	public:

		HttpClientImpl( SubmitHandler submitHandler, int timeout) :
			io_service_(std::make_shared<io_service>()),
			client_( http_client::options().follow_redirects(true).cache_resolved(true).io_service(io_service_).timeout(timeout)),
//			client_( http_client::options().follow_redirects(true).cache_resolved(true).io_service(io_service_).openssl_options(SSL_OP_NO_SSLv3 | SSL_OP_ALL).timeout(timeout)),
			submitHandler_(submitHandler) /*, metricsHandler_(metricsHandler) */ {}


		http_request buildReqHeaders(std::string const& url, req_headers const& headers) {
			http_request request(url);
			request.sni_hostname(request.host());

			for(auto const& header : headers)
				request << network::header(header.first, header.second);

			return request;
		}

		http_request buildReqUrl(std::string const& url, req_headers const& headers, req_params const& params) {
			uri::uri base_uri(url);

			for(auto const& param : params)
				base_uri << uri::query(param.first, uri::encoded(param.second));

			http_request request(base_uri);

			for(auto const& header : headers)
				request << network::header(header.first, header.second);

			return request;
		}

		void del(void_ptr const& ituple, http_request const& request) {
			std::shared_ptr<http_response> response = std::make_shared<http_response>();
			*response = client_.delete_(request, BodyHandler(ituple, response, submitHandler_));
		}

		void get(void_ptr const& ituple, http_request const& request) {
			std::shared_ptr<http_response> response = std::make_shared<http_response>();
			*response = client_.get(request, BodyHandler(ituple, response, submitHandler_));
		}

		void head(void_ptr const& ituple, http_request const& request) {
			try {
				try {
					http_response response = client_.head(request);
					network::body(response);

					submitHandler_(ituple, "", make_iterator_range(response.headers()), S("http status: ") + response.status_message(), response.status(), 0);

				} catch (std::exception_ptr& e) {
					std::rethrow_exception(e);
				}
			} catch (const boost::system::system_error& error) {
				submitHandler_(ituple, "", make_iterator_range(multi_map()), error.code().category().name() + S(": ") + error.what(), error.code().value(), 1);
			}
		}

		void post(void_ptr const& ituple, std::string const& url, http_request const& request, req_params const& params) {
			uri::uri base_uri(url);

			for(auto const& param : params)
				base_uri << uri::query(param.first, uri::encoded(param.second));

			std::shared_ptr<http_response> response = std::make_shared<http_response>();
			*response = client_.post(request, base_uri.query(), BodyHandler(ituple, response, submitHandler_));
		}

		void put(void_ptr const& ituple, std::string const& url, http_request const& request, req_params const& params) {
			uri::uri base_uri(url);

			for(auto const& param : params)
				base_uri << uri::query(param.first, uri::encoded(param.second));

			std::shared_ptr<http_response> response = std::make_shared<http_response>();
			*response = client_.put(request, base_uri.query(), BodyHandler(ituple, response, submitHandler_));
		}

		void post(void_ptr const& ituple, http_request const& request, std::string const& body) {
			std::shared_ptr<http_response> response = std::make_shared<http_response>();
			*response = client_.post(request, body, BodyHandler(ituple, response, submitHandler_));
		}

		void put(void_ptr const& ituple, http_request const& request, std::string const& body) {
			std::shared_ptr<http_response> response = std::make_shared<http_response>();
			*response = client_.put(request, body, BodyHandler(ituple, response, submitHandler_));
		}

		void run() {
			io_service::work work(*io_service_);
			io_service_->run();
		}

		void stop() {
			io_service_->stop();
		}

	private:
		io_service_ptr io_service_;
		http_client client_;

		SubmitHandler submitHandler_;
	};


	HttpClient::HttpClient( SubmitHandler submitHandler /*, MetricsHandler metricsHandler*/, int timeout) :
			pImpl(new HttpClientImpl( submitHandler /*, metricsHandler*/, timeout)) {}

	HttpClient::~HttpClient() {}


	void HttpClient::httpReq(Method method, void_ptr const& ituple, std::string const& url, req_headers const& headers, req_params const& params) {
		switch (method) {
			case del: {
				auto request = pImpl->buildReqUrl(url, headers, params);
				pImpl->del(ituple, request);
				break;
			}
			case get: {
				auto request = pImpl->buildReqUrl(url, headers, params);
				pImpl->get(ituple, request);
				break;
			}
			case head: {
				auto request = pImpl->buildReqUrl(url, headers, params);
				pImpl->head(ituple, request);
				break;
			}
			case post: {
				auto request = pImpl->buildReqHeaders(url, headers);
				pImpl->post(ituple, url,request, params);
				break;
			}
			case put: {
				auto request = pImpl->buildReqHeaders(url, headers);
				pImpl->put(ituple, url,request, params);
				break;
			}
			default:;
		}
	}

	void HttpClient::httpReq(Method method, void_ptr const& ituple, std::string const& url, req_headers const& headers, std::string const& body) {
		auto request = pImpl->buildReqHeaders(url, headers);

		switch (method) {
			case del:
				pImpl->del(ituple, request);
				break;
			case get:
				pImpl->get(ituple, request);
				break;
			case head:
				pImpl->head(ituple, request);
				break;
			case post:
				pImpl->post(ituple, request, body);
				break;
			case put:
				pImpl->put(ituple, request, body);
				break;
			default:;
		}
	}

	void HttpClient::run() {
		pImpl->run();
	}

	void HttpClient::stop() {
		pImpl->stop();
	}
}

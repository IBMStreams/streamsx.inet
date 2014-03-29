//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class HTTPRequest {
	private String url = null;
	private Map<String, String> headers =
			new HashMap<String, String> ();

	public static enum RequestType {GET, POST};
	private RequestType type = RequestType.GET;
	private int readTimeout = 10 * 60 * 1000; // 10 mins
	public HTTPRequest() {
	}

	public HTTPRequest(String url) {
		this.url =  url;
	}

	public void setUrl(String url) {
		this.url =  url;
	}
	public String getUrl() {
		return url;
	}

	public void setHeader(String name, String value) {
		headers.put(name, value);
	}

	public RequestType getType() {
		return type;
	}

	public void setType(RequestType type) {
		this.type = type;
	}

	public int getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
	}

	public HttpURLConnection connect() throws Exception {
		HttpURLConnection conn = HTTPUtils.getNewConnection(url);
		conn.setRequestMethod(type.toString());
		conn.setReadTimeout(readTimeout);
		if(type == RequestType.POST)
			conn.setDoOutput(true);
		if(headers.size() > 0) {
			for(Entry<String, String> entry : headers.entrySet()) {
				conn.setRequestProperty(entry.getKey(), entry.getValue());
			}
		}
		return conn;
	}
	public HTTPResponse sendRequest(IAuthenticate auth) throws Exception {

		return sendRequest(auth, null);
	}


	public HTTPResponse sendRequest(IAuthenticate auth, String data) throws Exception {
		HttpURLConnection conn = auth.sign(this); 

		if(data != null) {
			OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
			writer.write(data);
			writer.flush();
		}

		HTTPResponse resp = new HTTPResponse();
		resp.setResponseCode(conn.getResponseCode());
		resp.getHeaders().putAll(conn.getHeaderFields());

		try {
			resp.setOutStreamData(HTTPUtils.readFromStream(conn.getInputStream()));
		}catch(IOException e) {}

		resp.setErrorStreamData(HTTPUtils.readFromStream(conn.getErrorStream()));
		return resp;
	}

	@Override
	public String toString() {
		return "URL: " + url;
	}
}

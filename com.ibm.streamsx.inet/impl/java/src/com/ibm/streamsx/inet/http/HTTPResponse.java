//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.apache.http.HttpResponse;

class HTTPResponse {
	public static final int HTTP_OK = 200;
	
	private int responseCode = 0;
	private String errorStreamData = null;
	private HttpResponse resp = null;
	public HTTPResponse(HttpResponse resp) {
		this.resp = resp;
		responseCode = resp.getStatusLine().getStatusCode();
		errorStreamData = resp.getStatusLine().getReasonPhrase();
	}

	public int getResponseCode() {
		return responseCode;
	}

	public String getErrorStreamData() {
		return errorStreamData;
	}
	
	public String getContentEncoding() {
		return resp.getEntity().getContentEncoding().getValue();
	}

	public String getOutStreamData() throws IllegalStateException, IOException {
		return HTTPUtils.readFromStream(getInputStream());
	}
	
	public InputStream getInputStream() throws IllegalStateException, IOException {
		InputStream is = resp.getEntity().getContent();
		if(getContentEncoding()!=null) { 
			String enc = getContentEncoding().toLowerCase();
			if(enc.indexOf("gzip") != -1)
				return new GZIPInputStream(is);
			else if(enc.indexOf("deflate") != -1)
				return new InflaterInputStream(is, new Inflater(true));
		}
		return is;
	}

	public String toString() {
		return "HTTPResponse RC[" + responseCode + "]: " + resp.toString();
	}
}

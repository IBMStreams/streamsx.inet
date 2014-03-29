//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HTTPResponse {
	private int responseCode = 0;
	private String errorStreamData, outStreamData = null;
	private Map<String, List<String>> headers = new HashMap<String, List<String>>();
	public HTTPResponse() {
	}

	public int getResponseCode() {
		return responseCode;
	}

	public void setResponseCode(int responseCode) {
		this.responseCode = responseCode;
	}

	public String getErrorStreamData() {
		return errorStreamData;
	}

	public void setErrorStreamData(String errorStreamData) {
		this.errorStreamData = errorStreamData;
	}

	public String getOutStreamData() {
		return outStreamData;
	}

	public void setOutStreamData(String outStreamData) {
		this.outStreamData = outStreamData;
	}

	public Map<String, List<String>> getHeaders() {
		return headers;
	}
	public String toString() {
		return "HTTPResponse RC[" + responseCode + "] "
				+ " ErrMsg[" + errorStreamData + "]"
				+ " DataLength[" + (outStreamData==null ? 0 : outStreamData.length()) + "]";
	}
}

//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HTTPUtils {

	public static HttpURLConnection getNewConnection(String url) 
			throws IOException {
		return (HttpURLConnection)new URL(url).openConnection();
	}

	/**
	 * Reads all the data from the incoming stream and puts the contents in a string.
	 * @param stream
	 * @return
	 */
	public static String readFromStream(InputStream stream) {
		if(stream==null) return null;
		StringBuffer buf = new StringBuffer();
		BufferedReader in = new BufferedReader(new InputStreamReader(stream));
		String inputLine = null;
		try {
			while (((inputLine = in.readLine()) != null)) {
				buf.append(inputLine);
			}
		}catch(IOException e) {
			//ignore
		}
		return buf.toString();
	}

	public static Map<String, String> getHeaderMap(List<String> headers) {
		Map<String, String> headerMap = new HashMap<String, String>(headers.size());
		for(String header : headers) {
			String[] headerParts = header.split(":\\s*", 2);
			String headerName = headerParts[0];
			String headerValue = headerParts[1];
			headerMap.put(headerName, headerValue);
		}
		return headerMap;
	}
}

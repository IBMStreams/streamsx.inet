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

public class HTTPUtils {

	public static HttpURLConnection getNewConnection(String url) 
			throws IOException {
		return (HttpURLConnection)new URL(url).openConnection();
	}

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
}

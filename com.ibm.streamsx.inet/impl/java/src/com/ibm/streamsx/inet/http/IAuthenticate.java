//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

import com.ibm.misc.BASE64Encoder;

public interface IAuthenticate {

	public void setProperties(String propFile, List<String> override) throws IOException ;

	public HttpURLConnection sign(HTTPRequest req) throws Exception ;
}

class BasicAuth extends AAuthenticate {
	private String useridpassword = null;

	@Override
	public void init() {
		useridpassword = 
				getRequiredProperty("userid") 
				+ ":" 
				+ getRequiredProperty("password");
	}

	@Override
	public HttpURLConnection sign(HTTPRequest req) throws Exception {
		BASE64Encoder encoder = new BASE64Encoder();
		String up_encoded = encoder.encode(useridpassword.getBytes());
		req.setHeader("Authorization", "Basic " + up_encoded);
		return req.connect();
	}
}

//no authentication
class NoAuth extends AAuthenticate {

	@Override
	public HttpURLConnection sign(HTTPRequest req) throws Exception {
		return req.connect();
	}

	@Override
	void init() {
		//do nothing
	}

}
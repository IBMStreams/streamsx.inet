//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.net.HttpURLConnection;
import java.util.Properties;

import com.ibm.misc.BASE64Encoder;

public interface IAuthenticate {

	public IAuthenticate setProperties(Properties props) ;

	public HttpURLConnection sign(HTTPRequest req) throws Exception ;
}

class BasicAuth extends AAuthenticate {
	String userid = null, password = null;

	@Override
	public BasicAuth setProperties(Properties props)  {
		super.setProperties(props);
		this.userid = getRequiredProperty("userid");
		this.password = getRequiredProperty("password");
		if(userid == null || userid.isEmpty())
			throw new IllegalArgumentException("A valid userid must be specified");
		if(userid == null || userid.isEmpty())
			throw new IllegalArgumentException("A valid password must be specified");
		return this;
	}

	@Override
	public HttpURLConnection sign(HTTPRequest req) throws Exception {
		if(userid!=null && password!=null && 
				userid.length() > 0 && password.length()>0) {
			BASE64Encoder encoder = new BASE64Encoder();

			String useridpassword = userid + ":" + password;
			String up_encoded = encoder.encode(useridpassword.getBytes());

			req.setHeader("Authorization", "Basic " + up_encoded);
		}
		return req.connect();
	}
}

class NoAuth extends AAuthenticate {

	@Override
	public HttpURLConnection sign(HTTPRequest req) throws Exception {
		return req.connect();
	}

}
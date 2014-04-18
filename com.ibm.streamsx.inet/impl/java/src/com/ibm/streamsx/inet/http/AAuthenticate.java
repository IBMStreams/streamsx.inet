//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Properties;

import com.ibm.misc.BASE64Encoder;

abstract class AAuthenticate implements IAuthenticate {

	protected Properties prop = new Properties();
	
	@Override
	public void setProperties (String authFile, List<String> overrideProps) throws IOException {
		if(authFile != null) {
			prop.load(new FileReader(authFile));
		}
		if(overrideProps != null && overrideProps.size() >0 ) {
			for(String value : overrideProps) {
				String [] arr = value.split("=");
				if(arr.length < 2) 
					throw new IllegalArgumentException("Invalid property: " + value);
				String name = arr[0];
				String v = value.substring(arr[0].length()+1, value.length());
				prop.setProperty(name, v);
			}
		}
		init();
	}
	
	/**
	 * Returns the property value. 
	 * @param name
	 * @throws RuntimeException if a property is not found.
	 * @return
	 */
	public String getRequiredProperty(String name) {
		if(prop == null)
			throw new RuntimeException("No authentication properties specified");
		String ret = prop.getProperty(name);
		if(ret == null || ret.isEmpty())
			throw new RuntimeException("Required property \"" + name + "\" not specified");
		return ret;
	}
	
	/**
	 * Called once all the properties are initialized
	 */
	abstract void init(); 
	
}


/**
 * Signs request with basic authentication
 * "userid" and "password" properties are required.
 *
 */
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

/**
 * No authentication will be done. 
 */
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
//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import javax.xml.bind.DatatypeConverter;

abstract class AAuthenticate implements IAuthenticate {

	protected Properties prop = new Properties();
	
	@Override
	public void setProperties (String authFile, List<String> overrideProps) throws IOException {
		if(authFile != null) {
			prop.load(new FileReader(authFile));
		}
		if(overrideProps != null && overrideProps.size() >0 ) {
			for(String value : overrideProps) {
				int loc = value.indexOf("=");
				if(loc == -1) 
					throw new IllegalArgumentException("Invalid property: " + value);
				String name = value.substring(0, loc);
				String v = value.substring(loc+1, value.length());
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
	public void sign(HTTPRequest req) throws Exception {
		String up_encoded = DatatypeConverter.printBase64Binary(useridpassword.getBytes(StandardCharsets.US_ASCII));
		req.getReq().setHeader("Authorization", "Basic " + up_encoded);
	}
}

/**
 * No authentication will be done. 
 */
class NoAuth extends AAuthenticate {

	@Override
	public void sign(HTTPRequest req) throws Exception {
		//noop
	}

	@Override
	void init() {
		//do nothing
	}
}
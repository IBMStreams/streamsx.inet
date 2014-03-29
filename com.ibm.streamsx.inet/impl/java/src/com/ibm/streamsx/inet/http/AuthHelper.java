//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.util.Properties;

public class AuthHelper {

	public static IAuthenticate getAuthenticator(String name, Properties prop) {
		if(name == null)
			throw new NullPointerException("Invalid null value specified for authentication type");
	
		String nlc = name.toLowerCase();
		
		IAuthenticate auth = null;
		if(nlc.equals("none"))
			auth = new NoAuth();
		else if(nlc.equals("basic"))
			auth = new BasicAuth();
		
		if(auth != null) {
			auth.setProperties(prop);
			return auth;
		}
		throw new IllegalArgumentException("Unrecognized value \"" 
					+ name + "\" specified for authentication type");
	}
	
}

//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates HTTP requests using the appropriate mechanism
 *
 */
interface IAuthenticate {
	/**
	 * This method will be invoked once before the first invocation of the "sign" method
	 * @param propFile Properties file name containing the authentication properties
	 * @param override List of override properties in the "name=value" format.
	 * @throws IOException
	 */
	public void setProperties(String propFile, List<String> override) throws IOException ;

	
	/**
	 * Signs request using the authentication mode, connects to the endpoint and returns the connection
	 * @param req HTTP request object to be signed
	 * @throws Exception
	 */
	public void sign(HTTPRequest req) throws Exception ;
	
}


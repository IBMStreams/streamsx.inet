//
// *******************************************************************************
// * Copyright (C)2018, International Business Machines Corporation.
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

/**
 * HTTP 1.1 supported methods.
 *
 */
public enum HTTPMethod {
	OPTIONS,
	GET,
	HEAD,
	POST,
	PUT,
	PATCH,
	DELETE,
	TRACE,
	//CONNECT,
	NONE
	;
}
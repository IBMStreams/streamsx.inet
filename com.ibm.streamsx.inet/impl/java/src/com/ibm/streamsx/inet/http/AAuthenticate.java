//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.util.Properties;

abstract class AAuthenticate implements IAuthenticate {

	protected Properties prop = null;
	
	@Override
	public IAuthenticate setProperties (Properties props) {
		prop = props;
		return this;
	}
	
	String getRequiredProperty(String name) {
		if(prop == null)
			throw new IllegalArgumentException("No authentication properties spcified");
		String ret = prop.getProperty(name);
		if(name == null)
			throw new IllegalArgumentException("Required property \"" + name + "\" not specified");
		return ret;
	}
	
	
}

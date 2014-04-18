//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

abstract class AAuthenticate implements IAuthenticate {

	protected Properties prop = new Properties();
	
	@Override
	public void setProperties (String authFile, List<String> overrideProps) throws IOException {
		if(authFile != null) {
			prop.load(new FileReader(authFile));
		}
		if(overrideProps.size() >0 ) {
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
	
	String getRequiredProperty(String name) {
		if(prop == null)
			throw new IllegalArgumentException("No authentication properties specified");
		String ret = prop.getProperty(name);
		if(ret == null || ret.isEmpty())
			throw new IllegalArgumentException("Required property \"" + name + "\" not specified");
		return ret;
	}
	
	//called once all properties are initialized
	abstract void init(); 
	
}

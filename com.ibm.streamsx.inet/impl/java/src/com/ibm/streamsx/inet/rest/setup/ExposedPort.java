/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.setup;

import java.util.ArrayList;
import java.util.List;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.StreamingData;
import com.ibm.streams.operator.StreamingInput;

/**
 * Represents meta-data for a port and its URLs.
 */
public class ExposedPort {
	
	private final OperatorContext context;
	private final StreamingData port;
	private final String portsContext;
	private final JSONObject contextPaths;
	
	public ExposedPort(OperatorContext context,
			StreamingData port,
			String portsContext) {
		super();
		this.context = context;
		this.port = port;
		this.portsContext = portsContext;
		contextPaths = new JSONObject();
		
		// Automatically add in the generated port info links
		addURL("info",
				(port instanceof StreamingInput ? "/input/" : "/output/") +
		port.getPortNumber() + "/info");
	}
	
	public OperatorContext getOpContext() { return context;}
	public StreamingData getPort() {return port;}
	
	void addURL(String key, String path) {
		contextPaths.put(key, portsContext + path);
	}
	
	public JSONObject getSummary() {
		
		JSONObject summary = new JSONObject();
		
		summary.put("operatorName", context.getName());
		summary.put("operatorKind", context.getKind());
		summary.put("portName", port.getName());		
		summary.put("contextPaths", contextPaths);
		
		return summary;		
	}
}

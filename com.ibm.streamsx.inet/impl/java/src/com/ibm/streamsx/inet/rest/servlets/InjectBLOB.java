/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017
*/
package com.ibm.streamsx.inet.rest.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.types.ValueFactory;
import com.ibm.streams.operator.types.Blob;
import com.ibm.streams.operator.types.RString;

/**
 * Inject a tuple into an output port from a HTTP GET or POST.
 *
 */
public class InjectBLOB extends HttpServlet {
	

	private static final long serialVersionUID = 393023456650913744L;
	
	private final StreamingOutput<OutputTuple> port;
	
	public InjectBLOB(StreamingOutput<OutputTuple> port) {
		this.port = port;
	}
	
    @Override
    public void doPost(HttpServletRequest request,
		       HttpServletResponse response)
	throws ServletException, IOException
 {
	
	final String contentType =  request.getContentType();
	if (contentType != null && ("application/octet-stream".equals(contentType) || contentType.startsWith("application/octet-stream;")))
		;
	else
		throw new ServletException("Content type is required to be application/octet-stream, is: " + contentType);
	OutputTuple tuple = port.newTuple();
	InputStream in = request.getInputStream();
	Blob blob = ValueFactory.readBlob(in);
	tuple.setBlob(0, blob) ;

	// Output tuple may optionally have a second attribute for receiving all the HTTP request headers.
	// If this second tuple attribute is present, its type must be map<rstring, rstring>.
	// If the type matches, we will assign the HTTP request headers into this map attribute. Otherwise, we will skip this step.
	if ((port.getStreamSchema().getAttributeCount() > 1) && 
	    (port.getStreamSchema().getAttribute(1).getType().getLanguageType().equals("map<rstring,rstring>") == true)) {
		// Output tuple for this operator has an optional second attribute.
		// Collect the HTTP Request headers and assign them to the second attribute which is a map<rstring, rstring>.
		Map<RString, RString> map = new HashMap<RString, RString>();
		Enumeration headerNames = request.getHeaderNames();

		while (headerNames.hasMoreElements()) {
			String str = (String)headerNames.nextElement();
			RString key = new RString(str);
			RString value = new RString((String)request.getHeader(str));
			map.put(key, value);
			// System.out.println("Key=" + key + ", Value=" + value);
		}

		tuple.setMap(1, map);	
	}

	try {
		port.submit(tuple);
	} catch (Exception e) {
		throw new IOException(e);
	}	
	in.close();
	response.setStatus(HttpServletResponse.SC_NO_CONTENT);
 }
}

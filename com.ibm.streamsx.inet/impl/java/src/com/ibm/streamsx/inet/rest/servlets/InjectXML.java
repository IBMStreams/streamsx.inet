/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014
*/
package com.ibm.streamsx.inet.rest.servlets;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.types.ValueFactory;
import com.ibm.streams.operator.types.XML;

/**
 * Inject a tuple into an output port from a HTTP GET or POST.
 *
 */
public class InjectXML extends HttpServlet {
	

	private static final long serialVersionUID = 193023456650913733L;
	
	private final StreamingOutput<OutputTuple> port;
	
	public InjectXML(StreamingOutput<OutputTuple> port) {
		this.port = port;
	}
	
    @Override
    public void doPost(HttpServletRequest request,
		       HttpServletResponse response)
	throws ServletException, IOException
 {
	
	final String contentType =  request.getContentType();
	if (contentType != null && ("application/xml".equals(contentType) || contentType.startsWith("application/xml;")))
		;
	else
		throw new ServletException("Content type is required to be application/xml, is: " + contentType);
	OutputTuple tuple = port.newTuple();
	InputStream in = request.getInputStream();
	XML xml = ValueFactory.newXML(in);
	tuple.setXML(0, xml) ;
	try {
		port.submit(tuple);
	} catch (Exception e) {
		throw new IOException(e);
	}	
	in.close();
	response.setStatus(HttpServletResponse.SC_NO_CONTENT);
 }
}

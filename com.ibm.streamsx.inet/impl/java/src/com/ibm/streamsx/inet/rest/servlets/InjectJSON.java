/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014
*/
package com.ibm.streamsx.inet.rest.servlets;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;

/**
 * Inject a tuple into an output port from a HTTP GET or POST
 * using application/json mime type.
 *
 */
public class InjectJSON extends SubmitterServlet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3189619562362906581L;
	
	private final int attributeIndex;
	
	public InjectJSON(OperatorContext context, StreamingOutput<OutputTuple> port) {
	    super(context, port);

		Attribute jsonString = port.getStreamSchema().getAttribute("jsonString");
		if (jsonString != null)
			attributeIndex = jsonString.getIndex();
		else
			attributeIndex = 0;
	}
	
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		final String contentType = request.getContentType();
		if (contentType != null
				&& ("application/json".equals(contentType) || contentType
						.startsWith("application/json;")))
			;
		else
			throw new ServletException(
					"Content type is required to be application/json, is: "
							+ contentType);

		// Read the entire content as a String which will be the JSON.
		StringBuilder sb = new StringBuilder(request.getContentLength());
		char[] chars = new char[4 * 1024];
		BufferedReader br = request.getReader();
		for (;;) {
			int n = br.read(chars);
			if (n == -1)
				break;
			sb.append(chars, 0, n);
		}
		br.close();

		OutputTuple tuple = getPort().newTuple();
		tuple.setString(attributeIndex, sb.toString());
		submit(tuple);
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}
}

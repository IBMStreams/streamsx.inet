/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.servlets;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.StreamingData;

/**
 * Returns a JSON object that describes the port's schema.
 *
 */
public class PortInfo extends HttpServlet {
		
	/**
	 * 
	 */
	private static final long serialVersionUID = -4211423812893544972L;
	
	
	private final OperatorContext context;
	private final StreamingData port;
	private final long lastModified = System.currentTimeMillis();

	public PortInfo(OperatorContext context, StreamingData port) {
		this.context = context;
		this.port = port;
	}
	
	public long getLastModified() {
		return lastModified;
	}


	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		
		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json");
		PrintWriter out = response.getWriter();
		response.setStatus(HttpServletResponse.SC_OK);
		
		JSONObject info = new JSONObject();
		info.put("operatorName", context.getName());
		info.put("portName", port.getName());
		
		JSONArray attributes = new JSONArray();
		for (Attribute attr : port.getStreamSchema()) {
			JSONObject jattr = new JSONObject();
			jattr.put("name", attr.getName());
			jattr.put("type", attr.getType().getLanguageType());
			attributes.add(jattr);
		}
		info.put("attributes", attributes);
		out.println(info.serialize());
		out.flush();
		out.close();
	}
}

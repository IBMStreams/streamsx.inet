/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.streamsx.inet.rest.setup.ExposedPort;

public class ExposedPortsInfo extends HttpServlet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -6656221195619463166L;
	private final List<ExposedPort> ports;
	
	public ExposedPortsInfo(List<ExposedPort> ports) {
		this.ports = ports;
	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
				
		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json");
		PrintWriter out = response.getWriter();
		response.setStatus(HttpServletResponse.SC_OK);
		
		JSONObject jresp = new JSONObject();
		JSONArray jports = new JSONArray();
		for (ExposedPort ep : ports) {
			jports.add(ep.getSummary());
		}
		jresp.put("exposedPorts", jports);
		out.println(jresp.serialize());
		out.flush();
		out.close();
	}
}

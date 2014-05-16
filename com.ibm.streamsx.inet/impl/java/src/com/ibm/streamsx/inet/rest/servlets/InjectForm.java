/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2012 
*/
package com.ibm.streamsx.inet.rest.servlets;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.StreamingOutput;

public class InjectForm extends HttpServlet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2710824663219737428L;

	private final StreamingOutput<?> port;
	
	public InjectForm(StreamingOutput<?> port) {
		this.port = port;
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_OK);
		out.println("<html>");
		out.println("<head>");
		out.print("<title>");
		out.print(port.getName());
		out.print("</title>");
		out.println("</head>");
		out.println("<body>");
		out.println("<H1>" + port.getName() + "</H1>");
		String action = request.getRequestURI().replace("form", "inject");
		out.println("<FORM METHOD=POST ACTION=\"" + action + "\">");
		for (Attribute attr : port.getStreamSchema()) {
			out.print(attr.getName());
			out.println(": <input type=text size=30 value=\"\" name=" + attr.getName() + "><br/>");		
		}
		out.println("<input type=submit value=\"Inject Tuple\">");
		
		out.println("</body>");

		out.flush();
		out.close();
	}
}

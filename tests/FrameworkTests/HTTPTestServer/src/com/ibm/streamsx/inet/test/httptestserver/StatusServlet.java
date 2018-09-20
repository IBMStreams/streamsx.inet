package com.ibm.streamsx.inet.test.httptestserver;

import java.io.IOException;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StatusServlet extends HttpServlet {
	private static final long serialVersionUID = 7033072740399373757L;
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("GET: " + request.getRequestURI());
		doIt(request, response);
	}
	protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("HEAD: " + request.getRequestURI());
		doIt(request, response);
	}
	
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("PUT: " + request.getRequestURI());
		doIt(request, response);
	}
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("POST: " + request.getRequestURI());
		doIt(request, response);
	}
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("DELETE: " + request.getRequestURI());
		doIt(request, response);
	}

	private void doIt(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String uri = request.getRequestURI();
		StringTokenizer st = new StringTokenizer(uri, "/");
		if ( st.countTokens() != 2 ) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "url must have 2 components");
			return;
		}
		st.nextToken();
		String numberString = st.nextToken();
		try {
			Integer no = Integer.parseInt(numberString);
			response.sendError(no);
		} catch (java.lang.NumberFormatException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "must be a number : " + numberString);
		}
	}

}

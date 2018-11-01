package com.ibm.streamsx.inet.test.httptestserver;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ResourceServlet extends HttpServlet {
	private static final long serialVersionUID = -1923445723671885394L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (validateAccessToken(request)) {
			printOkNoBody(request, response);
		
		} else {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}
	
	@Override
	protected void doHead (HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (validateAccessToken(request)) {
			printOkNoBody(request, response);
		} else {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}

	@Override
	protected void doDelete (HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (validateAccessToken(request)) {
			printOkNoBody(request, response);
		} else {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}

	@Override
	protected void doPost (HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (validateAccessToken(request)) {
			printOkWithBody(request, response);
		} else {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}
	
	@Override
	protected void doPut (HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (validateAccessToken(request)) {
			printOkWithBody(request, response);
		} else {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}

	@Override
	protected void doOptions (HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (validateAccessToken(request)) {
			printOkNoBody(request, response);
		} else {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}

	static private void printOkNoBody(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("text/html");
		PrintWriter pw = response.getWriter();
		pw.print(Utils.printFormStart());
		pw.println("<h1>ResourceServlet</h1>");
		pw.print(Utils.printRequestInfo(request, response));
		pw.print(Utils.printRequestParameters(request, response));
		pw.print(Utils.printAllHeades(request, response));
		pw.print(Utils.printFormEnd());
	}
	
	static private void printOkWithBody (HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("text/html");
		PrintWriter pw = response.getWriter();
		pw.print(Utils.printFormStart());
		pw.println("<h1>ResourceServlet</h1>");
		pw.print(Utils.printRequestInfo(request, response));
		pw.print(Utils.printAllHeades(request, response));
		pw.print(Utils.printRequestBody(request, response));
		pw.print(Utils.printFormEnd());
	}
	
	static private boolean validateAccessToken(HttpServletRequest request) {
		boolean authenticated = false;
		String tok = request.getHeader("Authorization");
		if (tok != null) {
			if (tok.equals("Bearer 2YotnFZFEjr1zCsicMWpAA")) {
				authenticated = true;
			}
		}
		return authenticated;
	}
}

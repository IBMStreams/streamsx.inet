package com.ibm.streamsx.inet.test.httptestserver;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Oauth2Servlet extends HttpServlet {
	private static final long serialVersionUID = 5721236571744897028L;

	@Override
	protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_OK);
		PrintWriter pw = response.getWriter();
		pw.println(Utils.printFormStart());
		Principal principal = request.getUserPrincipal();
		String user = principal.getName();
		String authType = request.getAuthType();
		pw.println("<h2>Oauth2Servlet</h2>\n<p>\n");
		pw.println("authenticated: true</br>\n");
		pw.println("user: " + user + "</br>\n");
		pw.println("authentication scheme: " + authType + "</br>\n");
		pw.println("uri: " + request.getRequestURI() + "</br></p>\n");
		pw.print(Utils.printFormEnd());
	}
	
	@Override
	protected void doPost (HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(HttpServletResponse.SC_OK);
		PrintWriter pw = response.getWriter();
		pw.println("{");
		pw.println("	\"access_token\":\"2YotnFZFEjr1zCsicMWpAA\",");
		pw.println("	\"token_type\":\"example\",");
		pw.println("	\"expires_in\":3600,");
		pw.println("	\"example_parameter\":\"example_value\"");
		pw.println("}");
	}

}

package com.ibm.streamsx.inet.test.httptestserver;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HelloServlet extends HttpServlet {

	private static final long serialVersionUID = 7557117910632437226L;
	
	@Override
	protected void doGet( HttpServletRequest request, HttpServletResponse response )
			throws ServletException, IOException {
		
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_OK);
		PrintWriter pw = response.getWriter();
		pw.println(Utils.printFormStart());
		pw.println("<h1>Hello from HelloServlet</h1>");
		pw.print(Utils.printRequestInfo(request, response));
		pw.print(Utils.printAllHeades(request, response));
		pw.print(Utils.printRequestParameters(request, response));
		pw.print(Utils.printFormEnd());
	}

}

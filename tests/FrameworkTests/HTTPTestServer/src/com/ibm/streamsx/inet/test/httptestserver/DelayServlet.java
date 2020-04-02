package com.ibm.streamsx.inet.test.httptestserver;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DelayServlet extends HttpServlet {
	private static final long serialVersionUID = 7033072740399373757L;
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("GET: " + request.getRequestURI());
		doIt(request, response, true);
	}
	protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("HEAD: " + request.getRequestURI());
		doIt(request, response, false);
	}
	
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("PUT: " + request.getRequestURI());
		doIt(request, response, false);
	}
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("POST: " + request.getRequestURI());
		doIt(request, response, false);
	}
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("DELETE: " + request.getRequestURI());
		doIt(request, response, false);
	}

	private void doIt(HttpServletRequest request, HttpServletResponse response, boolean bodyresponse) throws IOException {
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
			if (no > 1800) {
				no = 1800;
				numberString = "1800";
				System.out.println("Delay limitted to 1800 sec");
			}
			long delay = no * 1000;
			Date date = new Date();
			long starttime = date.getTime();
			long now = starttime;
			while((now - starttime) < delay) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					System.out.println("InterruptedException");
				}
				Date date2 = new Date();
				now = date2.getTime();
			}
			System.out.println("End delay " + new Integer(no).toString());
			if (bodyresponse)
				printOkWithBody(request, response, numberString);
			else
				printOkNoBody(request, response, numberString);
			
		} catch (java.lang.NumberFormatException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "must be a number: " + numberString);
		}
	}

	static private void printOkNoBody(HttpServletRequest request, HttpServletResponse response, String delay) throws IOException {
		response.setStatus(HttpServletResponse.SC_OK);
	}
	
	static private void printOkWithBody (HttpServletRequest request, HttpServletResponse response, String delay) throws IOException {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("text/html");
		PrintWriter pw = response.getWriter();
		pw.print(Utils.printFormStart());
		pw.println("<h1>DelayServlet</h1>");
		pw.println("<p>Delayed : " + delay + " seconds.</p>");
		pw.print(Utils.printRequestInfo(request, response));
		pw.print(Utils.printAllHeades(request, response));
		pw.print(Utils.printRequestBody(request, response));
		pw.print(Utils.printFormEnd());
	}

}

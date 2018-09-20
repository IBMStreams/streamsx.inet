package com.ibm.streamsx.inet.test.httptestserver;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RedirectToServlet extends HttpServlet {
	private static final long serialVersionUID = 6213815576864558382L;
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		doRedirect(request, response);
	}
	@Override
	protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
		doRedirect(request, response);
	}
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		doRedirect(request, response);
	}
	@Override
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
		doRedirect(request, response);
	}
	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
		doRedirect(request, response);
	}
	/* 
	 * Return the new newUrl for redirection
	 * if newUrl is empty an error has occured
	 */
	private void doRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
		Enumeration<String> params = request.getParameterNames();
		int count = 0;
		String newUrl = null;
		String statusCode = null;
		while (params.hasMoreElements()) {
			count++;
			String param = params.nextElement();
			String val = request.getParameter(param);
			System.out.println(param + ": " + val);
			if (param.equalsIgnoreCase("url")) {
				newUrl = val;
			} else if (param.equalsIgnoreCase("status_code")) {
				statusCode = val;
			}
		}
		//System.out.println(request.getMethod());
		//System.out.println(count);
		//System.out.println(newUrl);
		//System.out.println(statusCode);
		if ((count != 2) || (newUrl == null) || (statusCode == null)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "uri must have 2 queries url and status_code");
			return;
		}
		try {
			Integer status = Integer.parseInt(statusCode);
			response.setStatus(status);
			response.addHeader("Location", response.encodeRedirectURL(newUrl));
		} catch (java.lang.NumberFormatException e) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "must be a number : " + statusCode);
		}
		return;
	}
}

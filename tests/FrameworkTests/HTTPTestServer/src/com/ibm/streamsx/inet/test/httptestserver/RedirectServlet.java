package com.ibm.streamsx.inet.test.httptestserver;

import java.io.IOException;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RedirectServlet extends HttpServlet {
	private static final long serialVersionUID = -6220106804631858671L;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doRedirect(request, response, request.getMethod());
    }
    
    @Override
    protected void doHead (HttpServletRequest request, HttpServletResponse response) throws IOException {
        doRedirect(request, response, request.getMethod());
    }

    @Override
    protected void doDelete (HttpServletRequest request, HttpServletResponse response) throws IOException {
        doRedirect(request, response, request.getMethod());
    }

    @Override
    protected void doPost (HttpServletRequest request, HttpServletResponse response) throws IOException {
        doRedirect(request, response, request.getMethod());
    }
    
    @Override
    protected void doPut (HttpServletRequest request, HttpServletResponse response) throws IOException {
        doRedirect(request, response, request.getMethod());
    }

    @Override
    protected void doOptions (HttpServletRequest request, HttpServletResponse response) throws IOException {
        doRedirect(request, response, request.getMethod());
    }
    
    static void doRedirect(HttpServletRequest request, HttpServletResponse response, String meth) throws IOException {
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
            Integer next = no - 1;
            //Content-Type: text/html; charset=utf-8\r\n
            //Location: /get\r\n
            //Location: /relative-redirect/1\r\n
            if (next.intValue() <= 0) {
                StringBuilder newUri = new StringBuilder("/");
                newUri.append(meth.toLowerCase());
                response.sendRedirect(newUri.toString());
            } else {
                response.sendRedirect("/redirect/" + next.toString());
            }
        } catch (java.lang.NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "must be a number : " + numberString);
        }
    }

}

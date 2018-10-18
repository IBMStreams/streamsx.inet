package com.ibm.streamsx.inet.test.httptestserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

class Utils {
    static String printFormStart() {
        return "<html>\n<body>\n";
    }
    
    static String printFormEnd() {
        return "</body>\n</html>\n";
    }
    
    static String printAllHeades(HttpServletRequest request, HttpServletResponse response) {
        String res = "<h2>headers</h2>\n<p>\n";
        for (Enumeration<String> headers = request.getHeaderNames(); headers.hasMoreElements();) {
            String header = headers.nextElement();
            res = res + header + ": " + request.getHeader(header) + "<br>\n";
        }
        res = res + "</p>\n";
        return res;
    }
    
    static String printRequestInfo(HttpServletRequest request, HttpServletResponse response) {
        String method = request.getMethod();
        String scheme = request.getScheme();
        String uri = request.getRequestURI();
        StringBuffer url = request.getRequestURL();
        String pathInfo = request.getPathInfo();
        String contextPath = request.getContextPath();
        String queryString = request.getQueryString();
        String protocol = request.getProtocol();
        String remoteAddr = request.getRemoteAddr();
        String remoteHost = request.getRemoteHost();

        String res = "<h2>request info</h2>\n<p>\n";
        res = res + "method: " + method + "</br>\n";
        res = res + "scheme: " + scheme + "</br>\n";
        res = res + "uri: " + uri + "</br>\n";
        res = res + "url: " + url.toString() + "</br>\n";
        res = res + "pathInfo: " + pathInfo + "</br>\n";
        res = res + "contextPath: " + contextPath + "</br>\n";
        res = res + "queryString: " + queryString + "</br>\n";
        res = res + "protocol: " + protocol + "</br>\n";
        res = res + "remoteAddr: " + remoteAddr + "</br>\n";
        res = res + "remoteHost: " + remoteHost + "</br>\n";
        res = res + "</p>\n";
        return res;
    }
    
    static String printRequestParameters (HttpServletRequest request, HttpServletResponse response) {
        String res = "<h2>parameters</h2>\n<p>\n";
        Enumeration<String> params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String param = params.nextElement();
            res = res + param + ": " + request.getParameter(param) + "</br>\n";
        }
        res = res + "</p>\n";
        return res;
    }
    
    static String printRequestBody (HttpServletRequest request, HttpServletResponse response) throws IOException {
        StringBuilder sb = new StringBuilder("<h2>body</h2><p>");
        BufferedReader reader = request.getReader();
        String line = reader.readLine();
        while (line != null) {
            sb.append(line).append("\n");
            line = reader.readLine();
        }
        sb.append("</p>\n");
        return sb.toString();
    }
    
    static String printPartNames (HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String res = "<h2>part names</h2>\n<p>\n";
        Collection<Part> parts = request.getParts();
        Iterator<Part> it = parts.iterator();
        while (it.hasNext()) {
            Part pp = it.next();
            res = res + pp.getName() + "</br>\n";
        }
        res = res + "</p>\n";
        return res;
    }

}

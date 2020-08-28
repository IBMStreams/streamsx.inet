package com.ibm.streamsx.inet.test.httptestserver;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MethodServlet extends HttpServlet {
    private static final long serialVersionUID = 7021716939814112013L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getRequestURI().equalsIgnoreCase("/get")) {
            printOkNoBody(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }
    
    @Override
    protected void doHead (HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getRequestURI().equalsIgnoreCase("/head")) {
            printOkNoBody(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    protected void doDelete (HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getRequestURI().equalsIgnoreCase("/delete")) {
            printOkNoBody(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    protected void doPost (HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getRequestURI().equalsIgnoreCase("/post")) {
            if (request.getContentType().equals("application/octet-stream")) {
                int len = request.getContentLength();
                if (len != 2048) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                } else {
                    ServletInputStream stream = request.getInputStream();
                    int mybyte;
                    boolean requestOk = false;
                    int byteschecked = 0;
                    do {
                        mybyte = stream.read();
                        if (mybyte == -1) {
                            if (byteschecked == 2048) {
                                requestOk = true;
                            }
                            break;
                        }
                        if (mybyte != (byteschecked % 256)) {
                            break;
                        }
                        byteschecked++;
                    } while (mybyte != -1);
                    if (requestOk) {
                        printOkNoBody(request, response);
                    } else {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    }
                }
            } else {
                printOkWithBody(request, response);
            }
        } else {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }
    
    @Override
    protected void doPut (HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getRequestURI().equalsIgnoreCase("/put")) {
            if (request.getContentType().equals("application/octet-stream")) {
                int len = request.getContentLength();
                if (len != 2048) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                } else {
                    ServletInputStream stream = request.getInputStream();
                    int mybyte;
                    boolean requestOk = false;
                    int byteschecked = 0;
                    do {
                        mybyte = stream.read();
                        if (mybyte == -1) {
                            if (byteschecked == 2048) {
                                requestOk = true;
                            }
                            break;
                        }
                        if (mybyte != (byteschecked % 256)) {
                            break;
                        }
                        byteschecked++;
                    } while (mybyte != -1);
                    if (requestOk) {
                        printOkNoBody(request, response);
                    } else {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    }
                }
            } else {
                printOkWithBody(request, response);
            }
        } else {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    protected void doOptions (HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getRequestURI().equalsIgnoreCase("/options")) {
            printOkNoBody(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    static private void printOkNoBody(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter pw = response.getWriter();
        pw.print(Utils.printFormStart());
        pw.println("<h1>MethodServlet</h1>");
        pw.print(Utils.printRequestInfo(request, response));
        pw.print(Utils.printRequestParameters(request, response));
        pw.print(Utils.printAllHeades(request, response));
        pw.print(Utils.printFormEnd());
    }
    
    static private void printOkWithBody (HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter pw = response.getWriter();
        pw.print(Utils.printFormStart());
        pw.println("<h1>MethodServlet</h1>");
        pw.print(Utils.printRequestInfo(request, response));
        pw.print(Utils.printAllHeades(request, response));
        pw.print(Utils.printRequestBody(request, response));
        pw.print(Utils.printFormEnd());
    }
}

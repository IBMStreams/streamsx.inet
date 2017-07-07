/**
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017 
*/
package com.ibm.streamsx.inet.rest.servlets;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.eclipse.jetty.continuation.Continuation;

import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.Tuple;
import com.ibm.streamsx.inet.rest.ops.RequestProcess;

/** Bridge between the WWW request, Streams processing and corresponding WWW response. 
* 
* 
*/ 
public class ReqWebMessage {
	static Logger trace = Logger.getLogger(InjectWithResponse.class.getName());

	public final long trackingKey;	
	
	private static AtomicLong trackingKeyGenerator = new AtomicLong();
	public Tuple tuple = null;
	public String requestFromWeb;
	private String method = null;
	private String contextPath = null;
	private String pathInfo = null;
	private String queryString = null;
	private String requestUrl = null;
	private String contentType = null;
	private HttpServletRequest request = null;
	StringBuffer readerSb = null;
	private final InjectWithResponse handler;

	private Hashtable<String, String> headers = new Hashtable<String, String>();
	private Continuation continuation;
	/*
	 * tracking key, tracking each request, becomes the key on Streams side
	 * where it's used to correlate the request and response.
	 */

	private String responseFromStreams = "";
	public static final String defaultResponseContentType = "text/html; charset=utf-8";
	private String responseContentType = defaultResponseContentType; 
	
	private Map<String, String> responseHeaders = new Hashtable<String, String>();
	
	public int statusCode = HttpServletResponse.SC_OK;
	private String statusMessage = null;	

	public ReqWebMessage(InjectWithResponse handler, HttpServletRequest request) {
		this.handler = handler;
		this.trackingKey = trackingKeyGenerator.incrementAndGet();
		this.request = request;
		getPayload();
		dumpPayload();
	}
	
	void dumpPayload() {
		if(trace.isInfoEnabled()) {
			trace.info("DUMP REQUEST tracking key:" + trackingKey + " ");
			for (String key : headers.keySet()) {
				trace.info("-- header : key: " + key + " value:" + headers.get(key));
			}
			trace.info(String.format(
				" -- method:%s -- context Path:%s -- pathInfo:%s -- requestUrl:%s -- contentType:%s",
				method, contextPath, pathInfo, requestUrl, contentType));
			trace.info(String.format(" -- queryString:%s -- readerInput: %s", queryString, readerSb.toString()));
			trace.info(String.format(" -- requestPayload:%s", getRequestPayload()));
		}
	}
	// Request components
	private void getPayload() {
		method = request.getMethod();
		contentType = request.getContentType();
		contextPath = request.getContextPath();
		pathInfo = request.getPathInfo();
		requestUrl = request.getRequestURL().toString();
		queryString = request.getQueryString();
		readerSb = new StringBuffer();
		try {
			Stream<String> stream = request.getReader().lines();
			stream.forEach(item -> readerSb.append(item));
			stream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println("Error reading request of context path : " + contextPath);
			e.printStackTrace();
		}

		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			headers.put(name, request.getHeader(name));
		}
	}
	// Request components 
	// 
	public String getRequestPayload() {
		if (queryString != null) {
			return queryString;
		} else if (readerSb != null) {
			return readerSb.toString();
		}
		return requestFromWeb == null ? "" : requestFromWeb;
	}
	public String getContentType() {
		return (this.contentType == null ? "" : this.contentType); 
	}
	public String getMethod() {
		return method == null ? "" : method;
	}
	public String getPathInfo() {
		return pathInfo == null ? "" : pathInfo;
	}
	public String getRequestUrl() {
		return requestUrl == null ? "" : requestUrl;
	}
	public Hashtable<String, String> getHeaders() {
	        return headers;
	}
	// Build the Json request from all the components. 
	public String jsonRequest() {
	
		JSONObject jsonObj = new JSONObject();

		jsonObj.put(RequestProcess.defaultKeyAttributeName, this.trackingKey);
		jsonObj.put(RequestProcess.defaultMethodAttributeName, this.getMethod());
		jsonObj.put(RequestProcess.defaultContentTypeAttributeName, this.getContentType());
		jsonObj.put(RequestProcess.defaultContextPathAttributeName,  this.contextPath);
		jsonObj.put(RequestProcess.defaultPathInfoAttributeName, this.getPathInfo());
		jsonObj.put(RequestProcess.defaultRequestAttributeName, this.getRequestPayload());
		jsonObj.put(RequestProcess.defaultUrlAttributeName, this.requestUrl);
		if (JSONObject.isValidObject(this.getHeaders())) {
			jsonObj.put(RequestProcess.defaultHeaderAttributeName, this.getHeaders());			
		} else {
		        //Invalid for JSON (Hashtable), switch over to jsonObject
			JSONObject jsonHead = new JSONObject();
			jsonHead.putAll(this.getHeaders());
			jsonObj.put(RequestProcess.defaultHeaderAttributeName, jsonHead);									
		}
		return(jsonObj.toString());
	} 
	
	
	// Jetty concept 
	public void setContinuation(Continuation continuation) {
		this.continuation = continuation;
	}
	public Continuation getContinuation() {
		return this.continuation;
	}
	
	// Response components - setting
	//
	public void setResponse(String response) {
		this.responseFromStreams = response;
	}
	public void setResponseHeaders(Map<String, String> responseHeaders) {
		this.responseHeaders = responseHeaders;
	}
	public void setResponseContentType(String responseContentType) {
		this.responseContentType = responseContentType;
	}
	public void setStatusCode(Integer statusCode) {
		if (statusCode != null) {
			trace.info("setStatusCode :: status code:" + statusCode);
			this.statusCode = statusCode;
		} else {
			trace.info("setStatusCode :: NULL mapped to 0");
			this.statusCode = 0;
			
		}
	}
	public void setStatusMessage(String statusMessage) {
		this.statusMessage = statusMessage;
	}	
	// response - getting
	public String getStatusMessage() {
		return (this.statusMessage);
	}	
	public Map<String, String> getResponseHeaders() {
		return (this.responseHeaders);
	}
	public String getResponse() {
		return this.responseFromStreams;
	}
	public String getResponseContentType() {
		return this.responseContentType == null ? defaultResponseContentType : this.responseContentType;
	}
	public boolean isErrorStatus() {
		trace.info("isErrorCode:" + this.statusCode);
		return (this.statusCode >= HttpServletResponse.SC_BAD_REQUEST); 
	}
	public int statusCode() {
		return this.statusCode < HttpServletResponse.SC_CONTINUE ? HttpServletResponse.SC_OK :this.statusCode;
	}

	/**
	 * Issue the respnse back to the requester (through the servlet & continuation).
	 */
	public void issueResponseFromStreams() {
		handler.asyncResume(this);
	}
}

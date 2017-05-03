package com.ibm.streamsx.inet.rest.ops;
/**
* Licensed Materials - Property of IBM
* Copyright IBM Corp. 2017
* @author mags
*/
import org.apache.log4j.Logger;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;


public class ReqHandlerSuspend extends AbstractHandler {
	static Logger trace = Logger.getLogger(ReqHandlerSuspend.class.getName());
	String greeting;
	String body;
	
	// Number of timeouts that have occurred. Used on timeout error message, attempting to 
	// hint where a possible problem is occurring. 
	public static int timeoutCount = 0;

	interface Constant {
		public static final String EXCHANGEWEBMESSAGE = "exchangeWebMessage";
	}

	ReqWebServer exchangeWebServer = null;

	// Integer trackingKey = 0;

	public ReqHandlerSuspend(ReqWebServer exchangeWebServer) {
		this.exchangeWebServer = exchangeWebServer;
	}

	/**
	 * Callback from Streams side with the results, the results are in the from
	 * streams.
	 * 
	 * @param ewm
	 */
	void asyncResume(ReqWebMessage ewm) {
		trace.info("asyncResume - Web to transmit Stream response trackingKey:" + ewm.trackingKey);

		Continuation resuming = ewm.getContinuation();
		if (!resuming.isSuspended()) {
			trace.warn("asyncResume - WEB NOT suspended, possible timeout, data not returned to requestor. trackingKey "
					+ ewm.trackingKey);
			String state = "";
			if (resuming.isExpired()) {
				state += "isExpired ";
			}
			if (resuming.isInitial()) {
				state += "isInitial ";
			}
			if (resuming.isResponseWrapped()) {
				state += "isWrapped ";
			}
			if (resuming.isSuspended()) {
				state += "isSuspended ";
			}
			trace.warn("asyncResume WEB NOT suspending trackinKey:" + ewm.trackingKey + " current state(s) : " + state);
			return;
		}
		trace.info("asyncResume - WEB connection resuming, trackingKey: " + ewm.trackingKey);
		resuming.resume();
	}
	/**
	 * Return false if we generated an error 
	 */
	private boolean buildWebResponse(HttpServletResponse response, ReqWebMessage exchangeWebMessage) throws IOException {
        if (exchangeWebMessage.isErrorStatus()) {
        	trace.info("buildWebResponse - statusCode:" + exchangeWebMessage.statusCode );    	        	
        	if (exchangeWebMessage.getStatusMessage() != null) {
        		response.sendError(exchangeWebMessage.statusCode, exchangeWebMessage.getStatusMessage()); 
        	} else {
        		response.sendError(exchangeWebMessage.statusCode);
        	}    
        	return false;
        }
        trace.info("buildWebResponse : contentType: " + exchangeWebMessage.getResponseContentType());
        // The jetty server seems to add more onto the contentType than I provided. 
        response.setContentType(exchangeWebMessage.getResponseContentType());

        
        trace.info("buildWebResponse : statusCode: " + exchangeWebMessage.statusCode);
        response.setStatus(exchangeWebMessage.statusCode());        	
       
        Map<String,String>headers = exchangeWebMessage.getResponseHeaders();
        for (Iterator<String> iterator = headers.keySet().iterator(); iterator.hasNext();) {
			String key = iterator.next();
			response.setHeader(key, headers.get(key));
		}
        PrintWriter out = response.getWriter();
        out.print(exchangeWebMessage.getResponse());
        out.flush();
        out.close();
        return true;
    }

	private void buildWebErrResponse(HttpServletResponse response, ReqWebMessage exchangeWebMessage, int errCode)
			throws IOException {
		trace.warn("buildWebErrResponse - errCode:" + errCode + " tracking key: " + exchangeWebMessage.trackingKey);
		response.setContentType("text/html; charset=utf-8"); // this should be
		PrintWriter out = response.getWriter();
		switch(errCode) {
		case HttpServletResponse.SC_REQUEST_TIMEOUT:
			response.setStatus(errCode);
			if (HTTPTupleRequest.getnMissingTrackingKey().getValue() > 0) {
				out.print("<h1>Request timeout. Unable to find tracking key #" + HTTPTupleRequest.getnMissingTrackingKey().getValue() +" times is it being dropped/corrupted?</h1>");				
			} else {
				out.print("<h1>Request timeout</h1>");
			}
			break;
		default:
			response.setStatus(errCode);
			out.print("<h1>Unanticipated error : " + errCode +  "</h1>");						
		}

		out.flush();
		out.close();
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		Continuation continuation = ContinuationSupport.getContinuation(request);
		ReqWebMessage exchangeWebMessage = null;

		trace.info("continuation ::: " + " initial : " + continuation.isInitial() + ", resume  : "
				+ continuation.isResumed() + ", suspend : " + continuation.isSuspended() + ", expired : "
				+ continuation.isExpired() + ", wrapped : " + continuation.isResponseWrapped());

		if (continuation.isExpired()) {
			HTTPTupleRequest.getnRequestTimeouts().incrementValue(1L);
			exchangeWebMessage = (ReqWebMessage) continuation.getAttribute(Constant.EXCHANGEWEBMESSAGE);
			trace.warn("continuation - expired, timeout response sent. trackingKey:" + exchangeWebMessage.trackingKey
					+ " REQ:" + request.getQueryString());
			buildWebErrResponse(response, exchangeWebMessage, HttpServletResponse.SC_REQUEST_TIMEOUT);
		} else if (continuation.isResumed()) {
			// TODO * Must destroy the KEY in order that you can find a mismatch....
			exchangeWebMessage = (ReqWebMessage) continuation.getAttribute(Constant.EXCHANGEWEBMESSAGE);
			trace.info("continuation - resumed, web response being sent trackingKey:" + exchangeWebMessage.trackingKey
					+ " RSP:" + exchangeWebMessage.getResponse());
			if (!buildWebResponse(response, exchangeWebMessage)) {
				trace.info("continuation - Complete with ERR web response trackingKey:" + exchangeWebMessage.trackingKey);
			}
			trace.info("continuation - Complete, with NO ERR web response trackingKey:" + exchangeWebMessage.trackingKey);
		} else {
			exchangeWebMessage = new ReqWebMessage(request);
			trace.info("continuation - Initiated, send request to streams and suspend, trackingKey:"
					+ exchangeWebMessage.trackingKey + " REQ:" + request.getQueryString());

			exchangeWebMessage.setContinuation(continuation);
			continuation.setAttribute(Constant.EXCHANGEWEBMESSAGE, exchangeWebMessage);
			if (exchangeWebServer.getWebtimeout() != 0.0) {
				continuation.setTimeout((long) (1000.0 * exchangeWebServer.getWebtimeout()));
			}
			continuation.suspend();         // !important suspend before sending request
			exchangeWebServer.requestToStreams(exchangeWebMessage); // send request
			trace.info("continuation - Suspending trackingKey : " + exchangeWebMessage.trackingKey + "timeout:"
					+ exchangeWebServer.getWebtimeout());
			if (!continuation.isSuspended()) {
				trace.warn("continuation - failed to suspend, trackingKey:" + exchangeWebMessage.trackingKey
						+ "timeout:" + exchangeWebServer.getWebtimeout());
			}
		}
		baseRequest.setHandled(true);
		return;
	}

}
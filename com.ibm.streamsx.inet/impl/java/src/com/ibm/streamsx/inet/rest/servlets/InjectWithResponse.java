/**
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017
*/

package com.ibm.streamsx.inet.rest.servlets;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streamsx.inet.rest.ops.RequestProcess;
/**
 * <p>
 * Processes the message from the web and builds the response, timeout (Streams taking too long) are handled here as well. 
 * </p>
 * <p>
 * Dependent on how Jetty drives the interaction.
 * Web request arrives via Jetty, the actions that ensue are dependent on the state of the request's state: 
 *</p>
 * <ul>
 *  <li>Initial : A new request has arrived, move the request into a Streams tuple, the web request is suspended. 
 *     The suspended request pushed back to Jetty with a timeout. </li>
 * 	<li>Resumed : Streams has finished processing the request and the answer is ready to be returned, the suspended web request
 * has been continued by the arrival of the tuple on the operators Input port.  All the initial web request values are available. 
 * The response is built from the arriving tuple and sent out the web.   </li>   
 * 	<li>Expired : Streams has taken too long and the request has expired, generate a timeout response.</li> 
 * 	<li>Suspend : Streams is still working on the request, this should not happen.</li>
 * </ul>
 *
 */
public class InjectWithResponse extends SubmitterServlet {
	static Logger trace = Logger.getLogger(InjectWithResponse.class.getName());
	String greeting;
	String body;
	
	// Number of timeouts that have occurred. Used on timeout error message, attempting to 
	// hint where a possible problem is occurring. 
	public static int timeoutCount = 0;

	interface Constant {
		public static final String EXCHANGEWEBMESSAGE = "exchangeWebMessage";
	}

	// ReqWebServer exchangeWebServer = null;

	// Integer trackingKey = 0;
	
	private final long webTimeout;
	
	private Function<ReqWebMessage,OutputTuple> tupleCreator;

	public InjectWithResponse(OperatorContext context, StreamingOutput<OutputTuple> port) {
	    super(context, port);
		// this.exchangeWebServer = exchangeWebServer;

	    if (context.getParameterNames().contains("webTimeout")) {
	        double wtd = Double.valueOf(context.getParameterValues("webTimeout").get(0));
	        webTimeout = (long) (1000.0 * wtd);
	    } else {
	        webTimeout = SECONDS.toMillis(15);
	    }
	}
	
    @SuppressWarnings("unchecked")
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        tupleCreator = (Function<ReqWebMessage, OutputTuple>) config.getServletContext()
                .getAttribute("operator.conduit");
    }

	/**
	 * Callback from Streams side with the results, the results are in the from
	 * streams.
	 * 
	 * @param ewm
	 */
	public void asyncResume(ReqWebMessage ewm) {
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
			if (RequestProcess.getnMissingTrackingKey().getValue() > 0) {
				out.print("<h1>Request timeout. Unable to find tracking key #" + RequestProcess.getnMissingTrackingKey().getValue() +" times is it being dropped/corrupted?</h1>");				
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
	public void service(HttpServletRequest request, HttpServletResponse response)
	            throws ServletException, IOException {

		Continuation continuation = ContinuationSupport.getContinuation(request);
		ReqWebMessage exchangeWebMessage = null;

		trace.info("continuation ::: " + " initial : " + continuation.isInitial() + ", resume  : "
				+ continuation.isResumed() + ", suspend : " + continuation.isSuspended() + ", expired : "
				+ continuation.isExpired() + ", wrapped : " + continuation.isResponseWrapped());

		if (continuation.isExpired()) {
			RequestProcess.getnRequestTimeouts().incrementValue(1L);
			exchangeWebMessage = (ReqWebMessage) continuation.getAttribute(Constant.EXCHANGEWEBMESSAGE);
			trace.warn("continuation - expired, timeout response sent. trackingKey:" + exchangeWebMessage.trackingKey
					+ " REQ:" + request.getQueryString());
			buildWebErrResponse(response, exchangeWebMessage, HttpServletResponse.SC_REQUEST_TIMEOUT);
		} else if (continuation.isResumed()) {
			exchangeWebMessage = (ReqWebMessage) continuation.getAttribute(Constant.EXCHANGEWEBMESSAGE);
			trace.info("continuation - resumed, web response being sent trackingKey:" + exchangeWebMessage.trackingKey
					+ " RSP:" + exchangeWebMessage.getResponse());
			if (!buildWebResponse(response, exchangeWebMessage)) {
				trace.info("continuation - Complete with ERR web response trackingKey:" + exchangeWebMessage.trackingKey);
			}
			trace.info("continuation - Complete, with NO ERR web response trackingKey:" + exchangeWebMessage.trackingKey);
		} else {
			exchangeWebMessage = new ReqWebMessage(this, request);
			trace.info("continuation - Initiated, send request to streams and suspend, trackingKey:"
					+ exchangeWebMessage.trackingKey + " REQ:" + request.getQueryString());

			exchangeWebMessage.setContinuation(continuation);
			continuation.setAttribute(Constant.EXCHANGEWEBMESSAGE, exchangeWebMessage);
			if (webTimeout != 0) {
				continuation.setTimeout(webTimeout);
			}
			continuation.suspend();         // !important suspend before sending request
			submit(tupleCreator.apply(exchangeWebMessage)); // send request
			trace.info("continuation - Suspending trackingKey : " + exchangeWebMessage.trackingKey + "timeout:"
					+ webTimeout);
			if (!continuation.isSuspended()) {
				trace.warn("continuation - failed to suspend, trackingKey:" + exchangeWebMessage.trackingKey
						+ "timeout:" + webTimeout);
			}
		}
	}

}
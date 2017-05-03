/**
* Licensed Materials - Property of IBM
* Copyright IBM Corp.2017 
* @author mags
*/
package com.ibm.streamsx.inet.rest.ops;

import org.apache.log4j.Logger;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streamsx.inet.rest.engine.ServletEngine;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

/**
 *  
 * @author streamsadmin
 *
 */
public class ReqWebServer {
    static Logger trace = Logger.getLogger(ServletEngine.class.getName());
	
    int port = 8080;
    String context = "/suspend";
    double webTimeout =  1.0;
	StreamingOutput<OutputTuple> outStream;
	ReqHandlerSuspend excHandlerSuspend= null;
	ReqHandlerInterface excHandler;
	private String responseContentType;	
	
	Server server = null;

	
	private void startServer(int port, String context) {
		trace.info("Initalizing the webserver on port:" + port );
		
		server = new Server(port);
		
		ContextHandler contextSuspend = new ContextHandler(context);
		excHandlerSuspend = new ReqHandlerSuspend(this);
		contextSuspend.setHandler(excHandlerSuspend);
		
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.setHandlers(new Handler[] { contextSuspend });
        server.setHandler(contexts);
        try {
			server.start();
		} catch (Exception e1) {
			trace.error("Exception occured while attempting to connect on port:" + port );
			e1.printStackTrace();
		}
        // Invoking join, makes us wait for the server thread - not what the goal is
        trace.info("Initalized web server on port:" + port );        

	}
	
	void stopServer() {
		if (server != null) {
			trace.info("Request to stop webserver, current state : " + server.isRunning() );			
			if (server.isRunning()) {
				trace.info("Shutting down web server." );			
				try {
					server.stop();
					return;
				} catch (Exception e) {
					trace.error("Exception occured while attempting to shutdown web server." );			
					e.printStackTrace();
				}
			}
		}
		trace.warn("Web server is not currently running, request to shutdown ignored." );						
	}

	public ReqWebServer(ReqHandlerInterface excHandlerInterface ) {
		this.excHandler = excHandlerInterface;

	}
	public void setPort(int port) {
		this.port = port;
	}
	public void setContext(String context) {
		this.context = context;
	}
	
	public void setWebtimeout(double webTimeout) {
		this.webTimeout = webTimeout;			
	}
	public double getWebtimeout() {
		return this.webTimeout;
	}	
	public void start() {
		this.startServer(this.port, this.context);
	}
	public void requestToStreams(ReqWebMessage activeWebMessage) {
		excHandler.initiateRequestFromWeb(activeWebMessage);
	}
	public void responseFromStreams(ReqWebMessage activeWebMessage) {
		excHandlerSuspend.asyncResume(activeWebMessage);
	}
	public void setResponseContentType(String responseContentType) {
		this.responseContentType = responseContentType; 
    }


	

}

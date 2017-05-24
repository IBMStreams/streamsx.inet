/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014
*/
package com.ibm.streamsx.inet.rest.servlets;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.http.HttpServlet;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;

/**
 * Inject a tuple into an output port from a HTTP GET or POST
 * using application/json mime type.
 *
 */
class SubmitterServlet extends HttpServlet {	
	private static final long serialVersionUID = -4749929691876585085L;
	
	private final ScheduledExecutorService scheduler;
	private final StreamingOutput<OutputTuple> port;
	
	SubmitterServlet(OperatorContext context, StreamingOutput<OutputTuple> port) {
		this.scheduler = context.getScheduledExecutorService();
		this.port = port;
	}
	
	/**
	 * Submit a tuple, divorcing it from the servlet thread.
	 */
	void submit(OutputTuple tuple) throws IOException {
		scheduler.submit(port.deferredSubmit(tuple));
	}
	
	final StreamingOutput<OutputTuple> getPort() {
		return port;
	}
}

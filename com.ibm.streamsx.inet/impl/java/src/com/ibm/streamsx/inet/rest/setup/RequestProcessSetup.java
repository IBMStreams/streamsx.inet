/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streamsx.inet.rest.servlets.InjectWithResponse;

/**
 * Sets up the single servlet for HTTPRequestProcess.
 */
public class RequestProcessSetup implements OperatorServletSetup {

    /**
     * Servlet that accepts application/json POST and submits a
     * corresponding tuple with the first attribute being an XML attribute.
     * @return 
     */
	@Override
	public List<ExposedPort> setup(OperatorContext context, ServletContextHandler handler,
			ServletContextHandler ports) {
		
		Logger trace = Logger.getAnonymousLogger();
		List<ExposedPort> exposed = new ArrayList<ExposedPort>();
		
        for (StreamingOutput<OutputTuple> port : context
                .getStreamingOutputs()) {
        	
            ExposedPort ep = new ExposedPort(context, port, ports.getContextPath());
            exposed.add(ep);

            String path = "/analyze/" + port.getPortNumber() + "/*";
            ports.addServlet(new ServletHolder(new InjectWithResponse(context, port)),
                    path);
            ep.addURL("analyze", path);
            
            trace.info("Analyze URL: " + ports.getContextPath()
                    + path);
        }  
        
        return exposed;
	}
}

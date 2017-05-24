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
import com.ibm.streamsx.inet.rest.servlets.InjectForm;
import com.ibm.streamsx.inet.rest.servlets.InjectTuple;

/**
 * Sets up the single servlet for Tuple injection.
 */
public class PostTupleSetup implements OperatorServletSetup {

    /**
     * Servlet that injects tuples based upon a application/x-www-form-urlencoded POST
     * Servlet that provides a basic HTML form for tuple injection.
     * @return 
     */
	@Override
	public List<ExposedPort> setup(OperatorContext context, ServletContextHandler haXXndler,
			ServletContextHandler ports) {
		
		Logger trace = Logger.getAnonymousLogger();
		List<ExposedPort> exposed = new ArrayList<ExposedPort>();
				
        for (StreamingOutput<OutputTuple> port : context
                .getStreamingOutputs()) {
        	
            ExposedPort ep = new ExposedPort(context, port, ports.getContextPath());
            exposed.add(ep);
            
            String path = "/output/" + port.getPortNumber() + "/inject";
            ports.addServlet(new ServletHolder(new InjectTuple(context, port)),
                    path);
            
            ep.addURL("inject", path);
            trace.info("Injection URL (application/x-www-form-urlencoded): " + ports.getContextPath()
                    + path);

            path = "/output/" + port.getPortNumber() + "/form";
            ports.addServlet(new ServletHolder(new InjectForm(port)),
                    path);
            ep.addURL("form", path);
            trace.info("Injection FORM URL: " + ports.getContextPath()
                    + path); 
        }
        
        return exposed;
	}
}

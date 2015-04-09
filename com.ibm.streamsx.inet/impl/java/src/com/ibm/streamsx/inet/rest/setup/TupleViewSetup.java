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
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.window.StreamWindow;
import com.ibm.streamsx.inet.rest.servlets.AccessWindowContents;
import com.ibm.streamsx.inet.window.WindowContentsAtTrigger;


public class TupleViewSetup implements OperatorServletSetup {

	@Override
	public List<ExposedPort> setup(OperatorContext context, ServletContextHandler staticContext,
			ServletContextHandler ports) {
		
		List<ExposedPort> exposed = new ArrayList<ExposedPort>();
		
        final List<WindowContentsAtTrigger<Tuple>> windows = new ArrayList<WindowContentsAtTrigger<Tuple>>(
                context.getNumberOfStreamingOutputs());
        
        Logger trace = Logger.getAnonymousLogger();

        for (StreamingInput<Tuple> port : context.getStreamingInputs()) {
        	                              
            StreamWindow<Tuple> window = port.getStreamWindow();

            WindowContentsAtTrigger<Tuple> contents = new WindowContentsAtTrigger<Tuple>(
                    context, port);
            windows.add(contents);
            window.registerListener(contents, false);
            
            String path = "/input/" + port.getPortNumber() + "/tuples";
            ports.addServlet(new ServletHolder(new AccessWindowContents(contents)),  path);

            ExposedPort ep = new ExposedPort(context, port, ports.getContextPath());
            exposed.add(ep);
            ep.addURL("tuples", path);
            
            trace.info("Port JSON URL: " + ports.getContextPath() + path);
        }
        return exposed;
	}
}

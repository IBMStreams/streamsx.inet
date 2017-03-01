/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.setup;

import java.util.List;

import org.eclipse.jetty.servlet.ServletContextHandler;

import com.ibm.streams.operator.OperatorContext;

/**
 * Interface to set up the servlets for an operator using Jetty.
 * 
 * The servlet contexts contain these attributes for the operator creating the servlets:
 * 
 * operator.context = OperatorContext reference 
 * operator.conduit = Conduit object provided by the operator in the registerOperator() call.
 * 
 * These attributes are available using the init method of the servlet,
 * see AccessXMLAttribute for an example.
 */
public interface OperatorServletSetup {
	
	public List<ExposedPort> setup(OperatorContext context,
			ServletContextHandler handler,
			ServletContextHandler ports);
}

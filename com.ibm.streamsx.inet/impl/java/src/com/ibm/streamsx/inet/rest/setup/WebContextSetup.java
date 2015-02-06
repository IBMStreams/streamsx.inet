/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.setup;

import java.util.List;

import org.eclipse.jetty.servlet.ServletContextHandler;

import com.ibm.streams.operator.OperatorContext;

/**
 * Does not setup any operator specific servlets.
 *
 */
public class WebContextSetup implements OperatorServletSetup {

	@Override
	public List<ExposedPort> setup(OperatorContext context, ServletContextHandler handler,
			ServletContextHandler ports) {
		return null;
	}
}

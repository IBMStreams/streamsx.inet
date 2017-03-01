/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2012  
*/
package com.ibm.streamsx.inet.rest.engine;

import com.ibm.streams.operator.OperatorContext;

/**
 * Interface for sharing a servlet engine across multiple
 * operators within the same PE. An instance of ServletEngineMBean
 * must be obtained through ServletEngine.getServletEngine().
 *
 */
public interface ServletEngineMBean {
	
	/**
	 * Register an operator with the default servlet setup.
	 * Registers any input ports as windowed with access to the window contents
	 * through getWindowContents().
	 * Each input port is registered as:
	 * operator_name/ports/input/port_number - POST url for tuple injection
	 * operator_name/ports/input/port_number/form - HTML form for tuple injection
	 * 
	 * Registers any output ports as injection of tuples into the stream.
	 * 
	 * @param injectPorts True if all output ports are to be added as injection ports
	 * @param viewPorts True if all input ports are to be added as viewable ports

	 * @param conduit adds a conduit object to pass data between the operator and the servlet.
	 * The conduit object must only use classes that will be in common between
	 * the two operator class loaders, ie. those from Java platform itself
	 * or the Java operator api. The object will be available as the attribute
	 * {@code operator.conduit} in the servlet context.
	 */
	public void registerOperator(String operatorClass, OperatorContext context, Object conduit) throws Exception;
	
	/**
	 * Start the web-server. Must be called in the allPortsReady method.
	 * @throws Exception
	 */
	public void start() throws Exception;
	
	/**
	 * Stop the web-server. Must be called in the shutdown method.
	 * @throws Exception
	 */
	public void stop() throws Exception;
}

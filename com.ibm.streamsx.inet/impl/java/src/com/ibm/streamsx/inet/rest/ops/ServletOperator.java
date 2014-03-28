/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014  
# US Government Users Restricted Rights - Use, duplication or
# disclosure restricted by GSA ADP Schedule Contract with
# IBM Corp.
*/
package com.ibm.streamsx.inet.rest.ops;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streamsx.inet.rest.engine.ServletEngine;
import com.ibm.streamsx.inet.rest.engine.ServletEngineMBean;

public abstract class ServletOperator extends AbstractOperator {
	
	private ServletEngineMBean jetty;

	public synchronized ServletEngineMBean getJetty() {
		return jetty;
	}

	public synchronized void setJetty(ServletEngineMBean jetty) {
		this.jetty = jetty;
	}

	@Override
	public void initialize(OperatorContext context) throws Exception {

		super.initialize(context);
				
		setJetty(ServletEngine.getServletEngine(context));
		
		getJetty().registerOperator(getClass().getName(), context, getConduit());
		
		createAvoidCompletionThreadIfNoInputs();
	}
	
	protected Object getConduit() {
		return null;
	}
	
	@Override
	public void process(com.ibm.streams.operator.StreamingInput<com.ibm.streams.operator.Tuple> stream, com.ibm.streams.operator.Tuple tuple) throws Exception {
	}
		

	@Override
	public void allPortsReady() throws Exception {
		getJetty().start();
	}
	
	@Override
	public void shutdown() throws Exception {
		getJetty().stop();
	}
	
	/*
	 * The ServletEngine accesses all parameters through the operator
	 * context, as that is an object that is not specific to each
	 * operator's class loader.
	 */
	
	@Parameter(optional=true, description="Port number for the embedded Jetty HTTP server. Defaults to 8080.")
	public void setPort(int port) {}
	@Parameter(optional=true, description=CONTEXT_DESC)
	public void setContext(String context) {}
	@Parameter(optional=true, description=CRB_DESC)
	public void setContextResourceBase(String base) {}
	
	@ContextCheck
	public static void checkContextParameters(OperatorContextChecker checker) {	
		checker.checkDependentParameters("context", "contextResourceBase");
		checker.checkDependentParameters("contextResourceBase", "context");
	}
	
	static final String CONTEXT_DESC = "Define a URL context path that maps to the resources defined by" + 
			"`contextResourceBase`. This allows a composite that invokes this operator in a " + 
			"toolkit to provide resources regardless of the value of the application's data directory. " + 
			"For example setting it to *maps* would result in the URL */maps/index.html* " + 
			"mapping to the file *index.html* in the directory defined by " + 
			"`contextResourceBase`. Requires the parameter `contextResourceBase` to be set. If " + 
			"when the operator is initialized the context already exists then no action is taken. This " + 
			"allows multiple independent composites in the same toolkit to have common `context` " + 
			"and `contextResourceBase` settings, typically to point to a single set of HTML and " + 
			"Javascript resources for the toolkit.\\n" +
			"\\n" +
			"If the operator provides URLs for its input or output ports then they are placed "
			+ "in the this context when this parameter is set. This then provides fixed locations "
			+ "for the URLs regardless of the depth of the operator invocation in the main composite.\\n" +
			"\\n" +
			"Only a single context per invocation is supported.";
	
	static final String CRB_DESC = "Directory location of resources that will be available through " + 
			"the the URL context defined by the parameter `context`. Typically used to allow a toolkit to provide a " + 
			"set of resources in a fixed location. A path within the toolkit is obtained using the SPL " + 
			"function `getThisFileDir()`. Thus a composite in the file *maps.spl* in the " + 
			"namespace directory `com.acme.streams.apps.map` might have the following " + 
			"setting to map `http://127.0.0.1:8080/maps` to `opt/html/mapinfo` in the toolkit.\\n" + 
			"\\n" + 
			"    param\\n" + 
			"      context: “maps”\\n" + 
			"      contextResourceBase: getThisFileDir() + “/../opt/html/mapinfo”\\n";
}

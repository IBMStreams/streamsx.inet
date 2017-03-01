/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014 
*/
package com.ibm.streamsx.inet.rest.ops;

import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

@PrimitiveOperator(name="HTTPTupleInjection", description=PostTuple.DESC)
@OutputPorts({@OutputPortSet(cardinality=1,
   description="Emits a tuple for each POST request on the inject URL with port index 0"),
   @OutputPortSet(optional=true,
   description="Optional additional ports that emit a tuple for each POST request on the inject URL with the corresponding port index")})
@Icons(location32="icons/HTTPTupleInjection_32.gif", location16="icons/HTTPTupleInjection_16.gif")
public class PostTuple extends ServletOperator {
	
	static final String DESC =
			"Embeds a Jetty web server to allow HTTP or HTTPS POST requests to submit a tuple on " + 
			"its output ports. Each output port corresponds to a unique URL comprising the operator name " + 
			"and the port index.\\n" + 
			"\\n" + 
			"A single tuple is generated for an incoming POST request. Each attribute in the output port's " + 
			"schema corresponds to a parameter in the POST with the attribute name, using `application/x-www-form-urlencoded`. If the parameter " + 
			"exists in the POST then its first value is assigned to the output tuple, if the value is not " + 
			"provided then the attribute will be set to its default value.\\n" + 
			"\\n" +
			"In addition to the URL for POST request, a URL is created that displays an automatically " + 
			"generated HTML form that can be displayed by a browser and used for manual injection of " + 
			"tuples.\\n" + 
			"\\n" +
			"The URLs defined by this operator are:\\n" +
			"* *prefix*`/ports/output/`*port index*`/inject` - Accepts POST requests of type `application/x-www-form-urlencoded`.\\n" +
			"* *prefix*`/ports/output/`*port index*`/form` - HTML web form that can be used to test tuple submission.\\n" +
			"* *prefix*`/ports/output/`*port index*`/info` - Output port meta-data including the stream attribute names and types (content type `application/json`).\\n" +
			"\\nThe *prefix* for the URLs is:\\n" +
			"* *context path*`/`*base operator name* - When the `context` parameter is set.\\n" +
			"* *full operator name* - When the `context` parameter is **not** set.\\n" +
			"\\n" + 
			"**Maximum Content Size**:\\n" +
			"Jetty limits the amount of data that can post back from a browser " +
			"or other client to this operator. This helps protect the operator against " +
			"denial of service attacks by malicious clients sending huge amounts of data. " +
			"The default limit is 200K bytes, a client will receive an HTTP 500 error response code if it " +
			"tries to POST too much data. The limit may be increased using the `maxContentSize` parameter.\\n" +
			"\\n" +
			"**Limitations**:\\n" + 
			"* Error handling is limited, incorrect URLs can crash the application.\\n" + 
			"* Not all SPL data types are supported. String, signed integer and float types are supported for POST parameters. Output port may contain other types but will be set\\n" + 
			"to their default values.\\n" + 
			"* By default no security access is provided to the data, HTTPS must be explicitly configured.";
	
	
	public static final String MAX_CONTEXT_SIZE_DESC =
			"Change the maximum HTTP POST content size (K bytes) allowed by this operator." +
			"Jetty limits the amount of data that can posted from a browser " +
			"or other client to the operator. This helps protect the operator against " +
			"denial of service attacks by malicious clients sending huge amounts of data. " +
			"The default maximum size Jetty permits is 200K bytes, thus the default value for this parameter is 200. " +
			"For example, to increase to 500,000 bytes set maxContentSize to 500.";
	
	public static final String MAX_CONTENT_SIZE_PARAM = "maxContentSize";
	
	/*
	 * The ServletEngine accesses all parameters through the operator
	 * context, as that is an object that is not specific to each
	 * operator's class loader.
	 */
	@Parameter(optional=true, description=MAX_CONTEXT_SIZE_DESC)
	public void setMaxContentSize(int maxContentSize) {}		
}

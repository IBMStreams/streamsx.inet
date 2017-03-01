/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014 
*/
package com.ibm.streamsx.inet.rest.ops;

import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.PrimitiveOperator;

@PrimitiveOperator(name="HTTPXMLInjection", description=PostXML.DESC)
@OutputPorts({@OutputPortSet(cardinality=1,
description="Emits a tuple for each POST request on the inject URL with port index 0"),
@OutputPortSet(optional=true,
description="Optional additional ports that emit a tuple for each POST request on the inject URL with the corresponding port index")})
@Icons(location32="icons/HTTPXMLInjection_32.gif", location16="icons/HTTPXMLInjection_16.gif")
public class PostXML extends ServletOperator {
	
	/**
	 * Verify the first attribute is an XML attribute for each output port.
	 */
	@ContextCheck
	public static void checkXMLAttribute(OperatorContextChecker checker) {	
		for (StreamingOutput<?> port : checker.getOperatorContext()
				.getStreamingOutputs()) {
			Attribute first = port.getStreamSchema().getAttribute(0);
			checker.checkAttributeType(first, MetaType.XML);
		}
	}
	
	static final String DESC =
			"Embeds a Jetty web server to allow HTTP or HTTPS POST requests to submit a tuple on " + 
			"its output ports. Each output port corresponds to a unique URL comprising the operator name " + 
			"and the port index.\\n" + 
			"\\n" + 
			"A single tuple is generated for an incoming POST request. The first attribute, which must be of SPL type `xml` in the output port's " + 
			"schema corresponds to the contents of the POST requiring content-type `application/xml`. " + 
			"Any other attributes will be set to their default value.\\n" + 
			"\\n" +
			"The URLs defined by this operator are:\\n" +
			"* *prefix*`/ports/output/`*port index*`/inject` - Accepts POST requests of type `application/xml`.\\n" +
			"* *prefix*`/ports/output/`*port index*`/info` - Output port meta-data including the stream attribute names and types (content type `application/json`).\\n" +
			"\\nThe *prefix* for the URLs is:\\n" +
			"* *context path*`/`*base operator name* - When the `context` parameter is set.\\n" +
			"* *full operator name* - When the `context` parameter is **not** set.\\n" +
			"\\n" + 
			"**Limitations**:\\n" + 
			"* Error handling is limited, incorrect URLs can crash the application.\\n" + 
			"* By default no security access is provided to the data, HTTPS must be explicitly configured.";
}

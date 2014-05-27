/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014 
*/
package com.ibm.streamsx.inet.rest.ops;

import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.PrimitiveOperator;

@PrimitiveOperator(name="HTTPTupleInjection", description=PostTuple.DESC)
@OutputPorts({@OutputPortSet(cardinality=1,
   description="Emits a tuple for each POST request on the inject URL with port index 0"),
   @OutputPortSet(optional=true,
   description="Optional additional ports that emit a tuple for each POST request on the inject URL with the corresponding port index")})
public class PostTuple extends ServletOperator {
	
	static final String DESC =
			"Embeds a Jetty web server to allow HTTP POST requests to submit a tuple on " + 
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
			"**Limitations**:\\n" + 
			"* Error handling is limited, incorrect URLs can crash the application.\\n" + 
			"* Not all SPL data types are supported. String, signed integer and float types are supported for POST parameters. Output port may contain other types but will be set\\n" + 
			"to their default values.\\n" + 
			"* No security access is provided to the data. This is mainly aimed at demos.";
}

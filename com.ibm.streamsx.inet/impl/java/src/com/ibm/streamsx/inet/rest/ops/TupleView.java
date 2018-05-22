/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014 
*/
package com.ibm.streamsx.inet.rest.ops;

import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

@PrimitiveOperator(name=TupleView.opName, description=TupleView.DESC)
// Require at least one input port
@InputPorts({
	@InputPortSet(cardinality=1,windowingMode=WindowMode.Windowed,windowPunctuationInputMode=WindowPunctuationInputMode.WindowBound,
			description="Windowed input port whose tuples will be available using a HTTP GET request with a URL using port index 0."),
	@InputPortSet(optional=true,windowingMode=WindowMode.Windowed,windowPunctuationInputMode=WindowPunctuationInputMode.WindowBound,
			description="Optional windowed input ports whose tuples will be available using a HTTP GET request a URL with the corresponding port index.")
	})
@Icons(location32="icons/"+TupleView.opName+"_32.gif", location16="icons/"+TupleView.opName+"_16.gif")

public class TupleView extends ServletOperator {
    static final String opName = "HTTPTupleView";
	// Parameter setters just to define the parameters in the SPL operator model.
	@Parameter(optional=true, cardinality=-1, description="Names of attributes to partition the window by. If the cardinality of this parameter is > 1,"
			+ "then every value represents one attribute name. If the cadinality equals to 1, the value may contain one attribute name or a comma separated list of attribute names.")
	public void setPartitionKey(String[] attributeNames) {}
	
    @Parameter(optional = true, description = "List of headers to insert into the http reply. Formatted as header:value")
    public void setHeaders(String[] headers) {}
    
	static final String DESC = "REST HTTP or HTTPS API to view tuples from windowed input ports.\\n" + 
			"Embeds a Jetty web server to provide HTTP REST access to the collection of tuples in " + 
			"the input port window at the time of the last eviction for tumbling windows, " + 
			"or last trigger for sliding windows." +
			"\\n" +
			"The URLs defined by this operator are:\\n" +
			"* *prefix*`/ports/input/`*port index*`/tuples` - Returns the set of tuples as a array of the tuples in JSON format (content type `application/json`).\\n" +
			"* *prefix*`/ports/input/`*port index*`/info` - Output port meta-data including the stream attribute names and types (content type `application/json`).\\n" +
			"\\nThe *prefix* for the URLs is:\\n" +
			"* *context path*`/`*base operator name* - When the `context` parameter is set.\\n" +
			"* *full operator name* - When the `context` parameter is **not** set.\\n" +
			"\\n" + 
			"The `/tuples` URL accepts these optional query parameters:\\n" + 
			"* `partition` – When the window is partitioned defines the partition to be extracted from the window. When partitionKey contains multiple attributes, partition must contain the same number of values as attributes and in the same order, e.g. `?partition=John&partition=Smith`. " + 
			"would match the SPL partitionKey setting of: `partitionKey: “firstName”, “lastName”;`. When a window is partitioned and no partition query parameter is provided the data for all partitions is returned.\\n" + 
			"* `attribute` – Restricts the returned data to the named attributes. Data is returned in the order the attribute names are provided. When not provided, all attributes in the input tuples are returned. E.g. `?format=json&attribute=lastName&attribute=city` will return only the `lastName` and `city` attributes in that order with `lastName` first.\\n" + 
			"* `suppress` – Suppresses the named attributes from the output. When not provided, no attributes are suppressed. suppress is applied after applying the query parameter attribute. E.g. `?suppress=firstName&suppress=lastName` will not include lastName or firstName in the returned data.\\n" +
			"* `callback` – Wrappers the returned JSON in a call to callbackvalue(...json...); to enable JSONP processing.\\n" +
			"Query parameters are ignored if the input port's schema is `tuple<rstring jsonString>`.\\n" +
			"\\n" + 
			"The fixed URL `/ports/info` returns meta-data (using JSON) about all of the Streams ports that have associated URLs.\\n" + 
			"\\n" + 
			"Tuples are converted to JSON using " + 
			"the `JSONEncoding` support from the Streams Java Operator API,\\n" + 
			"except for: \\n" +
			"* If the input port's schema is `tuple<rstring jsonString>` then value is taken as is serialized JSON " +
			" and the resultant JSON is returned as the tuple's JSON value.\\n" +
			"* Within a tuple any attribute that is `rstring jsonString`, then the value is taken as " +
			"serialized JSON and it is placed into the tuple's " +
			"JSON object as its deserialized JSON with key `jsonString`.\\n" +
			"\\n" + 
			"`HTTPTupleView`, [HTTPTupleInjection], [HTTPXMLInjection] and [WebContext] embed a Jetty webserver and " + 
			"all operator invocations in an SPL application that are co-located/fused in same partition (PE) " + 
			"will share a common Jetty instance. Thus by " + 
			"fusing these operators together with a common port value, a single web-server serving a " + 
			"single SPL application can be created. This sharing of the web-server by fusing multiple " + 
			"operators allows the operators to be logically connected in the SPL graph, rather than a single " + 
			"operator with multiple unrelated streams being connected to it.\\n" + 
			"\\n" + 
			"Static content in the sub-directory `html` of the application's `opt` directory will also be served " + 
			"by the embedded web-server, thus allowing a complete web-application with live data to be " + 
			"served by an SPL application. The default URL for the web-server resolves to " + 
			"`{application_dir}/opt/html/index.html`.\\n" + 
			"\\n" + 
			"Operators that support the `context` and `contextResourceBase` SPL parameters will serve " + 
			"static files from the `contextResourceBase` directory rooted at the supplied context path.\\n" + 
			"\\n" + 
			"**Limitations**:\\n" + 
			"* Error handling is limited, incorrect URLs can crash the application, e.g. providing the wrong number of partition values.\\n" + 
			"* By default no security access is provided to the data, HTTPS must be explicitly configured.\\n";
}

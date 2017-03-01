/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014 
*/
package com.ibm.streamsx.inet.rest.ops;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streamsx.inet.messages.Messages;

@PrimitiveOperator(name="HTTPXMLView", description=XMLView.DESC)
@InputPortSet(cardinality=1,windowingMode=WindowMode.NonWindowed,
			description="Input port whose first XML attribute will be available using a HTTP GET request with a URL using port index 0.")
@Icons(location32="icons/HTTPXMLView_32.gif", location16="icons/HTTPXMLView_16.gif")
public class XMLView extends ServletOperator {
	
	private final Map<Integer, Object[]> portData = Collections.synchronizedMap(new HashMap<Integer, Object[]>());
	
	/**
	 * Ensure there is an XML attribute.
	 * @param checker
	 */
	@ContextCheck
	public static void checkHasXMLAttribute(OperatorContextChecker checker) {
		StreamingInput<Tuple> port = checker.getOperatorContext().getStreamingInputs().get(0);
		for (Attribute attr : port.getStreamSchema()) {
			if (attr.getType().getMetaType() == MetaType.XML)
				return;
		}
		checker.setInvalidContext(Messages.getString("INPUT_PORT_PARAM_CHECK_1"), new String[] {port.getName()});
	}
	
	private int attributeIndex = -1;
	@Override
	public void initialize(OperatorContext context) throws Exception {
		super.initialize(context);
		
		for (Attribute attr : getInput(0).getStreamSchema()) {
			if (attr.getType().getMetaType() == MetaType.XML) {
				attributeIndex = attr.getIndex();
				break;
			}
		}
	}
	
	protected Object getConduit() {
		return portData;
	}
	
	
	@Override
	public void process(StreamingInput<Tuple> port, Tuple tuple)
			throws Exception {
		portData.put(port.getPortNumber(), new Object[] {tuple.getXML(attributeIndex), System.currentTimeMillis()});
	}
	
	static final String DESC = "REST API to view tuples from input ports.\\n" + 
			"Embeds a Jetty web server to provide HTTP or HTTPS REST access to the first XML attribute of the last tuple received by " + 
			"the input port." +
			"\\n" +
			"The URLs defined by this operator are:\\n" +
			"* *prefix*`/ports/input/`*port index*`/attribute` - Returns the value of the XML attribute (content type `application/xml`).\\n" +
			"* *prefix*`/ports/input/`*port index*`/info` - Output port meta-data including all the stream attribute names and types (content type `application/json`).\\n" +
			"\\nThe *prefix* for the URLs is:\\n" +
			"* *context path*`/`*base operator name* - When the `context` parameter is set.\\n" +
			"* *full operator name* - When the `context` parameter is **not** set.\\n" +
			"\\n" + 
			"The input port schema must contain an XML attribute whose value will be made available through the `/tuple` URL.\\n" + 
			"\\n" + 
			"**Limitations**:\\n" + 
			"* By default no security access is provided to the data, HTTPS must be explicitly configured.\\n";

}

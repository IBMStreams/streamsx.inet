//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.FileReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.types.RString;
import com.ibm.streamsx.inet.http.HTTPRequest.RequestType;

@InputPorts(@InputPortSet(cardinality=1, 
			description="All attributes of the input stream are sent as POST data to the specified HTTP server"))
@OutputPorts(@OutputPortSet(cardinality=1, optional=true, 
			description="Emits a tuple containing the reponse received from the server. " +
		     "Tuple structure must conform to the \\\"HTTPResponse\\\" type specified in this namespace."))
@PrimitiveOperator(name="HTTPPostSink", description=HTTPPostOper.DESC)
public class HTTPPostOper extends AbstractOperator  
{
	static final String CLASS_NAME="com.ibm.streamsx.inet.http.HTTPPostOper";

	private static Logger trace = Logger.getLogger(CLASS_NAME);

	private static final String ENCODING = "UTF-8"; 

	private int sleepDelay = 3, retries = 3;
	private String url = null;
	private IAuthenticate auth = null;
	private boolean sendAsParams = false;
	private String authType = "none", authFile = null;
	private RetryController rc = null;

	private boolean hasOutputPort = false;
	private boolean shutdown = false;
	private List<String> authProps = new ArrayList<String>();

	@Parameter(optional= false, description="URL to connect to")
	public void setUrl(String url) {
		this.url = url;
	}
	@Parameter(optional=true, 
			description="Valid options are \\\"basic\\\" and \\\"none\\\". Default is \\\"none\\\".")
	public void setAuthenticationType(String val) {
		this.authType = val;
	}
	@Parameter(optional=true, description=
			"Path to the properties file containing authentication information. Path can be absolute or relative to the data directory of the application."+
			" See http_auth_basic.properties in the toolkits config directory for a sample of basic authentication properties.")
	public void setAuthenticationFile(String val) {
		this.authFile = val;
	}
	@Parameter(optional=true, description="Properties to override those in the authentication file.")
	public void setAuthenticationProperty(List<String> val) {
		authProps.addAll(val);
	}
	@Parameter(optional=true, description="Maximum number of retries in case of failures/disconnects.")
	public void setMaxRetries(int val) {
		this.retries = val;
	}
	@Parameter(optional=true, description="Wait time between retries in case of failures/disconnects.")
	public void setSleepDelay(int val) {
		this.sleepDelay = val;
	}
	@Parameter(optional=true, description="Send data as parameters in the \\\"name=value\\\" format. Otherwise only values are sent. Default is false")
	public void setSendAsParams(boolean val) {
		this.sendAsParams = val;
	}
	

	@Override
	public void initialize(OperatorContext op) throws Exception 
	{
		super.initialize(op);    

		Properties props = null;
		if(authFile != null) {
			props = new Properties();
			props.load(new FileReader(authFile));
		}
		if(authProps.size() >0 ) {
			for(String value : authProps) {
				String [] arr = value.split("=");
				if(arr.length < 2) 
					throw new IllegalArgumentException("Invalid property: " + value);
				String name = arr[0];
				String v = value.substring(arr[0].length()+1, value.length());
				props.setProperty(name, v);
			}
		}
		
		trace.log(TraceLevel.INFO, "Using authentication type: " + authType);
		auth = AuthHelper.getAuthenticator(authType, props);

		rc = new RetryController(retries, sleepDelay);
		hasOutputPort = op.getStreamingOutputs().size() == 1;
		
		trace.log(TraceLevel.INFO, "URL: " + url);
	}


	@Override
	public synchronized void process(StreamingInput<Tuple> stream, Tuple tuple) throws Exception {
		rc.readSuccess();

		StringBuilder data = new StringBuilder(1024);
		StreamSchema schema = stream.getStreamSchema();

		for (Attribute attribute : schema) {
			if(sendAsParams) {
				if(data.length() != 0) 
					data.append('&');
				data.append(URLEncoder.encode(attribute.getName(), ENCODING));
				data.append('=');
			}
			data.append(URLEncoder.encode(tuple.getObject(attribute.getName()).toString(), ENCODING));            
		}

		if(trace.isLoggable(TraceLevel.DEBUG))
			trace.log(TraceLevel.DEBUG, "Sending Data: " + data.toString());

		HTTPResponse resp = null;

		Throwable t = null;
		while(true) {
			try {
				HTTPRequest req = new HTTPRequest(url);
				req.setType(RequestType.POST);
				
				if(trace.isLoggable(TraceLevel.INFO))
					trace.log(TraceLevel.INFO, "Sending request: " + req.toString() + ", data: " + data.toString());
				
				resp = req.sendRequest(auth, data.toString());
				rc.readSuccess();
				break;
			}catch(Exception e) {
				t=e;
				rc.readException();
				trace.log(TraceLevel.ERROR, "Exception", e);
			}
			if(!shutdown && rc.doRetry()) {
				trace.log(TraceLevel.ERROR, "Sleeping " + sleepDelay +" seconds");
				sleepABit(rc.getSleep());
			}
			else {
				break;
			}
		}

		if(trace.isLoggable(TraceLevel.INFO))
			trace.log(TraceLevel.INFO, "Response code: " + 
						((resp!=null) ? resp.getResponseCode() : -1) 
					);

		if(!hasOutputPort) 
			return;

		StreamingOutput<OutputTuple> op = getOutput(0);
		OutputTuple otup = op.newTuple();
		
		if(resp == null) {
			if(t == null) 
				otup.setObject("errorMessage", new RString("Unknown error."));
			else 
				otup.setObject("errorMessage", new RString(t.getMessage()));
			
			otup.setInt("responseCode", -1);
		}
		else {
			trace.log(TraceLevel.INFO, "Response: " + resp.toString());
			
			if(resp.getErrorStreamData()!=null)
				otup.setObject("errorMessage", new RString(resp.getErrorStreamData()));
	
			if(resp.getOutStreamData() != null) {
				otup.setObject("data", new RString(resp.getOutStreamData()));
				otup.setInt("dataSize", resp.getOutStreamData().length());
			}
	
			otup.setInt("responseCode", resp.getResponseCode());
		}
		if(trace.isLoggable(TraceLevel.DEBUG))
			trace.log(TraceLevel.DEBUG, "Sending tuple: " + otup.toString());
		op.submit(otup);
	}

	void sleepABit(long seconds) throws InterruptedException {
		long end  = System.currentTimeMillis() + (seconds * 1000);

		while(!shutdown && System.currentTimeMillis() < end) {
			Thread.sleep(1 * 100);
		}
	}

	@Override
	public void shutdown() throws Exception {
		shutdown = true;
	}

	public static final String DESC = 
			"This operator sends incoming tuples to the specified HTTP server as part of a POST request." +
			"A single tuple will be sent as a body of one HTTP POST request." +
			"All attributes of the tuple will be serialized and sent to the server." +
			"Certain authentication modes are supported." +
			"Tuples are sent to the server one at a time in order of receipt. If the HTTP server cannot be accessed, the operation " +
			"will be retried on the current thread and may temporarily block any additional tuples that arrive on the input port" +
			"\\n" + 
			"**Limitations**:\\n" + 
			"* Nested attributes are not individually accessed and serialized. Only the top level attributes are serialized individually.\\n" 
		;

}




//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.encoding.EncodingFactory;
import com.ibm.streams.operator.encoding.JSONEncoding;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.state.ConsistentRegionContext;
import com.ibm.streamsx.inet.http.HTTPRequest.RequestType;

@InputPorts(@InputPortSet(cardinality=1, 
			description="All attributes of the input stream are sent as POST data to the specified HTTP server"))
@OutputPorts(@OutputPortSet(cardinality=1, optional=true, 
			description="Emits a tuple containing the reponse received from the server. " +
		     "Tuple structure must conform to the [HTTPResponse] type specified in this namespace."))
@PrimitiveOperator(name=HTTPPostOper.OPER_NAME, description=HTTPPostOper.DESC)
@Libraries(value={"opt/downloaded/*"})
@Icons(location32="impl/java/icons/HTTPPost_32.gif", location16="impl/java/icons/HTTPPost_16.gif")
public class HTTPPostOper extends AbstractOperator  
{
	static final String CLASS_NAME="com.ibm.streamsx.inet.http.HTTPPostOper"; //$NON-NLS-1$
	static final String OPER_NAME = "HTTPPost"; //$NON-NLS-1$
        public static final String CONSISTENT_CUT_INTRODUCER="\\n\\n**Behavior in a consistent region**\\n\\n"; //$NON-NLS-1$
	
	static final String 
			MIME_JSON = "application/json",   //$NON-NLS-1$
			MIME_FORM = "application/x-www-form-urlencoded"; //$NON-NLS-1$

	private static Logger trace = Logger.getLogger(CLASS_NAME);


	private double retryDelay = 3;
	private int maxRetries = 3;
	private String url = null;
	private IAuthenticate auth = null;
	private String authenticationType = "none", authenticationFile = null; //$NON-NLS-1$
	private RetryController rc = null;

	private boolean hasOutputPort = false;
	private boolean shutdown = false;
	private List<String> authenticationProperties = new ArrayList<String>();
	
	private String headerContentType = MIME_FORM;

	@Parameter(optional= false, description="URL to connect to")
	public void setUrl(String url) {
		this.url = url;
	}
	@Parameter(optional=true, 
			description="Valid options are \\\"basic\\\" and \\\"none\\\". Default is \\\"none\\\".")
	public void setAuthenticationType(String val) {
		this.authenticationType = val;
	}
	@Parameter(optional=true, description=
			"Path to the properties file containing authentication information. " +
			"Authentication file is recommended to be stored in the application_dir/etc directory. " +
			"Path of this file can be absolute or relative, if relative path is specified then it is relative to the application directory. "+
			"See http_auth_basic.properties in the toolkits etc directory for a sample of basic authentication properties.")
	public void setAuthenticationFile(String val) {
		this.authenticationFile = val;
	}
	@Parameter(optional=true, description="Properties to override those in the authentication file.")
	public void setAuthenticationProperty(List<String> val) {
		authenticationProperties.addAll(val);
	}
	@Parameter(optional=true, description="Maximum number of retries in case of failures/disconnects.")
	public void setMaxRetries(int val) {
		this.maxRetries = val;
	}
	@Parameter(optional=true, description="Wait time between retries in case of failures/disconnects.")
	public void setRetryDelay(double val) {
		this.retryDelay = val;
	}
	@Parameter(optional=true, description="Set the content type of the HTTP request. " +
			" If the value is set to \\\""+MIME_JSON+"\\\" then the entire tuple is sent in JSON format. " +
			" Default is \\\""+MIME_FORM+"\\\"." +
			" Note that if a value other than the above mentioned ones is specified, the input stream can only have a single attribute.")
	public void setHeaderContentType(String val) {
		this.headerContentType = val;
	}
	
	//consistent region checks
	@ContextCheck(compile = true)
	public static void checkInConsistentRegion(OperatorContextChecker checker) {
		ConsistentRegionContext consistentRegionContext = 
				checker.getOperatorContext().getOptionalContext(ConsistentRegionContext.class);
		
		if(consistentRegionContext != null && consistentRegionContext.isStartOfRegion()) {
			checker.setInvalidContext(	Messages.getString("CONSISTENT_CHECK_1"),  //$NON-NLS-1$
										new String[] {HTTPPostOper.OPER_NAME});
		}
	}
	
	@Override
	public void initialize(OperatorContext op) throws Exception  {
		super.initialize(op);    

		trace.log(TraceLevel.INFO, "Using authentication type: " + authenticationType); //$NON-NLS-1$
		if(authenticationFile != null) {
            authenticationFile = authenticationFile.trim();
        }
        URI baseConfigURI = op.getPE().getApplicationDirectory().toURI();
		auth = AuthHelper.getAuthenticator(authenticationType, PathConversionHelper.convertToAbsPath(baseConfigURI, authenticationFile), authenticationProperties);

		rc = new RetryController(maxRetries, retryDelay);
		hasOutputPort = op.getStreamingOutputs().size() == 1;
		
		if((!headerContentType.equals(MIME_FORM) && !headerContentType.equals(MIME_JSON))) {
			if(getInput(0).getStreamSchema().getAttributeCount() != 1) 
				throw new Exception("Only a single attribute is permitted in the input stream for content type \"" + headerContentType + "\""); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		trace.log(TraceLevel.INFO, "URL: " + url); //$NON-NLS-1$
	}

	@ContextCheck(compile=true)
	public static boolean checkAuthParams(OperatorContextChecker occ) {
		return occ.checkDependentParameters("authenticationFile", "authenticationType") //$NON-NLS-1$ //$NON-NLS-2$
				&& occ.checkDependentParameters("authenticationProperty", "authenticationType") //$NON-NLS-1$ //$NON-NLS-2$
				;
	}

	@Override
	public synchronized void process(StreamingInput<Tuple> stream, Tuple tuple) throws Exception {
		rc.readSuccess();
		StreamSchema schema = stream.getStreamSchema();

		HTTPRequest req = new HTTPRequest(url);
		req.setHeader("Content-Type", headerContentType); //$NON-NLS-1$
		req.setType(RequestType.POST);

		if(headerContentType.equals(MIME_FORM)) {
			Map<String, String> params = new HashMap<String, String>();
			for (Attribute attribute : schema) {
				params.put(attribute.getName(), tuple.getObject(attribute.getName()).toString());
			}
			req.setParams(params);
		}
		else if(headerContentType.equals(MIME_JSON)){
			JSONEncoding<JSONObject, JSONArray> je = EncodingFactory.getJSONEncoding();
			req.setParams(je.encodeAsString(tuple));
		}
		else {
			req.setParams(tuple.getObject(schema.getAttribute(0).getName()).toString());
		}

		HTTPResponse resp = null;

		Throwable t = null;
		while(true) {
			try {
				if(trace.isLoggable(TraceLevel.TRACE))
					trace.log(TraceLevel.TRACE, "Sending request: " + req.toString()); //$NON-NLS-1$
				
				resp = req.sendRequest(auth);
				
				if(trace.isLoggable(TraceLevel.TRACE))
					trace.log(TraceLevel.TRACE, "Got response: " + resp.toString()); //$NON-NLS-1$
				rc.readSuccess();
				break;
			}catch(Exception e) {
				t=e;
				rc.readException();
				trace.log(TraceLevel.ERROR, "Exception", e); //$NON-NLS-1$
			}
			if(!shutdown && rc.doRetry()) {
				trace.log(TraceLevel.ERROR, "Sleeping " + retryDelay +" seconds"); //$NON-NLS-1$ //$NON-NLS-2$
				sleepABit(rc.getSleep());
			}
			else {
				break;
			}
		}

		if(trace.isLoggable(TraceLevel.INFO))
			trace.log(TraceLevel.INFO, "Response code: " +  //$NON-NLS-1$
						((resp!=null) ? resp.getResponseCode() : -1) 
					);

		if(!hasOutputPort) 
			return;

		StreamingOutput<OutputTuple> op = getOutput(0);
		OutputTuple otup = op.newTuple();
		
		if(resp == null) {
			otup.setString("errorMessage",  //$NON-NLS-1$
					t == null ? "Unknown error." : t.getMessage() //$NON-NLS-1$
					);			
			otup.setInt("responseCode", -1); //$NON-NLS-1$
		}
		else {
			if(trace.isLoggable(TraceLevel.DEBUG))
				trace.log(TraceLevel.DEBUG, "Response: " + resp.toString()); //$NON-NLS-1$
			
			if(resp.getErrorStreamData()!=null)
				otup.setString("errorMessage", resp.getErrorStreamData()); //$NON-NLS-1$
	
			if(resp.getOutputData() != null) {
				otup.setString("data", resp.getOutputData()); //$NON-NLS-1$
				otup.setInt("dataSize", resp.getOutputData().length()); //$NON-NLS-1$
			}
	
			otup.setInt("responseCode", resp.getResponseCode()); //$NON-NLS-1$
		}
		if(trace.isLoggable(TraceLevel.DEBUG))
			trace.log(TraceLevel.DEBUG, "Sending tuple: " + otup.toString()); //$NON-NLS-1$
		op.submit(otup);
	}

	void sleepABit(double seconds) throws InterruptedException {
		long end  = System.currentTimeMillis() + (long)(seconds * 1000);

		while(!shutdown && System.currentTimeMillis() < end) {
			Thread.sleep(100);
		}
	}

	@Override
	public void shutdown() throws Exception {
		shutdown = true;
	}
	
	

	public static final String DESC = 
			"This operator sends incoming tuples to the specified HTTP server as part of a POST request." + //$NON-NLS-1$
			" A single tuple will be sent as a body of one HTTP POST request." + //$NON-NLS-1$
			" All attributes of the tuple will be serialized and sent to the server." + //$NON-NLS-1$
			" Certain authentication modes are supported." + //$NON-NLS-1$
			" Tuples are sent to the server one at a time in order of receipt. If the HTTP server cannot be accessed, the operation" + //$NON-NLS-1$
			" will be retried on the current thread and may temporarily block any additional tuples that arrive on the input port." + //$NON-NLS-1$
			" By default, the data is sent in application/x-www-form-urlencoded UTF-8 encoded format."  + //$NON-NLS-1$
	    CONSISTENT_CUT_INTRODUCER +
			"\\nThis operator cannot be placed at the start of a consistent region." //$NON-NLS-1$
		;

}

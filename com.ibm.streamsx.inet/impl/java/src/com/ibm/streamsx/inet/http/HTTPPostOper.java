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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.ibm.json.java.JSON;
import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
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
import com.ibm.streamsx.inet.messages.Messages;

@InputPorts(@InputPortSet(cardinality=1, 
			description="By default, all attributes of the input stream are sent as POST data to the specified HTTP server."))
@OutputPorts(@OutputPortSet(cardinality=1, optional=true, 
			description="Emits a tuple containing the reponse received from the server and assignments automatically forwarded from the input. " +
		     "Tuple structure must conform to the [HTTPResponse] type specified in this namespace. " + 
                     "Additional attributes with corresponding input attributes will be forwarded before the POST request."

))
@PrimitiveOperator(name=HTTPPostOper.OPER_NAME, description=HTTPPostOper.DESC)
@Libraries(value={"opt/downloaded/*"})
@Icons(location32="icons/HTTPPost_32.gif", location16="icons/HTTPPost_16.gif")
public class HTTPPostOper extends AbstractOperator  
{
	/**
     * How the incoming tuple is
     * processed into a POST request.
     *
     */
    private enum ProcessType {
        
        /**
         * Tuple is converted to application/x-www-form-urlencoded
         */
        TUPLE_FORM,
        
        /**
         * Tuple is converted to application/json
         * using the standard encoding.
         */
        TUPLE_JSON,
        
        /**
         * Input schema is tuple<rstring jsonString>
         * passed directly as application/json;
         */
        PURE_JSON,
        
        /**
         * Input schema has one rstring jsonString
         * at the top level. E.g. tuple<int32 a, int64 b, rstring jsonString>.
         * In this case a JSON object is created from jsonString
         * and then keys a and b are added with the tuple's value
         * converted to its JSON representation.
         */
        MIX_JSON,
        
        /**
         * Input schema has a single attribute
         * and its string value is sent as the POST
         * body.
         */
        SINGLE_ATTRIBUTE,

        ;
    }



    static final String CLASS_NAME="com.ibm.streamsx.inet.http.HTTPPostOper";
	static final String OPER_NAME = "HTTPPost";
        public static final String CONSISTENT_CUT_INTRODUCER="\\n\\n**Behavior in a consistent region**\\n\\n";
	
	static final String 
			MIME_JSON = "application/json",  
			MIME_FORM = "application/x-www-form-urlencoded";

	private static Logger trace = Logger.getLogger(CLASS_NAME);


	private double retryDelay = 3;
	private double connectionTimeout = 60.0;
	private int maxRetries = 3;
	private String url = null;
	private IAuthenticate auth = null;
	private String authenticationType = "none", authenticationFile = null;
	private RetryController rc = null;

	private boolean hasOutputPort = false;
	private boolean shutdown = false;
	private List<String> authenticationProperties = new ArrayList<String>();
	
	private String headerContentType = MIME_FORM;
	private boolean acceptAllCertificates = false;
        private Set<String>includeAttributesSet = null;   // attributes to include in the http request
	
	private String keyStoreFile = null, keyStorePassword = null;
	/**
	 * How the input tuple is processed.
	 */
	private ProcessType processType = ProcessType.SINGLE_ATTRIBUTE;

	private List<String> extraHeaders = new ArrayList<String>();

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
	@Parameter(optional=true, description="Path to .jks file used for server and client authentication")
	public void setKeyStoreFile(String val){
		keyStoreFile = val;
	}
	@Parameter(optional=true, description="Password for the keyStore and the keys it contains")
	public void setKeyStorePassword(String val){
		keyStorePassword = val;
	}
	@Parameter(optional=true, description="Maximum number of retries in case of failures/disconnects.")
	public void setMaxRetries(int val) {
		this.maxRetries = val;
	}
	@Parameter(optional=true, description="Wait time between retries in case of failures/disconnects.")
	public void setRetryDelay(double val) {
		this.retryDelay = val;
	}
	@Parameter(optional=true, description="Optional parameter specifies amount of time (in seconds) that the operator waits for the connection for to be established. Default is 60.")
	public void setConnectionTimeout(double val) {
		this.connectionTimeout = val;
	}

	@Parameter(optional=true, description="Set the content type of the HTTP request. " +
			" If the value is set to \\\""+MIME_JSON+"\\\" then the entire tuple is sent in JSON format using SPL's standard tuple to JSON encoding, "
			        + "if the input schema is `tuple<rstring jsonString>` then `jsonString` is assumed to already be JSON and its value is sent as the content. " +
			" Default is \\\""+MIME_FORM+"\\\"." +
			" Note that if a value other than the above mentioned ones is specified, the input stream can only have a single attribute.")
	public void setHeaderContentType(String val) {
		this.headerContentType = val;
	}
	@Parameter(optional=true,
			description="Extra headers to send with request, format is \\\"Header-Name: value\\\".")
	public void setExtraHeaders(List<String> val) {
		this.extraHeaders = val;
	}
	@Parameter(optional=true, 
			description="Accept all SSL certificates, even those that are self-signed. " +
			"Setting this option will allow potentially insecure connections. Default is false.")
	public void setAcceptAllCertificates(boolean val) {
		this.acceptAllCertificates = val;
	}
	@Parameter(optional=true, 
			description="Specify attributes used to compose the POST. " +
					"Comma separated list of attribute names that will be posted to the url. " +
					"The parameter is invalid if HeaderContentType is " +
		                        "not \\\"" + MIME_JSON + "\\\" or \\\"" + MIME_FORM + "\\\". " +
					"Default is to send all attributes." 
					) 
	public void setInclude(List<TupleAttribute<Tuple, ?>> include) {
		includeAttributesSet = new HashSet<String>();
		for (TupleAttribute<Tuple, ?> postAttr : include) {
	            String attrName = postAttr.getAttribute().getName();		
	            includeAttributesSet.add(attrName);
		}
		
	}
        // includeAttribute invalid if HeaderContextType is something other thatn MIME_JSON, MIME_FORM.
        @ContextCheck(compile = true)
	public static void checkIncludeAttributesDependency(OperatorContextChecker checker) {
	    OperatorContext operatorContext = checker.getOperatorContext();
	    String header;
	    Set<String>parameterNames = operatorContext.getParameterNames();
	    if (!parameterNames.contains("includeAttributes")) return;
	    if (!parameterNames.contains("headerContextType")) return;	    


	    List<String>headers = operatorContext.getParameterValues("headerContextType");
	    if (headers.size() == 0) return;
	    header = headers.get(0);
	    if (header.equals(MIME_FORM) || header.equals(MIME_JSON)) return;
	    checker.setInvalidContext(Messages.getString("PARAM_HEADER_CHECK1"), new String[] {OPER_NAME, header});
    }
        
    @ContextCheck(compile=true)
    public static void checkKeyStoreParameters(OperatorContextChecker checker){
    	OperatorContext operatorContext = checker.getOperatorContext();
    	Set<String>parameterNames = operatorContext.getParameterNames();
    	
    	boolean hasFile = parameterNames.contains("keyStoreFile");
    	boolean hasPassword = parameterNames.contains("keyStorePassword");
    	
    	//The pair of these parameters is optional, we either need both to be present or neither of them
    	if(hasFile ^ hasPassword)
    	{
    		checker.setInvalidContext(Messages.getString("PARAM_TRUST_STORE_CHECK2"), new String[] {OPER_NAME});
    	}
    }
        
	//consistent region checks
	@ContextCheck(compile = true)
	public static void checkInConsistentRegion(OperatorContextChecker checker) {
		ConsistentRegionContext consistentRegionContext = 
				checker.getOperatorContext().getOptionalContext(ConsistentRegionContext.class);
		
		if(consistentRegionContext != null && consistentRegionContext.isStartOfRegion()) {
			checker.setInvalidContext(Messages.getString("CONSISTENT_CHECK_1"), new String[] {HTTPPostOper.OPER_NAME});
		}
	}
	
	@Override
	public void initialize(OperatorContext op) throws Exception  {
		super.initialize(op);    

		trace.log(TraceLevel.INFO, "Using authentication type: " + authenticationType);
		if(authenticationFile != null) {
            authenticationFile = authenticationFile.trim();
        }
        URI baseConfigURI = op.getPE().getApplicationDirectory().toURI();
		auth = AuthHelper.getAuthenticator(authenticationType, PathConversionHelper.convertToAbsPath(baseConfigURI, authenticationFile), authenticationProperties);
		
		keyStoreFile = PathConversionHelper.convertToAbsPath(baseConfigURI, keyStoreFile);
		
		rc = new RetryController(maxRetries, retryDelay);
		hasOutputPort = op.getStreamingOutputs().size() == 1;
		
		final StreamSchema inputSchema = getInput(0).getStreamSchema();
		if((!headerContentType.equals(MIME_FORM) && !headerContentType.equals(MIME_JSON))) {
			if(inputSchema.getAttributeCount() != 1) 
				throw new Exception("Only a single attribute is permitted in the input stream for content type \"" + headerContentType + "\"");
		}
	
		if (headerContentType.equals(MIME_FORM))
		    processType = ProcessType.TUPLE_FORM;		
		else if (headerContentType.equals(MIME_JSON)) {
		    processType = ProcessType.TUPLE_JSON;
		    
		    // Handle jsonString as JSON, not re-encode it.
		    if (inputSchema.getAttributeCount() == 1) {
		        Attribute attr = inputSchema.getAttribute(0);
		        if (isStandardJsonAttribute(attr)) {
		            // Schema is just tuple<rstring jsonString>
		            processType = ProcessType.PURE_JSON;
		        }
		    }
		    else {
		        // A top-level attribute being jsonString
		        for (Attribute attr : inputSchema) {
		            if (isStandardJsonAttribute(attr)) {
		                processType = ProcessType.MIX_JSON;
		                break;
		            }
		        }
		    }
		}
		trace.log(TraceLevel.INFO, "URL: " + url);
	}
	
	/**
	 * Is an attribute SPL's standard representation for JSON.
	 */
	private static boolean isStandardJsonAttribute(Attribute attr) {
        return attr.getName().equals("jsonString")
                && attr.getType().getMetaType() == MetaType.RSTRING;
	}

	@ContextCheck(compile=true)
	public static boolean checkAuthParams(OperatorContextChecker occ) {
		return occ.checkDependentParameters("authenticationFile", "authenticationType")
				&& occ.checkDependentParameters("authenticationProperty", "authenticationType")
				;
	}

	@Override
	public synchronized void process(StreamingInput<Tuple> stream, Tuple tuple) throws Exception {
		rc.readSuccess();
		StreamSchema schema = stream.getStreamSchema();

		HTTPRequest req = new HTTPRequest(url);
		req.setHeader("Content-Type", headerContentType);
		req.setMethod(HTTPMethod.POST);
		req.setInsecure(acceptAllCertificates);
		req.setConnectionTimeout(connectionTimeout);
		
		if(keyStoreFile != null)
			req.initializeKeyStore(keyStoreFile, keyStorePassword);
		
		trace.log(TraceLevel.TRACE, "Set connectionTimeout: " + connectionTimeout);					

		switch (processType) {

		case TUPLE_FORM:
		{
            Map<String, String> params = new HashMap<String, String>();
            
            for (Attribute attribute : schema) {
            	if (isAttributeToPost(attribute.getName())) {
            		params.put(attribute.getName(), tuple.getObject(attribute.getName()).toString());
            	}
            }
            req.setParams(params);	
            break;
		}
		case TUPLE_JSON:
		{
            JSONEncoding<JSONObject, JSONArray> je = EncodingFactory.getJSONEncoding();
            JSONObject jo = je.encodeTuple(tuple);
            
            for (Iterator<String> it = jo.keySet().iterator(); it.hasNext();) {
            	if (!isAttributeToPost(it.next())) {
            		it.remove();
            	}            	
            }
            req.setParams(jo.serialize());          
            break;
		}
	    case PURE_JSON:
	    {
             req.setParams(tuple.getString(0));
	         break;
	    }
	    case MIX_JSON:
	    {
	        JSONEncoding<JSONObject, JSONArray> je = EncodingFactory.getJSONEncoding();
	        
	        JSONObject json = (JSONObject) JSON.parse(tuple.getString("jsonString"));
	        for (Attribute attr : tuple.getStreamSchema()) {
	            if (attr.getName().equals("jsonString"))
	                continue;
	            
	            json.put(attr.getName(), je.getAttributeObject(tuple, attr));
	        }
	        req.setParams(json.serialize());
	        break;
	    }
	    case SINGLE_ATTRIBUTE:
	    {
	        req.setParams(tuple.getObject(schema.getAttribute(0).getName()).toString());
	        break;
	    }
		
		}

		Map<String, String> headerMap = HTTPUtils.getHeaderMap(extraHeaders);
		for(Map.Entry<String, String> header : headerMap.entrySet()) {
			req.setHeader(header.getKey(), header.getValue());
		}

		HTTPResponse resp = null;

		Throwable t = null;
		while(true) {
			try {
				if(trace.isLoggable(TraceLevel.TRACE))
					trace.log(TraceLevel.TRACE, "Sending request: " + req.toString());
				
				resp = req.sendRequest(auth);
				if(trace.isLoggable(TraceLevel.TRACE))
				{
					trace.log(TraceLevel.TRACE, "Got response: " + resp.toString());
				}
				rc.readSuccess();
				break;
			}catch(Exception e) {
				t=e;
				rc.readException();
				trace.log(TraceLevel.ERROR, "Exception", e);
			}
			if(!shutdown && rc.doRetry()) {
				trace.log(TraceLevel.ERROR, "Sleeping " + retryDelay +" seconds");
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
		otup.assign(tuple);    // propagate attributes input -> output
		
		if(resp == null) {
			otup.setString("errorMessage", 
					t == null ? "Unknown error." : t.getMessage()
					);			
			otup.setInt("responseCode", -1);
		} else {
			if(trace.isLoggable(TraceLevel.DEBUG))
				trace.log(TraceLevel.DEBUG, "Response: " + resp.toString());
			
			if(resp.getErrorStreamData()!=null){
				otup.setString("errorMessage", resp.getErrorStreamData());
			}
	
			if(resp.getOutputData() != null) {
				otup.setString("data", resp.getOutputData());
				otup.setInt("dataSize", resp.getOutputData().length());
			}
	
			otup.setInt("responseCode", resp.getResponseCode());
		}
		if(trace.isLoggable(TraceLevel.DEBUG))
			trace.log(TraceLevel.DEBUG, "Sending tuple: " + otup.toString());
		op.submit(otup);
	}

	void sleepABit(double seconds) throws InterruptedException {
		long end  = System.currentTimeMillis() + (long)(seconds * 1000);

		while(!shutdown && System.currentTimeMillis() < end) {
			Thread.sleep(100);
		}
	}
	
	boolean isAttributeToPost(String attributeName) {
		if (includeAttributesSet == null) {
			return true;
		}
		return(includeAttributesSet.contains(attributeName));
	}

	@Override
	public void shutdown() throws Exception {
		shutdown = true;
	}
	
	

	public static final String DESC = 
			"This operator sends incoming tuples to the specified HTTP server as part of a POST request." +
			" A single tuple will be sent as a body of one HTTP POST request." +
			" Certain authentication modes are supported." +
			" Tuples are sent to the server one at a time in order of receipt. If the HTTP server cannot be accessed, the operation" +
			" will be retried on the current thread and may temporarily block any additional tuples that arrive on the input port." +
			" By default, the data is sent in application/x-www-form-urlencoded UTF-8 encoded format."  +
	    CONSISTENT_CUT_INTRODUCER +
			"\\nThis operator cannot be placed at the start of a consistent region." +
			"\\n\\n**This operator will be deprecated.** Use HTTPRequest operator instead."
		;

}

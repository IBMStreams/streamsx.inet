/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017
*/
package com.ibm.streamsx.inet.rest.ops;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.apache.log4j.Logger;

import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.types.RString;
import com.ibm.streamsx.inet.rest.servlets.ReqWebMessage;

/**
 * <p>
 * HTTPRequestProcess - Enable Streams to process web requests . A request arrives via the web which injects a
 * tuple out the output port; processing happens; input port receives the processing results which are
 * communicated to the orgininating web requester. This is a gateway between web requests and Streams processing of the requests.
 * </p>
 * <p>
 * This relies on an embedded Jetty server. The Jetty portion: accepts a request from the web, suspends the web request
 * while Stream's processes the request, continues the web connection when Streams completes the processing and finally responds to 
 * the original request. 
 * </p>
 * <p>
 * The following is a brief description of the classes and their interaction. 
 * </p>
 * <dl>
 * <dt>{@link HTTPReqeustProcess}</dt>
 * <dd>
 * Operator Entry.  Web requests for Streams are injected into the Streams via the operators Output port. Responses, 
 * completed requests enter via the Input port. The request and response are packaged into ReqWebMessage objects.
 * </dd>
 * <dt>ReqWebServer </dt>
 * <dd>
 * Invoked by HTTPTupleRequest, starts the Jetty Web server. Sets up callback framework to accept requests {@link ReqHandlerInterface} from the web to Streams.
 * </dd>
 * <dt>ReqWebMessage</dt>
 * <dd>
 * Bridge between the Streams and WWW portion of the processing.
 * </dd>
 * <dt>ReqHandlerInterface</dt>
 * <dd>
 * Enable request from web into Streams.
 * </dd>
 * <dt>ReqHandlerSuspend extends AbstractHandler</dt>
 * <dd>
 * Waits around for an answer
 * </dd>
 * </dl>
 * <p>
 * This operator generates a key attribute on the output port and expects the same key attribute 
 * value on the input port, this is correlation key. If keys is corrupted, no response will be generated, 
 * the request will time out. 
 * </p> 
 *
 */

@PrimitiveOperator(name = "HTTPRequestProcess", description = RequestProcess.DESC)
@InputPorts({
		@InputPortSet(description = "Response to be returned to the web requestor.", cardinality = 1, optional = false, controlPort=true, windowingMode = WindowMode.NonWindowed, windowPunctuationInputMode = WindowPunctuationInputMode.Oblivious)})
		//@InputPortSet(description = "Optional input ports", optional = true, windowingMode = WindowMode.NonWindowed, windowPunctuationInputMode = WindowPunctuationInputMode.Oblivious) })
@OutputPorts({
		@OutputPortSet(description = "Request from web to process.", cardinality = 1, optional = false, windowPunctuationOutputMode = WindowPunctuationOutputMode.Generating) })
@Icons(location32="icons/HTTPTupleRequest_32.jpeg", location16="icons/HTTPTupleRequest_16.jpeg")

//		@OutputPortSet(description = "Optional output ports", optional = true, windowPunctuationOutputMode = WindowPunctuationOutputMode.Generating) })
public class RequestProcess extends ServletOperator {
	static Logger trace = Logger.getLogger(RequestProcess.class.getName());

	
	// communication
	public static final String defaultContentTypeAttributeName = "contentType";	
	public static final String defaultContextPathAttributeName = "contextPath";		
	public static final String defaultHeaderAttributeName = "header";	
	public static final String defaultKeyAttributeName = "key";
	public static final String defaultRequestAttributeName = "request";
	private static final String defaultResponseAttributeName = "response";
	public static final String defaultUrlAttributeName = "url";
	public static final String defaultMethodAttributeName = "method";	
	public static final String defaultPathInfoAttributeName = "pathInfo";	
	private static final String defaultStatusAttributeName = "status";	
	private static final String defaultStatusMessageAttributeName = "statusMessage";	
	private static final String defaultContext = "/streams";
	private static final String defaultJsonStringAttributeName = "jsonString";
	private static final int defaultPort = 8080;	
	private static final double defaultTimeout = 15.0f;	
	
	static final String DESC = "Operator accepts a web request and generates corresponding response.  The request is injected into "
		+ "streams on the output port, the input port receives the response."
	        + "This enables a developer to process HTTP form's and REST calls. The request arrives on the output port, results are " 
	        + "presented on the input port."
		+ "The request is coorolated to the response with an attribute 'key' that arrives with the request parameters' on the output port "
	        + "and must accompany the response on the input port."
		+ "\\n\\n" 
	        + "The URLs defined by this operator are:\\n"
	        + "* *prefix*`/ports/analyze/`*port index*`/` - Injects a tuple into the output and the response is taken from the matching tuple on the input port.\\n"
	        + "* *prefix*`/ports/input/`*port index*`/info` - Output port meta-data including the stream attribute names and types (content type `application/json`).\\n"
	        + "\\nThe *prefix* for the URLs is:\\n"
	        + "* *context path*`/`*base operator name* - When the `context` parameter is set.\\n"
	        + "* *full operator name* - When the `context` parameter is **not** set.\\n"
	        + "\\n"
	        + "For the `analyze` path any HTTP method can be used and any sub-path. For example with a context of "
	        + "`api` and operator name of `Bus` then `api/Bus/ports/analyze/0/get_location` is valid."
	        + "\\n\\n"
			+ "Input and output ports have two possible formats: tuple and json. With tuple format, each web input fields is mapped to an attribute. "
			+ "Json format has one attribute ('jsonString'), each web field is mapped to a json object field. "
			+ "\\n\\n"
			+ "The jsonString object will be "
			+ "populated with all the fields. The default attribute names can be"
			+ "overridden for tuple. "
			+ "\\n\\n" 
			+ "The operator handles two flavors of http requests, forms and REST. In the case of forms, webpages can be served up from the contextResourceBase, "
                        + "this can be to static html or template. . Refer to the spl example for a form processed by the operator using a template to format the response."
			+ "\\n\\n "
			+ "For the input port (the web response), only the 'key' is mandatory for both json and tuple. The following lists the default values if the field or attribute is not provided. "
			+ "\\n"
			+ "* rstring response : 0 length response.  \\n"
			+ "* int32 statusCode : 200 (OK) \\n"
			+ "* rstring statusMessage :  not set \\n"
			+ "* rstring contentType : '" + ReqWebMessage.defaultResponseContentType + "'. \\n"
			+ "* Map<rstring,rstring> headers : No headers provided \\n "
			+ "\\n\\n "
			+ "# Notes:\\n\\n "
			+ "* The 'key' attribute on the output and input port's are correlated. Losing the correlation loses the request.\\n "
			+ "* If the input port's response key cannot be located the web request will timeout, metrics will be incremented.\\n "
			+ "* If the input jsonString value cannot be converted to an jsonObject, no response will be generated and web request will timeout.\\n "
			+ "* Only the first input port's key will produce a web response.\\n "
			+ "* The 'jsonString' attribute json field names are the default attribute names.\\n "
			+ "* context/pathInfo relationship : A request's context path beyond the base is accepted, the 'pathInfo' attribute will have path beyond the base.  "
			+ "  If the context path is */work* requests to */work/translate* will have a 'pathInfo' of */translate* and requests "
			+ "  to */work/translate/speakeasy* will have a 'pathInfo' of */translate/speakeasy*. "
			+ "\\n\\n";


	
	
	static final String PORT_DESC = "Port the that requests will be recieved on, default: \\\"" + defaultPort + "\\\".";
	static final String CONTEXT_DESC = "Specify a URL context path base that requests will be accepted. Request's path beyond the base will be accepted, the 'pathInfo' attribute will have path beyond the base. "
			+ "If the context path is '/work' requests to '/work/translate' will have 'pathInfo' of '/translate and requests to '/work/translate/speakeasy' with hava 'pathInfo' of '/translate/speakeasy', default: \\\"" + defaultContext  + "\\\". ";

	static final String WEBTIMEOUT_DESC = "Number of seconds to wait for the web request to be processed by Streams, default: \\\"" + defaultTimeout + "\\\".  ";

	// common to output/input
	static final String KEY_DESC = " Input and output port's corrolation key. The values is expected to be unchanged between the input and output, default: \\\"" + defaultKeyAttributeName + "\\\". ";	
	// output port
	static final String REQUESTATTRNAME_DESC = "Output port's attribute name with the web request (body of the web request), default \\\"" + defaultRequestAttributeName + "\\\".  ";
	static final String METHODATTRNAME_DESC = "Output ports's attribute name with the request method (PUT, GET, POST), default: \\\"" + defaultMethodAttributeName + "\\\".  ";
	static final String PATHINFOATTRNAME_DESC = "Output ports's attribute of the content path below the base, default \\\"" + defaultPathInfoAttributeName + "\\\".  ";
	static final String CONTENTTYPEATTRNAME_DESC = "Output port's attribute with content-type will be provided in, default: \\\"" + defaultContentTypeAttributeName + "\\\".  ";
	static final String HEADERATTRNAME_DESC = "Output port's web request headers, in the form of a objects<name, value>, default: \\\"" + defaultHeaderAttributeName + "\\\".  ";
	
	// input port
	private String responseAttributeName = defaultResponseAttributeName;
	private String jsonStringAttributeName = defaultJsonStringAttributeName; 
	private String responseJsonStringAttributeName = defaultJsonStringAttributeName;
	private String statusAttributeName = defaultStatusAttributeName;	
	private String statusMessageAttributeName = defaultStatusMessageAttributeName;
	private String responseHeaderAttributeName = defaultHeaderAttributeName;	
	private String responseContentTypeAttributeName = defaultContentTypeAttributeName;
	
	private static final String RESPONSEATTRNAME_DESC = "Input port's attribute response (body of the web response), default:  \\\"" + defaultResponseAttributeName + "\\\".  ";
	private static final String RESPONSEJSONSTRINGATTRNAME_DESC = "Input port's json results (complete response), default:  \\\"" + defaultJsonStringAttributeName + "\\\".  ";	
	private static final String STATUSATTRNAME_DESC = "Input port's attribute web status, default:  \\\"" + defaultStatusAttributeName + "\\\".  ";
	private static final String STATUSMESSAGEATTRNAME_DESC = "Input port's web status message response, when the 'status' value is >= 400 (SC_BAD_REQUEST), default:  \\\"" + defaultStatusMessageAttributeName + "\\\".  ";
	// TODO is this used ??? 
	private static final String RESPONSECONTENTTYPE_DESC = "Input port's web response content type, default: \\\"" + defaultContentTypeAttributeName + "\\\".  ";
	private static final String RESPONSEHEADERATTRNAME_DESC = "Input port's web response header objects<name,value>, default: \\\"" + defaultHeaderAttributeName + "\\\".  ";


	/*int port = defaultPort;
	String webContext = defaultContext; */
	double webTimeout = defaultTimeout;
	
	/**
	 * Initialize this operator. Called once before any tuples are processed.
	 * 
	 * @param context
	 *            OperatorContext for this operator.
	 * @throws Exception
	 *             Operator failure, will cause the enclosing PE to terminate.
	 */
	private Map<Long, ReqWebMessage> activeMessages;
	private String keyAttributeName = defaultKeyAttributeName;
	private String requestAttributeName = defaultRequestAttributeName;
	private String methodAttributeName = defaultMethodAttributeName; // get/put/del/
	private String pathInfoAttributeName = defaultPathInfoAttributeName;		
	private String headerAttributeName = defaultHeaderAttributeName;
	private String contentTypeAttributeName = defaultContentTypeAttributeName;
	
	//private Boolean jsonStringResponse = false;
	private Collection<String> activeColumns = null;
	
	/*
	 * Count some things
	 */
	private Metric nMessagesReceived;
	private boolean jsonFormatInPort = false ;   // only one column on input port jsonString
	private boolean jsonFormatOutPort = false;   // only one column on output port jsonString
	private static Metric nMessagesResponded;
	private static Metric nRequestTimeouts;	
	private static Metric nMissingTrackingKey;
	private static Metric nActiveRequests;	
	
	/**
	 * Conduit object between operator and servlet.
	 * [0] - activeMessages
	 * [1] - function to create output tuple.
	 Notes - 
	 	This is the heart of the 'conduit' which uses Function pointers. The function
	 	pointer is tupleCreated invokes the initiateRequestFrom web via an apply function, 
	 	which can be found in the injectWtihResponse.java.  
	 	** TODO * need a complete explanation. 
	 	The initalization is getting gets the iniitRequestFromWeb function handle to 
	 	the servlet in order that messages arrive at the servlet get to the Streams code. 
	 	
	 */
	private Function<ReqWebMessage,OutputTuple> tupleCreator = this::initiateRequestFromWeb;

	@Override
	public synchronized void initialize(OperatorContext context) throws Exception {
		// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(context);

		activeMessages = Collections.synchronizedMap(new HashMap<>());
		
		jsonFormatOutPort = (getOutput(0).getStreamSchema().getAttributeCount() == 1) && (jsonStringAttributeName.equals(getOutput(0).getStreamSchema().getAttributeNames().toArray()[0])); 
		for (int idx = 0; getOutput(0).getStreamSchema().getAttributeCount() != idx; idx++) {
			trace.info(String.format(" -- OutPort@initalize attribute[%d]:%s ", idx, (getOutput(0).getStreamSchema().getAttributeNames().toArray()[idx])));		
		}		
		jsonFormatInPort = (getInput(0).getStreamSchema().getAttributeCount() == 1) && (responseJsonStringAttributeName.equals(getInput(0).getStreamSchema().getAttributeNames().toArray()[0]));
		for (int idx = 0; getInput(0).getStreamSchema().getAttributeCount() != idx; idx++) {
			trace.info(String.format(" -- InPort@initalize attribute[%d]:%s ", idx, (getInput(0).getStreamSchema().getAttributeNames().toArray()[idx])));		
		}


		if (jsonFormatOutPort) {
			Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " single column output ");
		} else {			
		// key, out port
		if (getOutput(0).getStreamSchema().getAttribute(keyAttributeName) == null) {
			throw new Exception("Could not detect the data field for output port 0. "
					+ "Specify a valid value for \"keyAttributeName\"");
		}
		MetaType keyParamType = getOutput(0).getStreamSchema().getAttribute(keyAttributeName)
				.getType().getMetaType();
		if (keyParamType != MetaType.INT64)
			throw new Exception(
					"Only types \"" + MetaType.INT64 + "\" allowed for param " + keyAttributeName + "\"");
		
		
		// request, out port
		if (getOutput(0).getStreamSchema().getAttribute(requestAttributeName) == null) {
			throw new Exception("Could not detect the data field for output port 0. "
					+ "Specify a valid value for \"requestAttributeName\"");
		}
		MetaType requestParamType = getOutput(0).getStreamSchema().getAttribute(requestAttributeName).getType()
				.getMetaType();
		if (requestParamType != MetaType.USTRING && requestParamType != MetaType.RSTRING)
			throw new Exception("Only types \"" + MetaType.USTRING + "\" and \"" + MetaType.RSTRING
					+ "\" allowed for param " + requestAttributeName + "\"");

		// header, out port
		if (getOutput(0).getStreamSchema().getAttribute(headerAttributeName) == null) {
			headerAttributeName = null;
		} else {
			MetaType headerParamType= getOutput(0).getStreamSchema().getAttribute(headerAttributeName).getType()
					.getMetaType();
			if (headerParamType != MetaType.MAP)
				throw new Exception(
						"Only type of \"" + MetaType.MAP + "\" allowed for param " + headerAttributeName + "\"");
		}
		// method, out port
		if (getOutput(0).getStreamSchema().getAttribute(methodAttributeName) == null) {
			methodAttributeName = null;
		} else {
			MetaType methodParamType = getOutput(0).getStreamSchema().getAttribute(methodAttributeName).getType()
					.getMetaType();
			if (methodParamType != MetaType.USTRING && methodParamType != MetaType.RSTRING)
				throw new Exception("Only types \"" + MetaType.USTRING + "\" and \"" + MetaType.RSTRING
						+ "\" allowed for param " + methodAttributeName + "\"");
		}
		// pathInfo, out port
		if (getOutput(0).getStreamSchema().getAttribute(pathInfoAttributeName) == null) {
			pathInfoAttributeName = null;
		} else {
			MetaType pathInfoParamType = getOutput(0).getStreamSchema().getAttribute(pathInfoAttributeName).getType()
					.getMetaType();
			if (pathInfoParamType != MetaType.USTRING && pathInfoParamType != MetaType.RSTRING)
				throw new Exception("Only types \"" + MetaType.USTRING + "\" and \"" + MetaType.RSTRING
						+ "\" allowed for param " + pathInfoAttributeName + "\"");
		}
		// contentType, out port
		if (getOutput(0).getStreamSchema().getAttribute(contentTypeAttributeName) == null) {
			contentTypeAttributeName = null;
		} else {
			MetaType methodParamType = getOutput(0).getStreamSchema().getAttribute(contentTypeAttributeName).getType()
					.getMetaType();
			if (methodParamType != MetaType.USTRING && methodParamType != MetaType.RSTRING)
				throw new Exception("Only types \"" + MetaType.USTRING + "\" and \"" + MetaType.RSTRING
						+ "\" allowed for param " + contentTypeAttributeName + "\"");
		}
		}
		if (jsonFormatInPort) {
			Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " single column input ");
		} else {
		// key, in port
			if (getOutput(0).getStreamSchema().getAttribute(keyAttributeName) == null) {
				throw new Exception("Could not detect the data field for output port 0. "
					+ "Specify a valid value for \"keyAttributeName\"");
		}
		MetaType keyResponseParamType = getOutput(0).getStreamSchema().getAttribute(keyAttributeName)
				.getType().getMetaType();
		if (keyResponseParamType != MetaType.INT64)
			throw new Exception(
					"Only types \"" + MetaType.INT64 + "\" allowed for param " + keyAttributeName + "\"");
		
		// response, in port - getting Null exception on the else added more protection.
		if ((getInput(0).getStreamSchema().getAttribute(responseHeaderAttributeName) == null) || 
					(getInput(0).getStreamSchema().getAttribute(responseHeaderAttributeName).getType() == null))		
		{
			responseHeaderAttributeName = null;
		} else {
			MetaType headerParamType = getOutput(0).getStreamSchema().getAttribute(responseHeaderAttributeName).getType()
					.getMetaType();
			if (headerParamType != MetaType.MAP)
				throw new Exception(
						"Only type of \"" + MetaType.MAP + "\" allowed for param " +responseHeaderAttributeName + "\"");			
		}
		// status, in port
		if (getInput(0).getStreamSchema().getAttribute(statusAttributeName) == null) {
			statusAttributeName = null;
		} else {
			MetaType responseHeaderParamType = getOutput(0).getStreamSchema().getAttribute(statusAttributeName)
					.getType().getMetaType();
			if (responseHeaderParamType != MetaType.INT32)
				throw new Exception(
						"Only types \"" + MetaType.INT32 + "\" allowed for param " + statusAttributeName + "\"");
		}
		// statusMessage, in port
		if (getInput(0).getStreamSchema().getAttribute(statusMessageAttributeName) == null) {
			statusMessageAttributeName = null;
		} else {
			MetaType responseHeaderParamType = getOutput(0).getStreamSchema().getAttribute(statusMessageAttributeName)
					.getType().getMetaType();
			if (responseHeaderParamType != MetaType.USTRING && responseHeaderParamType != MetaType.RSTRING)
				throw new Exception("Only types \"" + MetaType.USTRING + "\" and \"" + MetaType.RSTRING
						+ "\" allowed for param " + statusMessageAttributeName + "\"");
		}
		// response, in port
		if (getInput(0).getStreamSchema().getAttribute(responseContentTypeAttributeName) == null) {
			responseContentTypeAttributeName = null;
		} else {
			MetaType methodParamType = getOutput(0).getStreamSchema().getAttribute(responseContentTypeAttributeName).getType()
					.getMetaType();
			if (methodParamType != MetaType.USTRING && methodParamType != MetaType.RSTRING)
				throw new Exception("Only types \"" + MetaType.USTRING + "\" and \"" + MetaType.RSTRING
						+ "\" allowed for param " + responseContentTypeAttributeName + "\"");
		}	
		
		Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " initializing in PE: "
				+ context.getPE().getPEId() + " in Job: " + context.getPE().getJobId());
		}

	}
	
	   protected Object getConduit() {
	        return tupleCreator;
	    }

	/**
	 * Setup the metrics
	 */

    @CustomMetric(description="Number of requests received from web.", kind=Kind.COUNTER)
    public void setnMessagesReceived(Metric nMessagesReceived) {
        this.nMessagesReceived = nMessagesReceived;
    }
    public Metric getnMessagesReceived() {
        return nMessagesReceived;
    }
    @CustomMetric(description="Number of vaild responses sent back via web.", kind=Kind.COUNTER)
    public void setnMessagesResponded(Metric nMessagesResponded) {
        this.nMessagesResponded = nMessagesResponded;
    }
    public static Metric getnMessagesResponded() {
        return nMessagesResponded;
    }    
    @CustomMetric(description="Missing tracking key count..", kind=Kind.COUNTER)
    public void setnMissingTrackingKey(Metric nMissingTrackingKey) {
        this.nMissingTrackingKey = nMissingTrackingKey;
    }
    public static Metric getnRequestTimeouts() {
        return nRequestTimeouts;
    }       @CustomMetric(description="Number of timeouts waiting for response from Streams.", kind=Kind.COUNTER)
    public void setnRequestTimeouts(Metric nRequestTimeouts) {
        this.nRequestTimeouts = nRequestTimeouts;
    }
    public static Metric getnMissingTrackingKey() {
        return nMissingTrackingKey;
    }   
    // 
    
    @CustomMetric(description="Number of requests currently being processed.", kind=Kind.GAUGE)
    public void setnActiveRequests(Metric metric) {this.nActiveRequests = metric;}
    public Metric getnActiveRequests() { return nActiveRequests; }    
    

	@Parameter(optional = true, description = WEBTIMEOUT_DESC)
	public void setWebTimeout(double webTimeout) {
		this.webTimeout = webTimeout;
	}

	// OUTPUT - flow out of operator that that flows in from the web site
	@Parameter(optional = true, description = KEY_DESC)
	public void setKeyAttributeName(String keyAttributeName) {
		this.keyAttributeName = keyAttributeName;
	}

	@Parameter(optional = true, description = METHODATTRNAME_DESC)
	public void setMethodAttributeName(String methodAttributeName) {
		this.methodAttributeName = methodAttributeName;
	}

	@Parameter(optional = true, description = PATHINFOATTRNAME_DESC)
	public void setPathInfoAttributeName(String pathInfoAttributeName) {
		this.pathInfoAttributeName = pathInfoAttributeName;
	}

	@Parameter(optional = true, description = REQUESTATTRNAME_DESC)
	public void setRequestAttributeName(String requestAttributeName) {
		this.requestAttributeName = requestAttributeName;
	}

	@Parameter(optional = true, description = HEADERATTRNAME_DESC)
	public void setHeaderAttributeName(String headerAttributeName) {
		this.headerAttributeName = headerAttributeName;
	}
	@Parameter(optional = true, description = CONTENTTYPEATTRNAME_DESC)
	public void setContentTypeAttributeName(String contentTypeAttributeName) {
		this.contentTypeAttributeName = contentTypeAttributeName;
	}

	// INPUT - flow into operator that is returned to the web requestor.
	@Parameter(optional = true, description = RESPONSEATTRNAME_DESC)
	public void setResponseAttributeName(String responseAttributeName) {
		this.responseAttributeName = responseAttributeName;
	}
	
	@Parameter(optional = true, description = RESPONSEJSONSTRINGATTRNAME_DESC)
	public void setResponseJsonAttributeName(String jsonAttributeName) {
		this.responseJsonStringAttributeName = jsonAttributeName;
	}	

	@Parameter(optional = true, description = STATUSATTRNAME_DESC)
	public void setStatusAttributeName(String statusAttributeName) {
		this.statusAttributeName = statusAttributeName;
	}

	@Parameter(optional = true, description = STATUSMESSAGEATTRNAME_DESC)
	public void setStatusMessageAttributeName(String statusMessageAttributeName) {
		this.statusMessageAttributeName = statusMessageAttributeName;
	}

	@Parameter(optional = true, description = RESPONSEHEADERATTRNAME_DESC)
	public void setResponseHeaderAttributeName(String responseHeaderAttributeName) {
		this.responseHeaderAttributeName = responseHeaderAttributeName;
	}

	@Parameter(optional = true, description = RESPONSECONTENTTYPE_DESC)
	public void setResponseContentTypeAttributeName(String responseContentType) {
		this.responseContentTypeAttributeName = responseContentType;
	}

	ReqWebMessage retrieveExchangeWebMessage(long trackingKey) {
		ReqWebMessage activeWebMessage;
		
		trace.info(": trackingKey:" + trackingKey);						
		if (!activeMessages.containsKey(trackingKey)) {
			getnMissingTrackingKey().incrementValue(1L);			      				
			trace.error("retrieveExchangeWebMessage: failed to locate trackingKey. Has the key been corrupted or failed to propogate through the Stream? trackingKey:" + trackingKey + " missingTackingKeyCountt:" + getnMissingTrackingKey().getValue());							
			return null;
		}
		getnActiveRequests().setValue((long)activeMessages.size());
		activeWebMessage = activeMessages.get(trackingKey);
		activeMessages.remove(trackingKey);
		return activeWebMessage;
	}


	
	/**
	 * The arriving tuple is used to build a web response.  The response is related to the output tuples that was generated
	 * by initateRequestFromTheWeb(). Based upon the tuples attributes received the response is built. 
	 * If the attribute is not present on the tuple a default is provided, except for the mandatory key. 
	 */
	@Override
	public final void process(StreamingInput<Tuple> inputStream, Tuple tuple) throws Exception {
		String jsonString = null;
		String response = null;
		ReqWebMessage activeWebMessage = null;
		trace.info("processResponse ENTER");
		long trackingKey = 0;			
		if (activeColumns == null) {
			activeColumns = new HashSet<String>();
			for (Iterator<Attribute> iterator = tuple.getStreamSchema().iterator(); iterator.hasNext();) {
				Attribute attr = iterator.next();
				activeColumns.add(attr.getName());					
			}
			if ((activeColumns.size() != 1) && activeColumns.contains(responseJsonStringAttributeName)) {
				trace.info("processResponse : found that '"+ responseJsonStringAttributeName+"' is not the only input port attribute, NOT using attribute. " );							
			}
		}

		for (int idx = 0; getInput(0).getStreamSchema().getAttributeCount() != idx; idx++) {
			trace.info(String.format(" -- InPort@process attribute[%d]:%s ", idx, (getInput(0).getStreamSchema().getAttributeNames().toArray()[idx])));		
		}		
		
		if (jsonFormatInPort)  {
			trace.trace("processResponse - DUMP JSON:" + tuple.toString());
			trace.info("processResponse JSON");			
			jsonString = tuple.getString(responseJsonStringAttributeName);

			JSONObject json = null;
			try {
				json = JSONObject.parse(jsonString);
			} catch (IOException e) {
				// Handle a parse error - cannot send data via web since the key is in the jsonstring.
				getnMissingTrackingKey().incrementValue(1L);				
				trace.error("processResponse JSON - Failed  to parse json response string missingTrackingKeyCount[" + getnMissingTrackingKey().getValue() + "] jsonString:" + jsonString);
				e.printStackTrace();
				return;
			}
			if (!json.containsKey(defaultKeyAttributeName)) {
				trace.error("processResponse JSON: Did not locate the element  '" + defaultKeyAttributeName + "' in the JSON structure : " + jsonString); 
				return;
			}
			trackingKey =  (long) json.get(defaultKeyAttributeName);

			activeWebMessage = retrieveExchangeWebMessage(trackingKey);
			if (activeWebMessage == null) return;
			
			// Extract the components from json. 
			if (json.containsKey(defaultResponseAttributeName)) {
				response = (String) json.get(defaultResponseAttributeName);
				activeWebMessage.setResponse(response);							
			} else {
				trace.warn("processResponse JSON: Failed to extract response from JSON results, returning a 0 length string. ");
				activeWebMessage.setResponse("");											
			}			
			if (json.containsKey((String) defaultStatusAttributeName)) {
				Long val = (Long) json.get(defaultStatusAttributeName);
				activeWebMessage.setStatusCode(val.intValue());
			}
			if (json.containsKey((String) defaultStatusMessageAttributeName)) {
				activeWebMessage.setStatusMessage((String) json.get(defaultStatusMessageAttributeName));					
			}
			if (json.containsKey((String) defaultHeaderAttributeName)) {
				HashMap<String, String> mapWeb = new HashMap<String, String>();							
				Map<String,String> mapHeader =  (Map<String, String>) json.get(defaultHeaderAttributeName);	
				mapHeader.forEach((key, value)->{ trace.info("processResponse JSON : header key:" + value + " value: " + value);});
				mapHeader.forEach((key, value)->{ mapWeb.put(key,value);});
				activeWebMessage.setResponseHeaders(mapHeader);				
			}
			
			if (json.containsKey((String) defaultContentTypeAttributeName)) {
				activeWebMessage.setResponseContentType((String) json.get(defaultContentTypeAttributeName));									
			}			
		} else {
			trace.info("processResponse TUPLE");						
			trackingKey = tuple.getLong(keyAttributeName);

			activeWebMessage = retrieveExchangeWebMessage(trackingKey);
			if (activeWebMessage == null) return;
			
			// Extract components for response....
			response = (activeColumns.contains(responseAttributeName)) ? tuple.getString(responseAttributeName) : "";
			activeWebMessage.setResponse(response);			
			if (activeColumns.contains(statusAttributeName)) {
				activeWebMessage.setStatusCode((tuple.getInt(statusAttributeName)));				
			} 
			if (activeColumns.contains(statusMessageAttributeName)) {
				activeWebMessage.setStatusMessage(tuple.getString(statusMessageAttributeName));
			}
			if (activeColumns.contains(responseHeaderAttributeName)) {			
				Map<RString, RString> mapStr = (Map<RString, RString>) tuple.getMap(responseHeaderAttributeName);
				HashMap<String, String> mapWeb = new HashMap<String, String>();
				for (Iterator<RString> keys = mapStr.keySet().iterator(); keys.hasNext();) {
					RString key = (RString) keys.next();
					mapWeb.put(key.getString(), mapStr.get(key).getString());
				}
				activeWebMessage.setResponseHeaders(mapWeb);
			}
			if (activeColumns.contains(responseContentTypeAttributeName)) {
				activeWebMessage.setResponseContentType(tuple.getString(responseContentTypeAttributeName));
			}			
		}

		getnMessagesResponded().incrementValue(1L);
		trace.info("processResponse Received #" + getnMessagesResponded().getValue() + " response:" + response);
		activeWebMessage.issueResponseFromStreams();
		trace.info("processResponse EXIT response : trackingKey:" + trackingKey);		
	}

	/*
	 * A web request will arrive from the web and be injected into the Stream. 
	 * A request is injected into the stream response will enter through
	 * the process() method above. 
	 */
	private OutputTuple initiateRequestFromWeb(ReqWebMessage exchangeWebMessage) {
		getnMessagesReceived().incrementValue(1L);
		trace.info("initiateWebRequest ENTER # " + getnMessagesReceived().getValue() +" trackingKey: " + exchangeWebMessage.trackingKey);
		activeMessages.put(exchangeWebMessage.trackingKey, exchangeWebMessage);
		StreamingOutput<OutputTuple> outStream = getOutput(0);
		OutputTuple outTuple = outStream.newTuple();
		trace.info("initiateWebRequest Sending key - attr name:" + keyAttributeName + " trackingKey:"
				+ exchangeWebMessage.trackingKey);
		trace.info("initiateWebRequest - attr name:" + requestAttributeName + " attr value:"
				+ exchangeWebMessage.getRequestPayload());
		
		if (jsonFormatOutPort) {
			String jsonRequestString = exchangeWebMessage.jsonRequest();
			outTuple.setString(jsonStringAttributeName, jsonRequestString);
			trace.info("initiateWebRequest - single attribute, contents : " + jsonRequestString);			
		} else {
			outTuple.setLong(keyAttributeName, exchangeWebMessage.trackingKey);
			outTuple.setString(requestAttributeName, exchangeWebMessage.getRequestPayload());
			if (methodAttributeName != null) {
				outTuple.setString(methodAttributeName, exchangeWebMessage.getMethod());
			}
			if (pathInfoAttributeName != null) {
				outTuple.setString(pathInfoAttributeName, exchangeWebMessage.getPathInfo());
			}
			if (contentTypeAttributeName != null) {
				outTuple.setString(contentTypeAttributeName, exchangeWebMessage.getContentType());
			}
			if (headerAttributeName != null) {
				HashMap<RString, RString> transfer = new HashMap<RString, RString>();
				Map<String, String> mapp = exchangeWebMessage.getHeaders();
				for (Iterator<String> keys = mapp.keySet().iterator(); keys.hasNext();) {
					String key = (String) keys.next();
					transfer.put(new RString(key), new RString(mapp.get(key)));
				}
				trace.info("initiateWebRequest type:" + mapp.getClass().getName());
				outTuple.setMap(headerAttributeName, transfer);
			}
			
			trace.info("initiateWebRequest EXIT ");		
		}
		return outTuple;
	}
}

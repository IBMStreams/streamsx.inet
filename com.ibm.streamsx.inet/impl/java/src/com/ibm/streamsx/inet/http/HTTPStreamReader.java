//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingData;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.logging.LogLevel;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

@OutputPorts({@OutputPortSet(cardinality=1, optional=false, windowPunctuationOutputMode=WindowPunctuationOutputMode.Generating,
			  description="Data received from the server will be sent on this port."),
			  @OutputPortSet(cardinality=1, optional=true, windowPunctuationOutputMode=WindowPunctuationOutputMode.Free, 
			  description="Error information will be sent out on this port including the response code and any message recieved from the server. " +
			  		"Tuple structure must conform to the [HTTPResponse] type specified in this namespace.")})
@PrimitiveOperator(name="HTTPGetStream", description=HTTPStreamReader.DESC)
@Libraries(value={"opt/downloaded/*"})
public class HTTPStreamReader extends AbstractOperator {
	private String dataAttributeName = "data";
	private HTTPStreamReaderObj reader = null;
	private int maxRetries = 3;
	private double retryDelay = 30;
	private boolean hasErrorOut = false;
	private Thread th = null;
	private boolean shutdown = false, useBackoff = false;
	private String url = null;
	private List<String> postData = new ArrayList<String>();
	private String authenticationType = "none", authenticationFile = null;
	private RetryController rc = null;
	private List<String> authenticationProperties = new ArrayList<String>();

	static final String CLASS_NAME="com.ibm.streamsx.inet.http.HTTPStreamsReader";

	private static Logger trace = Logger.getLogger(CLASS_NAME);
	private boolean retryOnClose = false;
	private boolean disableCompression = false;

	@Parameter(optional= false, description="URL endpoint to connect to.")
	public void setUrl(String url) {
		this.url = url;
	}
	@Parameter(optional=true, 
			description="Valid options are \\\"oauth\\\", \\\"basic\\\" and \\\"none\\\". Default is \\\"none\\\"." +
					" If the \\\"oauth\\\" option is selected, the requests will be singed using OAuth 1.0a.")
	public void setAuthenticationType(String val) {
		this.authenticationType = val;
	}
	@Parameter(optional=true, description=
			"Path to the properties file containing authentication information." +
			" Path can be absolute or relative to the data directory of the application."+
			" See the config directory for sample properties files.")
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
	@Parameter(optional=true, 
			description="The value for this parameter will be sent to the server as a POST request body." +
					" The value is expected to be in \\\"key=value\\\" format. ")
	public void setPostData(List<String> val) {
		this.postData.addAll(val);
	}
	@Parameter(optional=true, description="Use a backoff function for increasing the wait time between retries. " +
			"Wait times increase by a factor of 10. Default is false")
	public void setBackoff(boolean val) {
		this.useBackoff = val;
	}
	@Parameter(optional=true, description="Name of the attribute to populate the response data with. Default is \\\"data\\\"")
	public void setDataAttributeName(String val) {
		this.dataAttributeName = val;
	}
	@Parameter(optional=true, description="Retry connecting if the connection has been closed. Default is false")
	public void setRetryOnClose(boolean val) {
		this.retryOnClose = val;
	}
	@Parameter(optional=true, 
			description="By default the client will ask the server to compress its reponse data using supported compressions (gzip, deflate). " +
			"Setting this option to true will disable compressions. Default is false.")
	public void setDisableCompression(boolean val) {
		this.disableCompression = val;
	}

	@ContextCheck(compile=true)
	public static boolean checkAuthParams(OperatorContextChecker occ) {
		return occ.checkDependentParameters("authenticationFile", "authenticationType")
				&& occ.checkDependentParameters("authenticationProperty", "authenticationType")
				;
	}
	
	@Override
	public void initialize(OperatorContext op) throws Exception {
		super.initialize(op);

		if(op.getNumberOfStreamingOutputs() == 2) {
			hasErrorOut = true;
			trace.log(TraceLevel.INFO, "Error handler port is enabled");
		}

		if(getOutput(0).getStreamSchema().getAttribute(dataAttributeName) == null) {
			if(getOutput(0).getStreamSchema().getAttributeCount() > 1) {
				throw new Exception("Could not automatically detect the data field for output port 0. " +
						"Specify a valid value for \"dataAttributeName\"");
			}
			dataAttributeName = getOutput(0).getStreamSchema().getAttribute(0).getName();
		}

		MetaType dataParamType = getOutput(0).getStreamSchema().getAttribute(dataAttributeName).getType().getMetaType();
		if(dataParamType!=MetaType.USTRING && dataParamType!=MetaType.RSTRING)
			throw new Exception("Only types \"" + MetaType.USTRING + "\" and \"" 
					+ MetaType.RSTRING + "\" allowed for param " + dataAttributeName + "\"");


		Map<String, String> postDataParams = null; 
		if(postData != null && postData.size() > 0 ) {
			postDataParams = new HashMap<String, String>();
			for(String value : postData) {
				int loc = value.indexOf("=");
				if(loc == -1 || loc >= value.length()-1)
					throw new Exception("Value of \"postData\" parameter not as expected: " + value);
				postDataParams.put(value.substring(0, loc), value.substring(loc+1, value.length()));
			}
		}
		
		if(useBackoff) 
			rc=new BackoffRetryController(maxRetries, retryDelay);
		else
			rc=new RetryController(maxRetries, retryDelay);
		
		trace.log(TraceLevel.INFO, "Using authentication type: " + authenticationType);
		IAuthenticate auth = AuthHelper.getAuthenticator(authenticationType, authenticationFile, authenticationProperties);

		reader = new HTTPStreamReaderObj(this.url, auth, this, postDataParams, disableCompression);
		th = op.getThreadFactory().newThread(reader);
		th.setDaemon(false);
	}


	@Override
	public void allPortsReady() throws Exception {
		trace.log(TraceLevel.INFO, "URL: " + reader.getUrl());
		th.start();
	}

	@Override
	public void shutdown() throws Exception {
		shutdown = true;
		if(reader!=null)
			reader.shutdown();
	}

	void connectionSuccess() throws Exception {
		trace.log(LogLevel.INFO, "Connection successful");
		rc.connectionSuccess();
	}

	boolean onReadException(Exception e) throws Exception {
		rc.readException();
		trace.log(TraceLevel.ERROR, "Processing Read Exception", e);

		if(hasErrorOut) {
			OutputTuple otup = getOutput(1).newTuple();

			int retCode = -1;
			if(e instanceof HTTPException) {
				retCode = ((HTTPException) e).getResponseCode();
				String data = ((HTTPException) e).getData();
				if(data != null) {
					otup.setString("data", data);
					otup.setInt("dataSize", data.length());
				}
				else {
					otup.setInt("dataSize", 0);
				}
			}

			otup.setInt("responseCode", retCode);
			otup.setString("errorMessage", e.getMessage());

			getOutput(1).submit(otup);
		}
		boolean retry = !shutdown && rc.doRetry() ;
		if(retry) {
			trace.log(TraceLevel.ERROR, "Will Retry", e);
			sleepABit(rc.getSleep());
		}
		return retry;
	}

	void sleepABit(double seconds) throws InterruptedException {
		trace.log(TraceLevel.INFO, "Sleeping for: " + seconds);
		long end  = System.currentTimeMillis() + (long)(seconds * 1000);
		while(!shutdown && System.currentTimeMillis() < end) {
			Thread.sleep(1 * 100);
		}
	}


	void processNewLine(String line) throws Exception {
		if(line == null) return;
		
		if(trace.isLoggable(TraceLevel.TRACE))
			trace.log(TraceLevel.TRACE, "New Data: " + line);
		rc.readSuccess();
		if(trace.isLoggable(TraceLevel.DEBUG))
			trace.log(TraceLevel.DEBUG, line);
		StreamingOutput<OutputTuple> op = getOutput(0);
		OutputTuple otup = op.newTuple(); 
		otup.setString(dataAttributeName, line);
		op.submit(otup);
		if(trace.isLoggable(TraceLevel.TRACE))
			trace.log(TraceLevel.TRACE, "Done Submitting");
	}

	boolean connectionClosed() throws Exception {
		trace.log(TraceLevel.INFO, "Stream Connection Closed");
		rc.connectionClosed();
		StreamingOutput<OutputTuple> op = getOutput(0);
		op.punctuate(StreamingData.Punctuation.WINDOW_MARKER);//signal current one done
		boolean retry = retryOnClose && rc.doRetry();//retry connection
		if(retry) {
			sleepABit(rc.getSleep());
		}
		return retry;
	}
	
	
	public static final String DESC  = 
			"Connects to an HTTP endpoint, reads \\\"chunks\\\" of data and sends it to the output port." +
			" Every line read from the HTTP server endpoint is sent as a single tuple." +
			" If a connection is closed by the server, a WINDOW punctuation will be sent on port 0." +
			" Supported Authentications: Basic Authentication, OAuth 1.0a." +
			" Supported Compressions: Gzip, Deflate."
			;
}


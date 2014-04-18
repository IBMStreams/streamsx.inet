//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingData;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.logging.LogLevel;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

@InputPorts(@InputPortSet(cardinality=0))
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
	private int retries = 3;
	private double sleepDelay = 30;
	private boolean hasErrorOut = false;
	private Thread th = null;
	private boolean shutdown = false, useBackoff = false;
	private String url = null;
	private String postData = null;
	private String authType = "none", authFile = null;
	private RetryController rc = null;
	private List<String> authProps = new ArrayList<String>();

	static final String CLASS_NAME="com.ibm.streamsx.inet.http.HTTPStreamsReader";

	private static Logger trace = Logger.getLogger(CLASS_NAME);
	private boolean retryOnClose = false;
	private boolean disableCompression = false;


	@Parameter(optional= false, description="URL to connect to")
	public void setUrl(String url) {
		this.url = url;
	}
	@Parameter(optional=true, 
			description="Valid options are \\\"oauth\\\", \\\"basic\\\" and \\\"none\\\". Default is \\\"none\\\".")
	public void setAuthenticationType(String val) {
		this.authType = val;
	}
	@Parameter(optional=true, description=
			"Path to the properties file containing authentication information." +
			" Path can be absolute or relative to the data directory of the application."+
			" See the config directory for sample properties files.")
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
	@Parameter(optional=true, name="retryDelay", description="Wait time between retries in case of failures/disconnects.")
	public void setSleepDelay(double val) {
		this.sleepDelay = val;
	}
	@Parameter(optional=true, 
			description="The value for this parameter will be sent to the server as a POST request body.")
	public void setPostData(String val) {
		this.postData = val;
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
	public void setCompression(boolean val) {
		this.disableCompression = val;
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

		if(useBackoff) 
			rc=new BackoffRetryController(retries, sleepDelay);
		else
			rc=new RetryController(retries, sleepDelay);

		trace.log(TraceLevel.INFO, "Using authentication type: " + authType);
		IAuthenticate auth = AuthHelper.getAuthenticator(authType, authFile, authProps);

		reader = new HTTPStreamReaderObj(this.url, auth, this, postData, disableCompression);
		th = op.getThreadFactory().newThread(reader);
		th.setDaemon(false);
	}


	@Override
	public void allPortsReady() throws Exception {
		trace.log(TraceLevel.INFO, "URL: " + reader.getUrl());
		th.start();
	}

	public void connectionSuccess() throws Exception {
		trace.log(LogLevel.INFO, "Connection successful");
		rc.connectionSuccess();
	}

	public boolean onReadException(Exception e) throws Exception {
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

	public void shutdown() throws Exception {
		shutdown = true;
		if(reader!=null)
			reader.shutdown();
	}

	public void processNewLine(String line) throws Exception {
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

	public boolean connectionClosed() throws Exception {
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
			"Every line read from the HTTP server endpoint is sent as a single tuple." +
			"If a connection is closed by the server, a WINDOW punctuation will be sent on port 0." +
			"Certain authentication modes are supported." 
			;
}


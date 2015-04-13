/*
 * Copyright (C) 2014, International Business Machines Corporation
 * All Rights Reserved.
 */
package com.ibm.streamsx.inet.httpxml;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.samples.patterns.PollingSingleTupleProducer;
import com.ibm.streams.operator.types.ValueFactory;
import com.ibm.streams.operator.types.XML;

/**
 * HTTP GET of application/xml content.
 *
 */
@PrimitiveOperator(description=HTTPGetXMLContent.DESC, namespace="com.ibm.streamsx.inet.http")
// Can't create a control port because TupleConsumer has process as final.
// @InputPortSet(optional=true, cardinality=1, controlPort=true, description="Control port to change the URL used for the HTTP GET.")
@OutputPortSet(cardinality=1,windowPunctuationOutputMode=WindowPunctuationOutputMode.Free,
    description="Content of the HTTP GET request as an XML attribute. Each successful HTTP request that returns a " +
                 "single well-formed XML document results in a submitted tuple with an XML attribute containing the returned content.")
@Icons(location32="impl/java/icons/HTTPGetXMLContent_32.gif", location16="impl/java/icons/HTTPGetXMLContent_16.gif")
public class HTTPGetXMLContent extends PollingSingleTupleProducer {
	
	static final String DESC = "Periodically connects to an HTTP endpoint to GET XML content as a single tuple. " +
	                            "The XML content is assigned  to the first attribute in the output tuple which must be " +
			                    "of type `xml`." +
			                    "" +
			                    "The URL can have a single query parameter updated using the `updateParameter` parameter." +
			                    "When set the URL query string will be modified to set the named parameter to a new value." +
			                    "The default action is to set it to the number of milliseconds since the 1970 epoch.";
	
	private static final Logger trace = Logger.getLogger(HTTPGetXMLContent.class.getName());
	
	private String url;
	// Currently hard-coded to zero.
	private final int contentIndex = 0;
	
	private HttpClient client;
	private URIBuilder builder;
	private HttpGet get;
	
	private Metric nFailedRequests; 
	
	private String updateParameter;
	private String updateXPath;
	private UpdateParameter updater;

	public synchronized String getUrl() {
		return url;
	}
	
	@Parameter(description="URL to GET `application/xml` or `text/xml` content from.")
	public synchronized void setUrl(String url) throws MalformedURLException {
		this.url = url;
	}

	@Parameter(optional=true, description="URL query parameter to update based upon content in a successful request.")
	public void setUpdateParameter(String updateParameter) {
		this.updateParameter = updateParameter;
	}

	public String getUpdateXPath() {
		return updateXPath;
	}

	@Parameter(name="updateParameterFromContent", optional=true, description="Update the query parameter set in `updateParameter` from the value of this XPath expression against the returned content.")
	public void setUpdateXPath(String updateXPath) {
		this.updateXPath = updateXPath;
	}
	
	@ContextCheck
	public static void checkParameters(OperatorContextChecker checker) {		
		checker.checkDependentParameters("updateParameterFromContent", "updateParameter");		
	}

	public Metric getnFailedRequests() {
		return nFailedRequests;
	}

	@CustomMetric(kind=Kind.COUNTER, description="Number of HTTP requests that failed, did not return response 200.")
	public void setnFailedRequests(Metric nFailedRequests) {
		this.nFailedRequests = nFailedRequests;
	}

	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
		super.initialize(context);
		client = new DefaultHttpClient();
		builder = new URIBuilder(getUrl());
		get = new HttpGet(builder.build());
		get.addHeader("Accept", "application/xml, text/xml");
		get.addHeader("Accept-Encoding", "gzip, deflate");
		trace.info("Initial URL: " + get.getURI().toString());	
		
		if (updateParameter != null) {			
			if (getUpdateXPath() != null) {
				// Update using XPath
				updater = new UpdateFromXPath(this, getUpdateXPath());		
		    } else {
				// default updater using the current time.
				updater = new UpdateCurrentTimeMills();
			}			   
		}
	}

	/**
	 * Execute an HTTP GET request for each tuple that will be submitted.
	 * The contents of the tuple will be the XML contents of the returned Content.
	 * If the request was not OK then no tuple will be submitted.
	 */
	@Override
	protected boolean fetchSingleTuple(OutputTuple tuple) throws Exception {
		
		HttpResponse response = client.execute(get);
						
		try {
			final int responseCode = response.getStatusLine().getStatusCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				getnFailedRequests().increment();
				return false;
			}
			final HttpEntity entity = response.getEntity();
			final InputStream content = getInputStream(entity);
			try {
			    final XML xml = ValueFactory.newXML(content);
			    tuple.setXML(contentIndex, xml);
			    if (updater != null) {
			    	if (!xml.isDefaultValue())
			    	    updater.update(xml);
			    }
			} finally {
				content.close();			
			}
		} finally {
			get.reset();
		}
		
		return true;
	}
	
	//for handling compressions
	 static InputStream getInputStream(HttpEntity entity) throws IOException  {
		 
		 final InputStream content = entity.getContent();
                 final Header contentEncodingHdr = entity.getContentEncoding();
                 if (contentEncodingHdr == null)
                      return content;
		 final String encoding = contentEncodingHdr.getValue();
		 if (encoding == null)
			 return content;
		 if ("gzip".equalsIgnoreCase(encoding))
			 return new GZIPInputStream(content);
		 if ("deflate".equalsIgnoreCase(encoding))
	 		return new InflaterInputStream(content, new Inflater(true));
		 throw new IOException("Unknown encoding: " + encoding);
	  	}
	 
	 /**
	  * How the URL query parameter will be updated.
	  */
	 abstract class UpdateParameter {
		
		void update(XML xml) throws Exception {
			String value = getValue(xml);
			if (value != null) {
				builder.setParameter(updateParameter, value);
				get.setURI(builder.build());
   			    trace.info("Updated URL: " + get.getURI().toString());
			}
		}
		
		abstract String getValue(XML xml) throws Exception;
	 }
	 
	 /**
	  * Update the query attribute to the current time in milliseconds.
	  */
	 class UpdateCurrentTimeMills extends UpdateParameter {

		@Override
		public String getValue(XML xml) throws Exception {
		   return Long.toString(System.currentTimeMillis());
		}		 
	 }
}

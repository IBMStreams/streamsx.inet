package com.ibm.streamsx.inet.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.samples.patterns.PollingSingleTupleProducer;
import com.ibm.streams.operator.types.ValueFactory;
import com.ibm.streams.operator.types.XML;

/**
 * HTTP GET of application/xml content.
 *
 */
@PrimitiveOperator(description=HTTPGetXMLContent.DESC)
@OutputPortSet(cardinality=1,windowPunctuationOutputMode=WindowPunctuationOutputMode.Free,
    description="Content of the HTTP GET request as an XML attribute. Each successful HTTP request that returns a " +
                 "single well-formed XML document results in a submitted tuple with an XML attribute containing the returned content.")
public class HTTPGetXMLContent extends PollingSingleTupleProducer {
	
	static final String DESC = "Periodically connects to an HTTP endpoint to GET XML content as a single tuple. " +
	                            "The XML content is assigned  to the first attribute in the output tuple which must be " +
			                    "of type `xml`.";
	
	private String url;
	private URL urlForXML;
	// Currently hard-coded to zero.
	private final int contentIndex = 0;
	
	private HttpClient client;
	private HttpGet get;

	public synchronized String getUrl() {
		return url;
	}
	
	@Parameter(description="URL to GET `application/xml` or `text/xml` content from.")
	public synchronized void setUrl(String url) throws MalformedURLException {
		this.url = url;
	}
	
	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
		super.initialize(context);
		createUrl();
		client = new DefaultHttpClient();
		get = new HttpGet(getUrl());
		get.addHeader("Accept", "application/xml, text/xml");
		get.addHeader("Accept-Encoding", "gzip, deflate");
	}
	
	private synchronized void createUrl() throws MalformedURLException {
		urlForXML = new URL(getUrl());
	}

	@Override
	protected boolean fetchSingleTuple(OutputTuple tuple) throws Exception {
		
		HttpResponse response = client.execute(get);
						
		try {
			final int responseCode = response.getStatusLine().getStatusCode();
			if (responseCode != HttpURLConnection.HTTP_OK)
				return false;
			final HttpEntity entity = response.getEntity();
			final InputStream content = getInputStream(entity);
			try {
			    XML xml = ValueFactory.newXML(content);
			    tuple.setXML(contentIndex, xml);
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
		 final String encoding = entity.getContentEncoding().getValue();
		 if (encoding == null)
			 return content;
		 if ("gzip".equalsIgnoreCase(encoding))
			 return new GZIPInputStream(content);
		 if ("deflate".equalsIgnoreCase(encoding))
	 		return new InflaterInputStream(content, new Inflater(true));
		 throw new IOException("Unknown encoding: " + encoding);
	  	}

}

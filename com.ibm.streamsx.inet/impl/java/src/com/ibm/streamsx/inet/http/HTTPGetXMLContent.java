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
	}
	
	private synchronized void createUrl() throws MalformedURLException {
		urlForXML = new URL(getUrl());
	}

	@Override
	protected boolean fetchSingleTuple(OutputTuple tuple) throws Exception {
		
		final HttpURLConnection conn = (HttpURLConnection) urlForXML.openConnection();
		conn.addRequestProperty("Accept", "application/xml, text/xml");
		conn.addRequestProperty("Accept-Encoding", "gzip, deflate");
						
		try {
			final int responseCode = conn.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK)
				return false;
			final InputStream content = getInputStream(conn);
			try {
			    XML xml = ValueFactory.newXML(content);
			    tuple.setXML(contentIndex, xml);
			} finally {
				content.close();			
			}
		} finally {
			conn.disconnect();
		}
		
		return true;
	}
	
	//for handling compressions
	 static InputStream getInputStream(URLConnection conn) throws IOException  {
		 
		 final InputStream content = conn.getInputStream();
		 final String encoding = conn.getContentEncoding();
		 if (encoding == null)
			 return content;
		 if ("gzip".equalsIgnoreCase(encoding))
			 return new GZIPInputStream(conn.getInputStream());
		 if ("deflate".equalsIgnoreCase(encoding))
	 		return new InflaterInputStream(conn.getInputStream(), new Inflater(true));
		 throw new IOException("Unknown encoding: " + encoding);
	  	}

}

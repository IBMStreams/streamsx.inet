package com.ibm.streamsx.inet.http;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.samples.patterns.PollingSingleTupleProducer;
import com.ibm.streams.operator.types.ValueFactory;
import com.ibm.streams.operator.types.XML;

@PrimitiveOperator
@OutputPortSet(cardinality=1,windowPunctuationOutputMode=WindowPunctuationOutputMode.Free)
public class HTTPGetXMLContent extends PollingSingleTupleProducer {
	
	private String url;
	private URL urlForXML;
	private int contentIndex;

	public String getUrl() {
		return url;
	}
	
	@Parameter
	public void setUrl(String url) throws MalformedURLException {
		this.url = url;
	}
	
	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
		// TODO Auto-generated method stub
		super.initialize(context);
		urlForXML = new URL(getUrl());
	}

	@Override
	protected boolean fetchSingleTuple(OutputTuple tuple) throws Exception {
		
		final HttpURLConnection conn = (HttpURLConnection) urlForXML.openConnection();
				
		try {
			final int responseCode = conn.getResponseCode();
			System.err.println("getResponseCode=" + responseCode);
			if (responseCode != HttpURLConnection.HTTP_OK)
				return false;
			System.err.println("getContentType=" + conn.getContentType());
			final InputStream content = conn.getInputStream();
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

}

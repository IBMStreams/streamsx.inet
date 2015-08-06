/*
 * Copyright (C) 2014, International Business Machines Corporation
 * All Rights Reserved.
 */
package com.ibm.streamsx.inet.http;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.DeflateDecompressingEntity;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.samples.patterns.PollingSingleTupleProducer;

public abstract class AbstractHTTPGetContent<A> extends
        PollingSingleTupleProducer {

    protected static final Logger trace = Logger
            .getLogger("com.ibm.streamsx.inet.http");

    private String url;
    private List<String> extraHeaders;

    private HttpClient client;
    protected URIBuilder builder;
    protected HttpGet get;

    protected TupleAttribute<Tuple, A> contentAttribute;
    protected int contentAttributeIndex;

    private Metric nFailedRequests;

    public String getUrl() {
        return url;
    }

    @Parameter(description = "URL to HTTP GET content from.")
    public void setUrl(String url) {
        this.url = url;
    }
    
    @Parameter(optional = true, description = "Extra headers to send with request, format is \\\"Header-Name: value\\\"")
    public void setExtraHeaders(List<String> extraHeaders) {
    	this.extraHeaders = extraHeaders;
    }

    public Metric getnFailedRequests() {
        return nFailedRequests;
    }

    @CustomMetric(kind = Kind.COUNTER, description = "Number of HTTP requests that failed, did not return response 200.")
    public void setnFailedRequests(Metric nFailedRequests) {
        this.nFailedRequests = nFailedRequests;
    }

    protected static final String CA_DESC = "Output attribute to assign content to.";

    public TupleAttribute<Tuple, A> getContentAttribute() {
        return contentAttribute;
    }

    @Override
    public synchronized void initialize(OperatorContext context)
            throws Exception {
        super.initialize(context);
        client = new DefaultHttpClient();
        builder = new URIBuilder(getUrl());
        get = new HttpGet(builder.build());
        get.addHeader("Accept", acceptedContentTypes());
        get.addHeader("Accept-Encoding", "gzip, deflate");
        trace.info("Initial URL: " + get.getURI().toString());

        if (getContentAttribute() != null)
            contentAttributeIndex = getContentAttribute().getAttribute()
                    .getIndex();
        else
            contentAttributeIndex = defaultContentAttributeIndex();
        
        Map<String, String> extraHeaderMap = HTTPUtils.getHeaderMap(extraHeaders);
        for(Map.Entry<String, String> header : extraHeaderMap.entrySet()) {
            get.setHeader(header.getKey(), header.getValue());
        }
    }

    protected abstract String acceptedContentTypes();

    // defaults to the first attribute
    protected int defaultContentAttributeIndex() {
        return 0;
    }

    /**
     * Execute an HTTP GET request for each tuple that will be submitted. The
     * contents of the tuple will be the XML contents of the returned Content.
     * If the request was not OK then no tuple will be submitted.
     */
    @Override
    protected boolean fetchSingleTuple(OutputTuple tuple) throws Exception {

        HttpResponse response = client.execute(get);

        try {
            final int responseCode = response.getStatusLine().getStatusCode();
            if (trace.isLoggable(TraceLevel.TRACE))
                trace.log(TraceLevel.TRACE, "HTTP GET:" + responseCode
                        + " URL:" + get.getURI());

            if (responseCode != HttpURLConnection.HTTP_OK) {
                getnFailedRequests().increment();
                return false;
            }
            HttpEntity content = response.getEntity();
            if (content != null) {
                content = getUncompressedEntity(content);
                submitContents(tuple, content);
            }
        } finally {
            get.reset();
        }

        return true;
    }


    protected abstract void submitContents(OutputTuple tuple, HttpEntity content)
            throws Exception;

    // for handling compressions
    static HttpEntity getUncompressedEntity(HttpEntity content)
            throws IOException {

        final org.apache.http.Header contentEncodingHdr = content
                .getContentEncoding();
        if (contentEncodingHdr == null)
            return content;
        final String encoding = contentEncodingHdr.getValue();
        if (encoding == null)
            return content;
        if ("gzip".equalsIgnoreCase(encoding))
            return new GzipDecompressingEntity(content);
        if ("deflate".equalsIgnoreCase(encoding))
            return new DeflateDecompressingEntity(content);
        throw new IOException("Unknown encoding: " + encoding);
    }
}

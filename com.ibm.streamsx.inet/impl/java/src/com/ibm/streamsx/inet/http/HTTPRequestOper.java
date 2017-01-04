//
// *******************************************************************************
// * Copyright (C)2016, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.encoding.EncodingFactory;
import com.ibm.streams.operator.encoding.JSONEncoding;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.PrimitiveOperator;

@InputPorts(@InputPortSet(cardinality = 1, description = "Each tuple results in an HTTP request."))
@PrimitiveOperator(name = "HTTPRequest", description = HTTPRequestOperAPI.DESC)
@Libraries("opt/HTTPClient4.2.3/lib/*")
@Icons(location32 = "icons/HTTPPost_32.gif", location16 = "icons/HTTPPost_16.gif")
public class HTTPRequestOper extends HTTPRequestOperClient {

    @Override
    public void process(StreamingInput<Tuple> stream, Tuple tuple) throws Exception {

        // Create the request from the input tuple.
        final String url = getUrl(tuple);
        final HTTPMethod method = getMethod(tuple);

        final HttpRequestBase request;
        switch (method) {
        case POST:
            request = createPost(url, tuple);
            break;
        case PUT:
            request = createPut(url, tuple);
            break;
        case GET:
            request = createGet(url, tuple);
            break;
        case CONNECT:
        case HEAD:
        case OPTIONS:
        case DELETE:
        case TRACE:
        default:
            throw new UnsupportedOperationException(method.name());
        }

        // Add extra headers
        Map<String, String> headerMap = HTTPUtils.getHeaderMap(getExtraHeaders());
        for (Map.Entry<String, String> header : headerMap.entrySet()) {
            request.setHeader(header.getKey(), header.getValue());
        }

        sendRequest(request);
    }

    private HttpRequestBase createPost(String url, Tuple tuple) throws IOException {
        HttpPost post = new HttpPost(url);
        createJsonEntity(post, tuple);
        return post;
    }

    private HttpRequestBase createPut(String url, Tuple tuple) throws IOException {
        HttpPut put = new HttpPut(url);
        createJsonEntity(put, tuple);
        return put;
    }

    private HttpRequestBase createGet(String url, Tuple tuple) {
        HttpGet get = new HttpGet(url);
        // TODO add query parameters
        return get;
    }

    /*
     * Entity methods
     */
    private void createJsonEntity(HttpEntityEnclosingRequest req, Tuple tuple) throws IOException {

        JSONEncoding<JSONObject, JSONArray> je = EncodingFactory.getJSONEncoding();
        JSONObject jo = je.encodeTuple(tuple);

        for (Iterator<?> it = jo.keySet().iterator(); it.hasNext();) {
            if (!isRequestAttribute(it.next())) {
                it.remove();
            }
        }
        req.setEntity(new StringEntity(jo.serialize(), ContentType.APPLICATION_JSON));
    }

    void sendRequest(HttpRequestBase request) throws ClientProtocolException, IOException {
        try {
            HttpResponse response = getClient().execute(request);

            // TODO error handling
            @SuppressWarnings("unused")
            StatusLine status = response.getStatusLine();

            HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity);
        } finally {
            request.reset();
        }
    }
}

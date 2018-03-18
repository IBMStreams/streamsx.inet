//
// *******************************************************************************
// * Copyright (C)2016, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
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
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.encoding.EncodingFactory;
import com.ibm.streams.operator.encoding.JSONEncoding;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streamsx.inet.messages.Messages;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;

@PrimitiveOperator(name = HTTPRequestOper.OPER_NAME, description = HTTPRequestOperAPI.DESC)
@Libraries("opt/httpcomponents-client-4.5.5/lib/*")
@Icons(location32 = "icons/HTTPPost_32.gif", location16 = "icons/HTTPPost_16.gif")
@InputPorts(@InputPortSet(cardinality = 1, description = "Each tuple results in an HTTP request."))
@OutputPorts(
	{
		@OutputPortSet(
			cardinality=1, optional=true, windowPunctuationOutputMode=WindowPunctuationOutputMode.Generating,
			description="Data received from the server will be sent on this port."
		)
	}
)
public class HTTPRequestOper extends HTTPRequestOperClient {

    public static final String OPER_NAME="HTTPRequest";
    @ContextCheck(compile=true)
    public static void checkMethodParams(OperatorContextChecker occ) {
        Set<String> parameterNames = occ.getOperatorContext().getParameterNames();
        if (! parameterNames.contains("method") && ! parameterNames.contains("fixedMethod")) {
            occ.setInvalidContext(OPER_NAME+" operator requires parameter method or fixedMethod", null);
        }
        if (! parameterNames.contains("url") && ! parameterNames.contains("fixedUrl")) {
            occ.setInvalidContext(OPER_NAME+" operator requires parameter url or fixedUrl", null);
        }
        occ.checkExcludedParameters("method", "fixedMethod");
        occ.checkExcludedParameters("url", "fixedurl");
        occ.checkExcludedParameters("dataAttributeName", "bodyAttributeName");
    }

    @Override
    public void process(StreamingInput<Tuple> stream, Tuple tuple) throws Exception {

        // Create the request from the input tuple.
        final String url = getUrl(tuple);
        final HTTPMethod method = getMethod(tuple);

        if (tracer.isLoggable(TraceLevel.DEBUG))
            tracer.log(TraceLevel.DEBUG, "Request method:"+method.toString()+" url:"+url);
        final HttpRequestBase request;
        switch (method) {
        case POST:
            request = createPost(url, tuple);
            break;
        case PUT:
            request = createPut(url, tuple);
            break;
        case GET:
        	System.out.println("Create get");
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

        sendRequest(tuple, request);
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

    void sendOtuple(Tuple inTuple, String statusLine, int statusCode, String contentEncoding, String contentType, List<String> headers, String body) throws Exception {
        StreamingOutput<OutputTuple> op = getOutput(0);
        OutputTuple otup = op.newTuple();
        otup.assign(inTuple);
        if ( getStatusAttributeName()          != null) otup.setString(getStatusAttributeName(), statusLine);
        if ( getStatusCodeAttributeName()      != null) otup.setInt(getStatusCodeAttributeName(), statusCode);
        if ( getContentEncodingAttributeName() != null) otup.setString(getContentEncodingAttributeName(), contentEncoding);
        if ( getContentTypeAttributeName()     != null) otup.setString(getContentTypeAttributeName(), contentType);
        if ( getHeaderAttributeName()          != null) otup.setList(getHeaderAttributeName(), headers);
        if ( getDataAttributeName()            != null) otup.setString(getDataAttributeName(), body);
        if ( getBodyAttributeName()            != null) otup.setString(getBodyAttributeName(), body);
        op.submit(otup);
    }
    
    void sendRequest(Tuple inTuple, HttpRequestBase request) throws ClientProtocolException, IOException, Exception {
        String statusLine = "";
        int statusCode = -1;
        List<String> headers = new ArrayList<>();
        String contentEncoding = "";
        String contentType = "";
        String body = "";
        try {
            if (tracer.isLoggable(TraceLevel.DEBUG)) tracer.log(TraceLevel.DEBUG, "Request:"+request.toString());

            HttpResponse response = getClient().execute(request);

            // TODO error handling
            StatusLine status = response.getStatusLine();
            statusLine = status.toString();
            statusCode = status.getStatusCode();
            
            if ((statusCode < 200) && (statusCode > 299)) {
                if (tracer.isLoggable(TraceLevel.WARN)) tracer.log(TraceLevel.WARN, "status="+status.toString());
                sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, body);
            } else {
                if (tracer.isLoggable(TraceLevel.DEBUG)) tracer.log(TraceLevel.DEBUG, "status="+status.toString());
                HttpEntity entity = response.getEntity();
                boolean tupleSent = false;
                if (entity != null) {
                    if (tracer.isLoggable(TraceLevel.TRACE))
                        tracer.log(TraceLevel.TRACE, "entitiy isChunked="+entity.isChunked()+" isRepeatable="+entity.isRepeatable()+" isStreaming="+entity.isStreaming());
                    Header contEnc = entity.getContentEncoding();
                    if (contEnc != null) contentEncoding = contEnc.toString();
                    Header contType = entity.getContentType();
                    if (contentType != null) contentType = contType.toString();
                    InputStream instream = entity.getContent();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
                    String inputLine = null;
                    while (!shutdown && ((inputLine = reader.readLine()) != null)) {
                        if (hasDataPort) {
                            if (getDataAttributeName() != null) {
                                sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, inputLine);
                                tupleSent = true;
                            }
                            if (getBodyAttributeName() != null) body = body + inputLine;
                        }
                    }
                    EntityUtils.consume(entity);
                }
                if (hasDataPort) {
                    if (getDataAttributeName() != null) {
                        if ( ! tupleSent) {
                            sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, "");
                        }
                    } else {
                        sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, body);
                    }
                    
                }
            }
            /*StreamingOutput<OutputTuple> op = getOutput(0);
            OutputTuple otup = op.newTuple();
            otup.setString("status", status.toString());
            otup.setString("contentEncoding", contentEncoding);
            otup.setString("contentType", contentType);
            otup.setString("data", resp);
            String respHeader = "";
            HeaderIterator hi = response.headerIterator();
            while (hi.hasNext()) {
                Header myh = hi.nextHeader();
                respHeader = respHeader + "||" + myh;
            }
            //Header[] ha = response.getAllHeaders();
            otup.setString("responseHeader", respHeader);
            op.submit(otup);*/
        } catch (ClientProtocolException e) {
            tracer.log(TraceLevel.ERROR, "ClientProtocolException: "+e.getMessage());
            sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, body);
        } catch (IOException e) {
            tracer.log(TraceLevel.ERROR, "IOException: "+e.getMessage());
            sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, body);
        } finally {
            if (shutdown) {
                request.abort();
            } else {
                request.reset();
            }
        }
    }
}

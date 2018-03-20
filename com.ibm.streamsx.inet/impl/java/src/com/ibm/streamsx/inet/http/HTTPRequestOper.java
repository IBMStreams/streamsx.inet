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
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
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
import com.ibm.streams.operator.types.RString;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
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
            request = createGet(url, tuple);
            break;
        //case CONNECT:
        case HEAD:
            request = createHead(url, tuple);
            break;
        case OPTIONS:
            request = createOptions(url, tuple);
            break;
        case DELETE:
            request = createDelete(url, tuple);
            break;
        case TRACE:
            request = createTrace(url, tuple);
            break;
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
        createEntity(post, tuple);
        return post;
    }

    private HttpRequestBase createPut(String url, Tuple tuple) throws IOException {
        HttpPut put = new HttpPut(url);
        createEntity(put, tuple);
        return put;
    }

    private HttpRequestBase createGet(String url, Tuple tuple) {
        HttpGet get = new HttpGet(url);
        // TODO add query parameters
        return get;
    }

    private HttpRequestBase createHead(String url, Tuple tuple) {
        HttpHead head = new HttpHead(url);
        return head;
    }
    
    private HttpRequestBase createDelete(String url, Tuple tuple) {
        HttpDelete options = new HttpDelete(url);
        return options;
    }
    
    private HttpRequestBase createOptions(String url, Tuple tuple) {
        HttpOptions options = new HttpOptions(url);
        return options;
    }
    
    private HttpRequestBase createTrace(String url, Tuple tuple) {
        HttpTrace options = new HttpTrace(url);
        return options;
    }
    
    /*
     * Entity methods
     */
    private void createEntity(HttpEntityEnclosingRequest req, Tuple tuple) throws IOException {
        if (contentType.getMimeType() == ContentType.APPLICATION_JSON.getMimeType()) {
            JSONEncoding<JSONObject, JSONArray> je = EncodingFactory.getJSONEncoding();
            JSONObject jo = je.encodeTuple(tuple);

            for (Iterator<?> it = jo.keySet().iterator(); it.hasNext();) {
                if (!isRequestAttribute(it.next())) {
                    it.remove();
                }
            }
            req.setEntity(new StringEntity(jo.serialize(), ContentType.APPLICATION_JSON));
            
        } else if (contentType.getMimeType() == ContentType.APPLICATION_FORM_URLENCODED.getMimeType()) {
            StreamSchema ss = tuple.getStreamSchema();
            Iterator<Attribute> ia = ss.iterator();
            String payload = "";
            while (ia.hasNext()) {
                Attribute attr =ia.next();
                String name = attr.getName();
                if (requestAttributes.contains(name)) {
                    if (!payload.isEmpty())
                        payload = payload+"\n";
                    payload = payload + name + ":" + attr.toString();
                }
            }
            req.setEntity(new StringEntity(payload, ContentType.APPLICATION_FORM_URLENCODED));
        } else {
            throw new UnsupportedOperationException(contentType.getMimeType());
        }
    }

    void sendOtuple(Tuple inTuple, String statusLine, int statusCode, String contentEncoding, String contentType, List<RString> headers, String body) throws Exception {
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
        List<RString> headers = new ArrayList<>();
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
                    //content type and encoding
                    Header contEnc = entity.getContentEncoding();
                    if (contEnc != null) contentEncoding = contEnc.toString();
                    Header contType = entity.getContentType();
                    if (contentType != null) contentType = contType.toString();
                    //Response Headers
                    HeaderIterator hi = response.headerIterator();
                    while (hi.hasNext()) {
                        Header myh = hi.nextHeader();
                        String h = myh.toString();
                        RString rh = new RString(h);
                        headers.add(rh);
                    }
                    //Message Body
                    if (hasDataPort) {
                        if (getDataAttributeName() != null) {
                            InputStream instream = entity.getContent();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
                            String inputLine = null;
                            while (!shutdown && ((inputLine = reader.readLine()) != null)) {
                                sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, inputLine);
                                tupleSent = true;
                            }
                        }
                        if (getBodyAttributeName() != null) {
                            body = EntityUtils.toString(entity);
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

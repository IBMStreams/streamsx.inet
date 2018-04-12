//
// *******************************************************************************
// * Copyright (C)2018, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
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
import com.ibm.streamsx.inet.messages.Messages;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.DataException;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingOutput;

/*******************************************************************************************
 * 
 * HTTP Request Operator
 * 
 ********************************************************************************************/
@PrimitiveOperator(name = HTTPRequestOper.OPER_NAME, description = HTTPRequestOperAPI.DESC)
@Libraries("opt/httpcomponents-client-4.5.5/lib/*")
@Icons(location32 = "icons/HTTPPost_32.gif", location16 = "icons/HTTPPost_16.gif")
@InputPorts(@InputPortSet(cardinality = 1, description = "This stream contains the information sent in a http request. Each tuple with valid request data results in an HTTP request except if method `NONE`is specified."))
@OutputPorts(
    {
        @OutputPortSet(
            cardinality=1, optional=true, windowPunctuationOutputMode=WindowPunctuationOutputMode.Generating,
            description="Data received in the http response be sent on this port. Other attributes are assigned from input stream."
        )
    }
)
public class HTTPRequestOper extends HTTPRequestOperClient {

    /********************************************
     * compile time checks
     ********************************************/
    @ContextCheck(compile = true)
    public static void checkInConsistentRegion(OperatorContextChecker checker) {
        HTTPRequestOperAPI.checkInConsistentRegion(checker);
    }
    @ContextCheck(compile=true)
    public static void checkMethodParams(OperatorContextChecker occ) {
        HTTPRequestOperAPI.checkMethodParams(occ);
    }

    /********************************************************************************
     * process (StreamingInput<Tuple> stream, Tuple tuple)
     ********************************************************************************/
    @Override
    public void process(StreamingInput<Tuple> stream, Tuple tuple) throws Exception {

        try {
            // Create the request from the input tuple.
            final String url = getUrl(tuple);
            final HTTPMethod method = getMethod(tuple);
            final ContentType contentType = getContentType(tuple);
            if ((contentType == null) && ((method == com.ibm.streamsx.inet.http.HTTPMethod.POST) || (method == com.ibm.streamsx.inet.http.HTTPMethod.PUT))) {
                throw new DataException(Messages.getString("DATA_INVALID_CONTENT_TYPE", tuple.toString()));
            }

            if (tracer.isLoggable(TraceLevel.DEBUG))
                tracer.log(TraceLevel.DEBUG, "Request method:"+method.toString()+" url:"+url);
            switch (method) {
            case POST: {
                    HttpPost post = new HttpPost(url);
                    createEntity(post, tuple, contentType);
                    setHeader(post);
                    signRequest(post);
                    sendRequest(tuple, post);
                }
                break;
            case PUT: {
                    HttpPut put = new HttpPut(url);
                    createEntity(put, tuple, contentType);
                    setHeader(put);
                    signRequest(put);
                    sendRequest(tuple, put);
                }
                break;
            case GET: {
                    URI uri = createUriWithParams(url, tuple);
                    HttpGet get = new HttpGet(uri);
                    setHeader(get);
                    signRequest(get);
                    sendRequest(tuple, get);
                }
                break;
            //case CONNECT:
            case HEAD: {
                    URI uri = createUriWithParams(url, tuple);
                    HttpHead head = new HttpHead(uri);
                    setHeader(head);
                    signRequest(head);
                    sendRequest(tuple, head);
                }
                break;
            case OPTIONS: {
                    HttpOptions options = new HttpOptions(url);
                    setHeader(options);
                    signRequest(options);
                    sendRequest(tuple, options);
                }
                break;
            case DELETE: {
                    HttpDelete delete = new HttpDelete(url);
                    setHeader(delete);
                    signRequest(delete);
                    sendRequest(tuple, delete);
                }
                break;
            case TRACE: {
                    HttpTrace trace = new HttpTrace(url);
                    setHeader(trace);
                    signRequest(trace);
                    sendRequest(tuple, trace);
                }
                break;
            case NONE: {
                    sendOtuple(tuple, "", -1, "", "", new ArrayList<RString>(), "");
                }
                return;
            default:
                throw new UnsupportedOperationException(method.name()); //This exception must not happen - no catch
            }

        } catch (DataException e) {
            tracer.log(TraceLevel.ERROR, e.getMessage()+" Input tuple:"+tuple.toString());
        } catch (IllegalArgumentException e) {
            tracer.log(TraceLevel.ERROR, e.getMessage()+" Input tuple:"+tuple.toString());
        } catch (URISyntaxException e) {
            tracer.log(TraceLevel.ERROR, e.getMessage()+" Input tuple:"+tuple.toString());
        }
    }

    /***********************************************************************************
     * add query params to uri
     ***********************************************************************************/
    private URI createUriWithParams(String url, Tuple tuple) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(url);

        //take all request attributes if no request body is given
        StreamSchema ss = tuple.getStreamSchema();
        Iterator<Attribute> ia = ss.iterator();
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        while (ia.hasNext()) {
            Attribute attr =ia.next();
            String name = attr.getName();
            if (requestAttributes.contains(name)) {
                int index = attr.getIndex();
                //MetaType paramType = attr.getType().getMetaType();
                String value = tuple.getString(index);
                params.add(new BasicNameValuePair(name, value));
                uriBuilder = uriBuilder.addParameter(name, value);
            }
        }
        URI result = uriBuilder.build();
        if (tracer.isLoggable(TraceLevel.DEBUG))
            tracer.log(TraceLevel.DEBUG, "Request uri:"+result.toString());
    return result;
    }
    
    /************************************************
     * set extra headers
     ************************************************/
    private void setHeader(HttpRequestBase request) {
        Map<String, String> headerMap = HTTPUtils.getHeaderMapThrow(extraHeaders);
        for (Map.Entry<String, String> header : headerMap.entrySet()) {
            request.setHeader(header.getKey(), header.getValue());
        }
    }
    
    /************************************************
     * sign the request if oath is used
     * @throws OAuthCommunicationException 
     * @throws OAuthExpectationFailedException 
     * @throws OAuthMessageSignerException 
     ************************************************/
    private void signRequest(HttpRequestBase request) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException {
        switch (authenticationType) {
        case STANDARD:
            break;
        case OAUTH1:
            oAuthConsumer.sign(request);
            break;
        case OAUTH2:
            request.setHeader(oAuth2AuthHeaderKey, oAuth2AuthHeaderValue);
            break;
        }
    }
    
    /******************************************************************************************************************
     * Create and add entity to request
     *******************************************************************************************************************/
    private void createEntity(HttpEntityEnclosingRequest req, Tuple tuple, ContentType contentType) throws IOException {
        if (getRequestBody() != null) {
            //take request body from input tuple transparently
            String instr = getRequestBody().getValue(tuple);
            req.setEntity(new StringEntity(instr, contentType));
        } else {
            //content type specific 
            if (contentType.getMimeType() == ContentType.APPLICATION_JSON.getMimeType()) {
                //take all request attributes if no request body is given
                JSONEncoding<JSONObject, JSONArray> je = EncodingFactory.getJSONEncoding();
                JSONObject jo = je.encodeTuple(tuple);
                for (Iterator<?> it = jo.keySet().iterator(); it.hasNext();) {
                    if (!isRequestAttribute(it.next())) {
                        it.remove();
                    }
                }
                req.setEntity(new StringEntity(jo.serialize(), ContentType.APPLICATION_JSON));
            } else if (contentType.getMimeType() == ContentType.APPLICATION_FORM_URLENCODED.getMimeType()) {
                //take all request attributes if no request body is given
                StreamSchema ss = tuple.getStreamSchema();
                Iterator<Attribute> ia = ss.iterator();
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                while (ia.hasNext()) {
                    Attribute attr =ia.next();
                    String name = attr.getName();
                    if (requestAttributes.contains(name)) {
                        int index = attr.getIndex();
                        //MetaType paramType = attr.getType().getMetaType();
                        String value = tuple.getString(index);
                        params.add(new BasicNameValuePair(name, value));
                    }
                }
                req.setEntity(new UrlEncodedFormEntity(params));
            } else {
                //take all request attributes if no request body is given
                StreamSchema ss = tuple.getStreamSchema();
                Iterator<Attribute> ia = ss.iterator();
                String payload = "";
                while (ia.hasNext()) {
                    Attribute attr =ia.next();
                    String name = attr.getName();
                    if (requestAttributes.contains(name)) {
                        int index = attr.getIndex();
                        //MetaType paramType = attr.getType().getMetaType();
                        String value = tuple.getString(index);
                        payload = payload+value;
                    }
                }
                req.setEntity(new StringEntity(payload, contentType));
            }
        }
    }

    /******************************************************************************************************************
     * send output tuple
     ******************************************************************************************************************/
    void sendOtuple(Tuple inTuple, String statusLine, int statusCode, String contentEncoding, String contentType, List<RString> headers, String body) throws Exception {
        StreamingOutput<OutputTuple> op = getOutput(0);
        OutputTuple otup = op.newTuple();
        otup.assign(inTuple);
        if ( outputStatus          != null) otup.setString(outputStatus,          statusLine);
        if ( outputStatusCode      != null) otup.setInt   (outputStatusCode,      statusCode);
        if ( outputContentEncoding != null) otup.setString(outputContentEncoding, contentEncoding);
        if ( outputContentType     != null) otup.setString(outputContentType,     contentType);
        if ( outputHeader          != null) otup.setList  (outputHeader,          headers);
        if ( outputDataLine        != null) otup.setString(outputDataLine,        body);
        if ( outputBody            != null) otup.setString(outputBody,            body);
        op.submit(otup);
    }
    
    /*****************************************************************************************************************
     * send request
     ****************************************************************************************************************/
    void sendRequest(Tuple inTuple, HttpRequestBase request) throws ClientProtocolException, IOException, Exception {
        String statusLine = "";
        int statusCode = -1;
        List<RString> headers = new ArrayList<>();
        String contentEncoding = "";
        String contentType = "";
        String body = "";
        try {
            if (tracer.isLoggable(TraceLevel.DEBUG) || tracer.isLoggable(TraceLevel.TRACE)) {
                StringBuilder sb = new StringBuilder("request to send:\n");
                sb.append(request.getRequestLine());
                Header[] hd = request.getAllHeaders();
                int len = hd.length;
                for (int i=0; i<len; i++) {
                    String name = hd[i].getName();
                    String val = hd[i].getValue();
                    sb.append("\n").append(name).append(": ");
                    if (name.equals("Authorization")) {
                        sb.append("********");
                    } else {
                        sb.append(val);
                    }
                }
                if (tracer.isLoggable(TraceLevel.TRACE)) {
                    final HTTPMethod method = getMethod(inTuple);
                    switch (method) {
                    case POST: {
                            HttpEntityEnclosingRequest her = (HttpEntityEnclosingRequest) request;
                            HttpEntity he = her.getEntity();
                            if (he != null) {
                                String requestBody = EntityUtils.toString(he);
                                sb.append("\n").append(requestBody);
                            }
                    }
                        break;
                    case PUT: {
                            HttpEntityEnclosingRequest her = (HttpEntityEnclosingRequest) request;
                            HttpEntity he = her.getEntity();
                            if (he != null) {
                                String requestBody = EntityUtils.toString(he);
                                sb.append("\n").append(requestBody);
                            }
                        }
                        break;
                    default:
                        break;
                    }
                }
                RequestConfig rc = request.getConfig();
                if (rc == null) {
                    sb.append("\nRequestConfig=null");
                } else {
                    sb.append("\n").append(rc.toString());
                }

                if (tracer.isLoggable(TraceLevel.TRACE)) {
                    tracer.log(TraceLevel.TRACE, sb.toString());
                } else {
                    tracer.log(TraceLevel.DEBUG, sb.toString());
                }
            }
            
            HttpResponse response = httpClient.execute(request, httpContext);

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
                    if (contType != null) contentType = contType.toString();
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
                        if (outputDataLine != null) {
                            InputStream instream = entity.getContent();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
                            String inputLine = null;
                            while (!shutdown && ((inputLine = reader.readLine()) != null)) {
                                sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, inputLine);
                                tupleSent = true;
                            }
                        }
                        if (outputBody != null) {
                            body = EntityUtils.toString(entity);
                        }
                    }
                    EntityUtils.consume(entity);
                }
                if (hasDataPort) {
                    if (outputDataLine != null) {
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

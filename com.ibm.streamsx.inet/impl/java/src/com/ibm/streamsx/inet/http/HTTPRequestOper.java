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
import java.nio.charset.Charset;
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
import org.apache.http.client.methods.HttpPatch;
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
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.encoding.EncodingFactory;
import com.ibm.streams.operator.encoding.JSONEncoding;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.types.Blob;
import com.ibm.streams.operator.types.RString;
import com.ibm.streams.operator.types.ValueFactory;
import com.ibm.streamsx.inet.messages.Messages;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthException;
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
@InputPorts(@InputPortSet(cardinality = 1, description = "This stream contains the information sent in a http request. Each tuple with valid request data results in an HTTP request except if method `NONE` is specified."))
@OutputPorts(
    {
        @OutputPortSet(
            cardinality=1, optional=true, windowPunctuationOutputMode=WindowPunctuationOutputMode.Preserving,
            description="Data received in the http response be sent on this port. Other attributes are assigned from input stream."
        )
    }
)
public class HTTPRequestOper extends HTTPRequestOperClient {

    private Metric nRequestTransmit;
    private Metric nResponseSuccess;
    private Metric nResponseNoSuccess;

    /********************************************
     * Metrics
     ********************************************/
    @CustomMetric(kind = Kind.COUNTER, description ="The number of request transmit attempts.")
    public void setnRequestTransmit(Metric nRequestTransmit) {
        this.nRequestTransmit = nRequestTransmit;
    }
    @CustomMetric(kind = Kind.COUNTER, description ="The number of received responses with result code: success (2xx).")
    public void setnResponseSuccess(Metric nResponseSuccess) {
        this.nResponseSuccess = nResponseSuccess;
    }
    @CustomMetric(kind = Kind.COUNTER, description ="The number of received responses with result codes other than success.")
    public void setnResponseNoSuccess(Metric nResponseNoSuccess) {
        this.nResponseNoSuccess = nResponseNoSuccess;
    }
    
    /********************************************************************************
     * process (StreamingInput<Tuple> stream, Tuple tuple)
     * @throws Exception 
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
                    URI uri = createUriWithParams(url, tuple, false);
                    HttpPost post = new HttpPost(uri);
                    createEntity(post, tuple, contentType, true);
                    setHeader(post, tuple);
                    signRequest(post);
                    setConnectionParams(post);
                    sendRequest(tuple, post);
                }
                break;
            case PUT: {
                    URI uri = createUriWithParams(url, tuple, false);
                    HttpPut put = new HttpPut(uri);
                    createEntity(put, tuple, contentType, false);
                    setHeader(put, tuple);
                    signRequest(put);
                    setConnectionParams(put);
                    sendRequest(tuple, put);
                }
                break;
            case PATCH: {
                    URI uri = createUriWithParams(url, tuple, false);
                    HttpPatch patch = new HttpPatch(uri);
                    createEntity(patch, tuple, contentType, false);
                    setHeader(patch, tuple);
                    signRequest(patch);
                    setConnectionParams(patch);
                    sendRequest(tuple, patch);
                }
                break;
            case GET: {
                    URI uri = createUriWithParams(url, tuple, true);
                    HttpGet get = new HttpGet(uri);
                    setHeader(get, tuple);
                    signRequest(get);
                    setConnectionParams(get);
                    sendRequest(tuple, get);
                }
                break;
            //case CONNECT:
            case HEAD: {
                    URI uri = createUriWithParams(url, tuple, false);
                    HttpHead head = new HttpHead(uri);
                    setHeader(head, tuple);
                    signRequest(head);
                    setConnectionParams(head);
                    sendRequest(tuple, head);
                }
                break;
            case OPTIONS: {
                    URI uri = createUriWithParams(url, tuple, false);
                    HttpOptions options = new HttpOptions(uri);
                    setHeader(options, tuple);
                    signRequest(options);
                    setConnectionParams(options);
                    sendRequest(tuple, options);
                }
                break;
            case DELETE: {
                    URI uri = createUriWithParams(url, tuple, false);
                    HttpDelete delete = new HttpDelete(uri);
                    setHeader(delete, tuple);
                    signRequest(delete);
                    setConnectionParams(delete);
                    sendRequest(tuple, delete);
                }
                break;
            case TRACE: {
                    URI uri = createUriWithParams(url, tuple, false);
                    HttpTrace trace = new HttpTrace(uri);
                    setHeader(trace, tuple);
                    signRequest(trace);
                    setConnectionParams(trace);
                    sendRequest(tuple, trace);
                }
                break;
            case NONE: {
                    if (hasDataPort()) {
                        sendOtuple(tuple, "", -1, "", "", new ArrayList<RString>(), "", null, "", "");
                    }
                }
                return;
            default:
                throw new UnsupportedOperationException(method.name()); //This exception must not happen - no catch
            }

        } catch (DataException e) {
            String errmess = e.getClass().getName() + ": " + e.getMessage() + " Input tuple:" + tuple.toString();
            tracer.log(TraceLevel.ERROR, errmess);
            if (hasDataPort()) {
                sendOtuple(tuple, "", -1, "", "", new ArrayList<RString>(), "", null, errmess, "");
            }
        } catch (OAuthException e) {
        //} catch (OAuthMessageSignerException | OAuthExpectationFailedException | OAuthCommunicationException e) {
            String errmess = e.getClass().getName() + ": " + e.getMessage() + " Input tuple:" + tuple.toString();
            tracer.log(TraceLevel.ERROR, errmess);
            if (hasDataPort()) {
                sendOtuple(tuple, "", -1, "", "", new ArrayList<RString>(), "", null, errmess, "");
            }
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            String errmess = e.getClass().getName() + ": " + e.getMessage() + " Input tuple:" + tuple.toString();
            tracer.log(TraceLevel.ERROR, errmess);
            if (hasDataPort()) {
                sendOtuple(tuple, "", -1, "", "", new ArrayList<RString>(), "", null, errmess, "");
            }
        }
    }

    /***********************************************************************************
     * add query params to uri
     ***********************************************************************************/
    private URI createUriWithParams(String url, Tuple tuple, boolean isMethodGet) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(url);

        if ((getRequestUrlArgumentsAttribute() != null) && ( ! getRequestUrlArgumentsAttribute().getValue(tuple).isEmpty())) {
            //take arguments from getRequestUrlArguments attribute
            String args = getRequestUrlArgumentsAttribute().getValue(tuple);
            uriBuilder = uriBuilder.setCustomQuery(args);
        } else {
            //take all request attributes if no request body is given
            if (isMethodGet && getRequestAttributesAsUrlArguments()) {
                StreamSchema ss = tuple.getStreamSchema();
                Iterator<Attribute> ia = ss.iterator();
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                while (ia.hasNext()) {
                    Attribute attr =ia.next();
                    String name = attr.getName();
                    if (getRequestAttributes().contains(name)) {
                        int index = attr.getIndex();
                        Object obj = tuple.getObject(index); //consider optional type
                        if (obj != null) {
                            String value = tuple.getString(index);
                            params.add(new BasicNameValuePair(name, value));
                            uriBuilder = uriBuilder.addParameter(name, value);
                        }
                    }
                }
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
    private void setHeader(HttpRequestBase request, Tuple tuple) {
        ArrayList<String> headerList = getExtraHeaders();
        TupleAttribute<Tuple, String> headerAttribute = getExtraHeaderAttribute();
        //add the header from extraHeaderAttribute to the extra headers list if the value is not empty
        if (headerAttribute != null) {
            if (headerList == null) {
                headerList = new ArrayList<String>(1);
            }
            String additionalHeader = headerAttribute.getValue(tuple);
            if (!additionalHeader.isEmpty()) {
                headerList.add(additionalHeader);
            }
        }
        Map<String, String> headerMap = HTTPUtils.getHeaderMapThrow(headerList);
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
        switch (getAuthenticationType()) {
        case STANDARD:
            break;
        case OAUTH1:
            getOAuthConsumer().sign(request);
            break;
        case OAUTH2:
            request.setHeader(getOAuth2AuthHeaderKey(), getOAuth2AuthHeaderValue());
            break;
        }
    }
    
    /**************************************************
     * set conn params
     **************************************************/
    private void setConnectionParams(HttpRequestBase request) {
        if (getConnectionTimeout() > 0) {
            if (tracer.isLoggable(TraceLevel.DEBUG))
                tracer.log(TraceLevel.DEBUG, "client configuration: setConnectTimeout=" + new Integer(getConnectionTimeout()).toString());
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(getConnectionTimeout()).build();
            request.setConfig(requestConfig);
        }
    }
    /******************************************************************************************************************
     * Create and add entity to request
     *******************************************************************************************************************/
    private void createEntity(HttpEntityEnclosingRequest req, Tuple tuple, ContentType contentType, boolean isPostMethod) throws IOException {
        if (isPostMethod) {
            if ((getRequestBodyAttribute() != null) && ( ! getRequestBodyAttribute().getValue(tuple).isEmpty())) {
                //take request body from input tuple transparently
                String instr = getRequestBodyAttribute().getValue(tuple);
                req.setEntity(new StringEntity(instr, contentType));
            } else {
                //take request attributes content type specific 
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
                    List<NameValuePair> params = new ArrayList<NameValuePair>();
                    while (ia.hasNext()) {
                        Attribute attr =ia.next();
                        String name = attr.getName();
                        if (getRequestAttributes().contains(name)) {
                            int index = attr.getIndex();
                            Object obj = tuple.getObject(index); //consider optional type
                            if (obj != null) {
                                String value = tuple.getString(index);
                                params.add(new BasicNameValuePair(name, value));
                            }
                        }
                    }
                    req.setEntity(new UrlEncodedFormEntity(params));
                } else {
                    StreamSchema ss = tuple.getStreamSchema();
                    Iterator<Attribute> ia = ss.iterator();
                    String payload = "";
                    while (ia.hasNext()) {
                        Attribute attr =ia.next();
                        String name = attr.getName();
                        if (getRequestAttributes().contains(name)) {
                            int index = attr.getIndex();
                            Object obj = tuple.getObject(index); //consider optional type
                            if (obj != null) {
                                String value = tuple.getString(index);
                                payload = payload+value;
                            }
                        }
                    }
                    req.setEntity(new StringEntity(payload, contentType));
                }
            }
        } else {
            //other methods take body from input tuple transparently
            String payload = "";
            if (getRequestBodyAttribute() != null)
                payload = getRequestBodyAttribute().getValue(tuple);
            req.setEntity(new StringEntity(payload, contentType));
        }
    }

    /******************************************************************************************************************
     * send output tuple
     * @throws Exception 
     ******************************************************************************************************************/
    void sendOtuple(Tuple inTuple, String statusLine, int statusCode, String contentEncoding, String contentType, List<RString> headers, String body, byte[] bodyRaw, String errorDiagnostics, String charSet) throws Exception {
        StreamingOutput<OutputTuple> op = getOutput(0);
        OutputTuple otup = op.newTuple();
        otup.assign(inTuple);
        if (getOutputStatus()          != null) otup.setString(getOutputStatus(),          statusLine);
        if (getOutputStatusCode()      != null) otup.setInt   (getOutputStatusCode(),      statusCode);
        if (getOutputContentEncoding() != null) otup.setString(getOutputContentEncoding(), contentEncoding);
        if (getOutputContentType()     != null) otup.setString(getOutputContentType(),     contentType);
        if (getOutputCharSet()         != null) otup.setString(getOutputCharSet(),         charSet);
        if (getOutputHeader()          != null) otup.setList  (getOutputHeader(),          headers);
        if (getOutputDataLine()        != null) otup.setString(getOutputDataLine(),        body);
        if (getOutputBody()            != null) otup.setString(getOutputBody(),            body);
        if (getErrorDiagnostics()      != null) otup.setString(getErrorDiagnostics(),      errorDiagnostics);
        if (getOutputBodyRaw() != null) {
            Blob blob = ValueFactory.newBlob(bodyRaw);
            otup.setBlob(getOutputBodyRaw(), blob);
        }
        op.submit(otup);
    }
    
    /*****************************************************************************************************************
     * send request
     * @throws Exception 
     ****************************************************************************************************************/
    void sendRequest(Tuple inTuple, HttpRequestBase request) throws Exception {
        String statusLine = "";
        int statusCode = -1;
        List<RString> headers = new ArrayList<>();
        String contentEncoding = "";
        String contentType = "";
        String charSet = "";
        String body = "";
        byte[] bodyRaw = {};
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
                    if ((method == HTTPMethod.PATCH) || (method == HTTPMethod.POST) || (method == HTTPMethod.PUT)) {
                        HttpEntityEnclosingRequest her = (HttpEntityEnclosingRequest) request;
                        HttpEntity he = her.getEntity();
                        if (he != null) {
                            String requestBody = EntityUtils.toString(he);
                            sb.append("\n").append(requestBody);
                        }
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
            
            nRequestTransmit.increment();
            HttpResponse response = getHttpClient().execute(request, getHttpContext());

            StatusLine status = response.getStatusLine();
            statusLine = status.toString();
            statusCode = status.getStatusCode();
            
            if ((statusCode < 200) || (statusCode > 299)) {
                nResponseNoSuccess.increment();
                if (tracer.isLoggable(TraceLevel.WARN)) tracer.log(TraceLevel.WARN, "status="+status.toString());
            } else {
                nResponseSuccess.increment();
                if (tracer.isLoggable(TraceLevel.DEBUG)) tracer.log(TraceLevel.DEBUG, "status="+status.toString());
            }
            HttpEntity entity = response.getEntity();
            boolean tupleSent = false;
            if (entity != null) {
                if (tracer.isLoggable(TraceLevel.TRACE))
                    tracer.log(TraceLevel.TRACE, "entitiy isChunked="+entity.isChunked()+" isRepeatable="+entity.isRepeatable()+" isStreaming="+entity.isStreaming());
                //content type and encoding
                Header contEnc = entity.getContentEncoding();
                if (contEnc != null) contentEncoding = contEnc.toString();
                //Header contType = entity.getContentType();
                //if (contType != null) contentType = contType.toString();
                ContentType contType = ContentType.getLenient(entity);
                if (contType != null) {
                    contentType = contType.getMimeType();
                    Charset cs = contType.getCharset();
                    if (cs != null ) charSet = cs.name();
                }
                //Response Headers
                HeaderIterator hi = response.headerIterator();
                while (hi.hasNext()) {
                    Header myh = hi.nextHeader();
                    String h = myh.toString();
                    RString rh = new RString(h);
                    headers.add(rh);
                }
                //Message Body
                if (hasDataPort()) {
                    if (getOutputDataLine() != null) { //one tuple per line
                        InputStream instream = entity.getContent();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(instream));
                        String inputLine = null;
                        while (!getShutdownRequested() && ((inputLine = reader.readLine()) != null)) {
                            sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, inputLine, null, "", charSet);
                            tupleSent = true;
                        }
                    }
                    if ((getOutputBody() != null) || (getOutputBodyRaw() != null)) {
                        if (getOutputBodyRaw() == null) { //get body only -> put content always into body
                            body = EntityUtils.toString(entity);
                        } else if (getOutputBody() == null) { //get raw body only -> put content always into body raw
                            bodyRaw = EntityUtils.toByteArray(entity);
                        } else { //both outputs
                            if ( (contType != null)
                                  && (contType.getMimeType().equals(ContentType.APPLICATION_OCTET_STREAM.getMimeType())
                                   || contType.getMimeType().equals(ContentType.DEFAULT_BINARY.getMimeType()))) {
                                bodyRaw = EntityUtils.toByteArray(entity);
                            } else {
                                body = EntityUtils.toString(entity);
                            }
                        }
                    }
                }
                EntityUtils.consume(entity);
            }
            if (hasDataPort()) {
                if (getOutputDataLine() != null) { //one tuple per line
                    if ( ! tupleSent) {
                        sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, "", null, "", charSet);
                    }
                } else {
                    sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, body, bodyRaw, "", charSet);
                }
            }
        } catch (ClientProtocolException e) {
            String errmess = e.getClass().getName() + ": " + e.getMessage();
            tracer.log(TraceLevel.ERROR, errmess);
            if (hasDataPort()) {
                sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, body, bodyRaw, errmess, charSet);
            }
        } catch (IOException e) {
            String errmess = e.getClass().getName() + ": " + e.getMessage();
            tracer.log(TraceLevel.ERROR, errmess);
            if (hasDataPort()) {
                sendOtuple(inTuple, statusLine, statusCode, contentEncoding, contentType, headers, body, bodyRaw, errmess, charSet);
            }
        } finally {
            if (getShutdownRequested()) {
                request.abort();
            } else {
                request.reset();
            }
        }
    }
}

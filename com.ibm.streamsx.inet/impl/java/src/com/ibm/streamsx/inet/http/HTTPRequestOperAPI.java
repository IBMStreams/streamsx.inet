//
// *******************************************************************************
// * Copyright (C)2016, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.http.entity.ContentType;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.model.Parameter;

/**
 * Handles the API (parameters) for the HTTPRequest operator.
 * 
 */
class HTTPRequestOperAPI extends AbstractOperator {

    static final String DESC = "Issue an HTTP request for each input tuple. "
            + "The URL and method of the HTTP request either come from the input tuple using attributes "
            + "specified by the `url` and `method` parameters, or can be fixed using the `fixedUrl` "
            + "and `fixedMethod` parameters. These parameters can be mixed, for example the URL "
            + "can be fixed with `fixedUrl` while the method is set from each tuple using `method`." + "\\n\\n"
            + "The contents of the request is dependent on the method type.\\n" + "# GET\\n"
            + "An HTTP GET request is made, any request attributes are converted to URL query parameters.\\n"
            + "# POST\\n"
            + "An HTTP PUT request is made, any request attributes are set as the body of the request message.\\n"
            + "# PUT\\n"
            + "An HTTP PUT request is made, any request attributes are set as the body of the request message.\\n"
            + "\\n\\n" + "# OPTIONS, HEAD, DELETE, TRACE, CONNECT\\n" + "Not yet supported.\\n"
            + "# Request Attributes\\n" + "Attributes from the input tuple are request parameters except for:\\n"
            + "* Any attribute specified by parameters `url` and `method`.\\n";

    /*
     * Operator parameters
     */
    private TupleAttribute<Tuple, String> url;
    private TupleAttribute<Tuple, String> method;
    private String fixedUrl;
    private HTTPMethod fixedMethod;
    private List<String> extraHeaders = Collections.emptyList();
    
    // TODO implement content type.
    @SuppressWarnings("unused")
    private ContentType contentType = ContentType.APPLICATION_JSON;

    /*
     * Values derived from the parameters.
     */

    // Function to return the method from an input tuple
    private Function<Tuple, HTTPMethod> methodGetter;

    // Function to return the URL from an input tuple
    private Function<Tuple, String> urlGetter;

    /**
     * Attributes that are part of the request.
     */
    private Set<String> requestAttributes = new HashSet<>();

    @Parameter(optional = true, description = "Attribute that specifies the URL to be used in the"
            + "HTTP request for a tuple.  One and only one of `url` and `fixedUrl` must be specified.")
    public void setUrl(TupleAttribute<Tuple, String> url) {
        this.url = url;
    }

    @Parameter(optional = true, description = "Fixed URL to send HTTP requests to. Any tuple received"
            + " on the input port results in a results to the URL provided."
            + " One and only one of `url` and `fixedUrl` must be specified.")
    public void setFixedUrl(String fixedUrl) {
        this.fixedUrl = fixedUrl;
    }

    @Parameter(optional = true, description = "Attribute that specifies the method to be used in the"
            + "HTTP request for a tuple.  One and only one of `method` and `fixedMethod` must be specified.")
    public void setMethod(TupleAttribute<Tuple, String> method) {
        this.method = method;
    }

    @Parameter(optional = true, description = "Fixed method for each HTTP request. Every HTTP request "
            + " uses the method provided. One and only one of `method` and `fixedMethod` must be specified.")
    public void setFixedMethod(HTTPMethod fixedMethod) {
        this.fixedMethod = fixedMethod;
    }

    @Parameter(optional = true, description = "Extra headers to send with request, format is `Header-Name: value`.")
    public void setExtraHeaders(List<String> extraHeaders) {
        this.extraHeaders = extraHeaders;
    }

    // TODO: use contentType
    @Parameter(optional = true, description = "MIME content type of entity for `POST` and `PUT` requests. "
            + "Supported values are `application/json` and `application/x-www-form-urlencoded`."
            + " Defaults to `application/json`.")
    public void setContentType(String ct) {
        if (ct.equals(ContentType.APPLICATION_JSON.getMimeType()))
            contentType = ContentType.APPLICATION_JSON;
        else if (ct.equals(ContentType.APPLICATION_FORM_URLENCODED.getMimeType()))
            contentType = ContentType.APPLICATION_FORM_URLENCODED;
        else
            throw new IllegalArgumentException(ct);
    }

    public void initialize(com.ibm.streams.operator.OperatorContext context) throws Exception {
        super.initialize(context);

        if (fixedMethod != null) {
            methodGetter = t -> fixedMethod;
        } else {
            methodGetter = tuple -> HTTPMethod.valueOf(method.getValue(tuple));
        }

        if (fixedUrl != null) {
            urlGetter = t -> fixedUrl;
        } else {
            urlGetter = tuple -> url.getValue(tuple);
        }

        // Assume request attributes (sent for any method that accepts an
        // entity)
        // are all attributes, excluding those that are used to specify the
        // URL or method.
        requestAttributes.addAll(getInput(0).getStreamSchema().getAttributeNames());
        if (url != null)
            requestAttributes.remove(url.getAttribute().getName());
        if (method != null)
            requestAttributes.remove(method.getAttribute().getName());
    }

    /*
     * Methods uses by implementation class
     */

    HTTPMethod getMethod(Tuple tuple) {
        return methodGetter.apply(tuple);
    }

    String getUrl(Tuple tuple) {
        return urlGetter.apply(tuple);
    }

    Set<String> getRequestAttributes() {
        return requestAttributes;
    }

    boolean isRequestAttribute(Object name) {
        return getRequestAttributes().contains(name);
    }

    public List<String> getExtraHeaders() {
        return extraHeaders;
    }
}
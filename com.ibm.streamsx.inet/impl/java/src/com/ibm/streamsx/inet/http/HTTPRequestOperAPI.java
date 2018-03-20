//
// *******************************************************************************
// * Copyright (C)2016, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import org.apache.http.entity.ContentType;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.meta.CollectionType;
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

    //register trace and log facility
    protected static Logger logger = Logger.getLogger("com.ibm.streams.operator.log." + HTTPRequestOperAPI.class.getName());
    protected static Logger tracer = Logger.getLogger(HTTPRequestOperAPI.class.getName());
    /*
     * Operator parameters
     */
    private TupleAttribute<Tuple, String> url;
    private TupleAttribute<Tuple, String> method;
    private String fixedUrl = null;
    private HTTPMethod fixedMethod = null;
    private List<String> extraHeaders = Collections.emptyList();
    private String dataAttributeName = null;
    private String bodyAttributeName = null;
    private String statusAttributeName = null;
    private String statusCodeAttributeName = null;
    private String headerAttributeName = null;
    private String contentEncodingAttributeName = null;
    private String contentTypeAttributeName = null;
    
    protected boolean shutdown = false;
    protected boolean hasDataPort = false;
    protected boolean hasErrorPort = false;
    
    protected ContentType contentType = ContentType.APPLICATION_JSON;

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
    protected Set<String> requestAttributes = new HashSet<>();

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
    public void setExtraHeaders(String[] extraHeaders) {
        //This is never called
        //this.extraHeaders = operatorContext.getParameterValues("extraHeaders");
        //this.extraHeaders = extraHeaders;
    }

    // TODO: use contentType
    @Parameter(optional = true, description = "MIME content type of entity for `POST` and `PUT` requests. "
            + "Supported values are `application/json` and `application/x-www-form-urlencoded`."
            + " Defaults to `application/json`.")
    public void setContentType(String contentType) {
        if (contentType.equals(ContentType.APPLICATION_JSON.getMimeType())) {
            this.contentType = ContentType.APPLICATION_JSON;
        } else if (contentType.equals(ContentType.APPLICATION_FORM_URLENCODED.getMimeType())) {
            this.contentType = ContentType.APPLICATION_FORM_URLENCODED;
        } else {
            String allowedIs = ContentType.APPLICATION_JSON.getMimeType()+
                           "|"+ContentType.APPLICATION_FORM_URLENCODED.getMimeType();
            throw new IllegalArgumentException("Argument of contentType:"+contentType+" is invalid! Allowed is:"+allowedIs);
        }
    }

    @Parameter(optional=true, description="Name of the attribute to populate the response data with. This parameter is "
                                        + "mandatory if the number of attributes of the output stream is greater than one.")
    public void setDataAttributeName(String val) {
        this.dataAttributeName = val;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response data with. This parameter is "
            + "mandatory if the number of attributes of the output stream is greater than one.")
    public void setBodyAttributeName(String val) {
        this.bodyAttributeName = val;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response data with. This parameter is "
            + "mandatory if the number of attributes of the output stream is greater than one.")
    public void setStatusAttributeName(String val) {
        this.statusAttributeName = val;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response data with. This parameter is "
            + "mandatory if the number of attributes of the output stream is greater than one.")
    public void setStatusCodeAttributeName(String val) {
        this.statusCodeAttributeName = val;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response data with. This parameter is "
            + "mandatory if the number of attributes of the output stream is greater than one.")
    public void setHeaderAttributeName(String val) {
        this.headerAttributeName = val;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response data with. This parameter is "
            + "mandatory if the number of attributes of the output stream is greater than one.")
    public void setContentEncodingAttributeName(String val) {
        this.contentEncodingAttributeName = val;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response data with. This parameter is "
            + "mandatory if the number of attributes of the output stream is greater than one.")
    public void setContentTypeAttributeName(String val) {
        this.contentTypeAttributeName = val;
    }
    @Parameter(optional=true, description="Names of the attributes which are part of the request body. The content of "
            + "these attributes are sent for any method that accepts an entity (PUT / POST). If this parameter is missing, "
            + "all attributes, excluding those that are used to specify the URL or method, are used in the request body.")
    public void setRequestAttributes(String[] requestAttributes) {
        //This is never called
    }

    /*****************************************
    * Initialize
    *****************************************/
    public void initialize(com.ibm.streams.operator.OperatorContext context) throws Exception {
        tracer.log(TraceLevel.TRACE, "initialize(context)");
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

        Set<String> parameterNames = context.getParameterNames();

        if (parameterNames.contains("extraHeaders")) {
            extraHeaders = context.getParameterValues("extraHeaders");
        }
        
        boolean hasOutputAttributeParameter = false;
        if (parameterNames.contains("dataAttributeName")
         || parameterNames.contains("bodyAttributeName")
         || parameterNames.contains("statusAttributeName")
         || parameterNames.contains("statusCodeAttributeName")
         || parameterNames.contains("headerAttributeName")
         || parameterNames.contains("contentEncodingAttributeName")
         || parameterNames.contains("contentTypeAttributeName")
         || parameterNames.contains("bodyAttributeName")
         || parameterNames.contains("bodyAttributeName")
         ) {
            hasOutputAttributeParameter = true;
        }
        if (context.getNumberOfStreamingOutputs() == 0) {
            if (hasOutputAttributeParameter)
                throw new Exception("Operator has output attribute name parameter but has no output port");
        } else {
            hasDataPort = true;
            if(getOutput(0).getStreamSchema().getAttributeCount() == 1) {
                if ( ! hasOutputAttributeParameter ) {
                    dataAttributeName = getOutput(0).getStreamSchema().getAttribute(0).getName();
                }
            }
            Set<String> outPortAttributes = getOutput(0).getStreamSchema().getAttributeNames();
            //final Set<String> outAttrNames = new HashSet<>("dataAttributeName", "bodyAttributeName", "statusAttributeName", "statusCodeAttributeName", "headerAttributeName");
            String missingOutAttribute = null;

            if (dataAttributeName != null) {
                if (outPortAttributes.contains(dataAttributeName)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(dataAttributeName).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException("Only types \""+MetaType.USTRING+"\" and \""+MetaType.RSTRING+"\" allowed for param \""+dataAttributeName+"\"");
                } else {
                    missingOutAttribute = dataAttributeName;
                }
            }
            
            if (bodyAttributeName != null) {
                if (outPortAttributes.contains(bodyAttributeName)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(bodyAttributeName).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException("Only types \""+MetaType.USTRING+"\" and \""+MetaType.RSTRING+"\" allowed for param \""+bodyAttributeName+"\"");
                } else {
                    missingOutAttribute = bodyAttributeName;
                }
            }
            
            if (statusAttributeName != null) {
                if (outPortAttributes.contains(statusAttributeName)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(statusAttributeName).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException("Only types \""+MetaType.USTRING+"\" and \""+MetaType.RSTRING+"\" allowed for param \""+statusAttributeName+"\"");
                } else {
                    missingOutAttribute = statusAttributeName;
                }
            }
            
            if (statusCodeAttributeName != null) {
                if (outPortAttributes.contains(statusCodeAttributeName)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(statusCodeAttributeName).getType().getMetaType();
                    if(paramType!=MetaType.INT32)
                        throw new IllegalArgumentException("Only types \""+MetaType.INT32+"\" allowed for param \""+statusCodeAttributeName+"\"");
                } else {
                    missingOutAttribute = statusCodeAttributeName;
                }
            }
            
            if (headerAttributeName != null) {
                if (outPortAttributes.contains(headerAttributeName)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(headerAttributeName).getType().getMetaType();
                    if(paramType==MetaType.LIST) {
                        Attribute attr = getOutput(0).getStreamSchema().getAttribute(headerAttributeName);
                        CollectionType collType = (CollectionType) attr.getType();
                        com.ibm.streams.operator.Type elemType = collType.getElementType();
                        MetaType lelemTypeM = elemType.getMetaType();
                        if (lelemTypeM != MetaType.RSTRING)
                            throw new IllegalArgumentException("Only element types \""+MetaType.RSTRING+"\" allowed for param \""+headerAttributeName+"\"");
                    } else {
                        throw new IllegalArgumentException("Only types \""+MetaType.LIST+"\" allowed for param \""+headerAttributeName+"\"");
                    }
                } else {
                    missingOutAttribute = headerAttributeName;
                }
            }
            
            if (contentEncodingAttributeName != null) {
                if (outPortAttributes.contains(contentEncodingAttributeName)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(contentEncodingAttributeName).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException("Only types \""+MetaType.USTRING+"\" and \""+MetaType.RSTRING+"\" allowed for param \""+contentEncodingAttributeName+"\"");
                } else {
                    missingOutAttribute = contentEncodingAttributeName;
                }
            }
            
            if (contentTypeAttributeName != null) {
                if (outPortAttributes.contains(contentTypeAttributeName)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(contentTypeAttributeName).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException("Only types \""+MetaType.USTRING+"\" and \""+MetaType.RSTRING+"\" allowed for param \""+contentTypeAttributeName+"\"");
                } else {
                    missingOutAttribute = contentTypeAttributeName;
                }
            }

            if (missingOutAttribute != null) 
                throw new IllegalArgumentException("No attribute with name "+missingOutAttribute+" found in schema of output port 0.");
        }

        //Request attributes
        StreamSchema ss = getInput(0).getStreamSchema();
        Set<String> inputAttributeNames = ss.getAttributeNames();
        if (parameterNames.contains("requestAttributes")) {
            List<String> reqAttrNames = context.getParameterValues("requestAttributes");
            if (inputAttributeNames.containsAll(reqAttrNames)) {
                requestAttributes.addAll(reqAttrNames);
            } else {
                throw new IllegalArgumentException("Input stream does not have all requestAttributes: "+reqAttrNames.toString());
            }
        } else {
            // Assume request attributes (sent for any method that accepts an entity)
            // are all attributes, excluding those that are used to specify the URL or method.
             requestAttributes.addAll(inputAttributeNames);
             if (url != null)
                 requestAttributes.remove(url.getAttribute().getName());
             if (method != null)
                 requestAttributes.remove(method.getAttribute().getName());
        }
        //Check whether all attributes are string type for urlencoded doc
        if (contentType.getMimeType() == ContentType.APPLICATION_FORM_URLENCODED.getMimeType()) {
            Iterator<Attribute> ia = ss.iterator();
            while (ia.hasNext()) {
                Attribute attr =ia.next();
                String name = attr.getName();
                if (requestAttributes.contains(name)) {
                    MetaType paramType = attr.getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING) {
                        throw new IllegalArgumentException("Attribute="+name+": If content type is:"+ContentType.APPLICATION_FORM_URLENCODED.getMimeType()+", request attributes must have type \""+MetaType.USTRING+"\" or \""+MetaType.RSTRING+"\"");
                    }
                }
            }
        }
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    /*
     * Methods uses by implementation class
     */

    HTTPMethod getMethod(Tuple tuple) { return methodGetter.apply(tuple); }

    String getUrl(Tuple tuple) { return urlGetter.apply(tuple); }

    Set<String> getRequestAttributes() { return requestAttributes; }

    boolean isRequestAttribute(Object name) {
        return getRequestAttributes().contains(name);
    }

    public List<String> getExtraHeaders() { return extraHeaders; }
    
    public String getDataAttributeName() { return dataAttributeName; }
    
    public String getBodyAttributeName() { return bodyAttributeName; }
    public String getStatusAttributeName() { return statusAttributeName; }
    public String getStatusCodeAttributeName() { return statusCodeAttributeName; }
    public String getHeaderAttributeName() { return headerAttributeName; }
    public String getContentEncodingAttributeName() { return contentEncodingAttributeName; }
    public String getContentTypeAttributeName() { return contentTypeAttributeName; }

}
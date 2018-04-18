//
// *******************************************************************************
// * Copyright (C)2016, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.meta.CollectionType;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.state.ConsistentRegionContext;
import com.ibm.streamsx.inet.messages.Messages;

/**
 * Handles the API (parameters) for the HTTPRequest operator.
 * 
 */
class HTTPRequestOperAPI extends AbstractOperator {

    static final String DESC = "Issue an HTTP request of the specified method for each input tuple. For method `NONE`, the request is supressed."
            + "The URL and  method of the HTTP request either come from the input tuple using attributes "
            + "specified by the `url` and `method` parameters, or can be fixed using the `fixedUrl` "
            + "and `fixedMethod` parameters. These parameters can be mixed, for example the URL "
            + "can be fixed with `fixedUrl` while the method is set from each tuple using `method`. "
            + "A content type is required for `POST`, `PUT` and `PATCH` method. The content type is specified by `contenType`or "
            + "`fixedContentType` parameter.\\n\\n"
            + "The contents of the request is dependent on the method type.\\n"
            + "# GET\\n"
            + "An HTTP GET request is made. If parameter `requestAttributesAsUrlArguments` is true, all request attributes "
            + "are converted to URL query parameters. If parameter `requestUrlArguments` is specified and this attribute is not "
            + "empty, this attribute is copied as URL argument string and overwrites all other arguments.\\n"
            + "# POST\\n"
            + "An HTTP POST request is made, any request attributes are set as the body of the request message if parameter "
            + "`requestBody` is not present or the value of the attribute is empty. The encoding of the request body takes "
            + "the content type into account. If content type is `application/json`, a json body is generated from request attributes. "
            + "If content type is `APPLICATION_FORM_URLENCODED`, a url encoded body is generated from request attributes. "
            + "For all other content types the content of all request attributes is concatenated into the message body."
            + "If `requestBody` attribute is not empty, the body of the request is copied from this attribute instead.\\n"
            + "# PUT\\n"
            + "An HTTP PUT request is made, the body of the request message is copied from `requestBody` attribute.\\n"
            + "# PATCH\\n"
            + "An HTTP PATCH request is made, the body of the request message is copied from `requestBody` attribute.\\n"
            + "# OPTIONS\\n"
            + "No message body is generated.\\n"
            + "# HEAD\\n"
            + "An HTTP HEAD request is made.\\n"
            + "# DELETE\\n"
            + "No message body is generated.\\n"
            + "# TRACE\\n"
            + "No message body is generated.\\n"
            + "# NONE\\n"
            + "No http request is generated but an output tuple is genrated if the output port is present.\\n"
            + "# Request Attributes\\n"
            + "Attributes from the input tuple are request parameters except for:\\n"
            + "* Any attribute specified by parameters `url`, `method`, `contentType`, `requestBody` or `equestUrlArguments`.\\n"
            + "* If parameter `requestAttributes` is set, all attributes of this parameter are considered a request attribute.\\n"
            + "* If parameter `requestAttributes` has one empty element, no attributes are considered a request attribute.\\n"
            + "# Http Authentication\\n"
            + "The operator supports the following authentication methods: Basic, Digest, OAuth1a and OAuth2.0; see parameter `authenticationType`.\\n"
            + "# Behavior in a consistent region\\n"
            + "This operator cannot be used inside a consistent region.";

    public static final String OPER_NAME="HTTPRequest";

    public enum AuthenticationType {
        STANDARD,
        OAUTH1,
        OAUTH2
    }

    //register trace and log facility
    //protected static Logger logger = Logger.getLogger("com.ibm.streams.operator.log." + HTTPRequestOperAPI.class.getName()); logger is not required
    protected static final Logger tracer = Logger.getLogger(HTTPRequestOperAPI.class.getName());
    
    //request parameters
    private String                        fixedUrl = null;
    private TupleAttribute<Tuple, String> url;
    private HTTPMethod                    fixedMethod = null;
    private TupleAttribute<Tuple, String> method;
    private String                        fixedContentType = null;
    private TupleAttribute<Tuple, String> contentType;
    private ContentType                   contentTypeToUse = null;
    private List<String>                  extraHeaders = Collections.emptyList();
    
    //request configuration
    private TupleAttribute<Tuple, String> requestBody = null;  //request body
    private Set<String>                   requestAttributes = new HashSet<>(); //Attributes that are part of the request.
    private boolean                       requestAttributesAsUrlArguments = false;
    private TupleAttribute<Tuple, String> requestUrlArguments = null;
    
    //output parameters
    private String outputDataLine = null;
    private String outputBody = null;
    private String outputStatus = null;
    private String outputStatusCode = null;
    private String outputHeader = null;
    private String outputContentEncoding = null;
    private String outputContentType = null;
    
    //connection configs
    private AuthenticationType authenticationType = AuthenticationType.STANDARD;
    private String             authenticationFile = null;
    private List<String>       authenticationProperties = null;
    private boolean            sslAcceptAllCertificates = false;
    private String             sslTrustStoreFile = null;
    private String             sslTrustStorePassword = null;
    private String             proxy = null;
    private int                proxyPort = 8080;
    private boolean            disableRedirectHandling = false;
    private boolean            disableContentCompression = false;
    private boolean            disableAutomaticRetries = false;
    private int                connectionTimeout = 0;

    //internal operator state
    private boolean shutdownRequested = false;
    private boolean hasDataPort = false;
    //private boolean hasOutputAttributeParameter = false;

    // Function to return the url, method, content type from an input tuple or fixed
    private Function<Tuple, HTTPMethod>  methodGetter;
    private Function<Tuple, String>      urlGetter;
    private Function<Tuple, ContentType> contentTypeGetter;
    

    /********************************
     * request parameters
     ********************************/
    @Parameter(optional = true, description = "Attribute that specifies the URL to be used in the"
            + "HTTP request for a tuple. One and only one of `url` and `fixedUrl` must be specified.")
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
            + "HTTP request for a tuple. One and only one of `method` and `fixedMethod` must be specified.")
    public void setMethod(TupleAttribute<Tuple, String> method) {
        this.method = method;
    }
    @Parameter(optional = true, description = "Fixed method for each HTTP request. Every HTTP request "
            + " uses the method provided. One and only one of `method` and `fixedMethod` must be specified.")
    public void setFixedMethod(HTTPMethod fixedMethod) {
        this.fixedMethod = fixedMethod;
    }
    @Parameter(optional = true, description = "Fixed MIME content type of entity for `POST` and `PUT` requests. "
            + "Only one of `contentType` and `fixedContentType` must be specified."
            + " Defaults to `application/json`.")
    public void setFixedContentType(String fixedContentType) {
        this.fixedContentType = fixedContentType;
    }
    @Parameter(optional = true, description = "MIME content type of entity for `POST` and `PUT` requests. "
            + "Only one of `contentType` and `fixedContentType` must be specified."
            + " Defaults to `application/json`.")
    public void setContentType(TupleAttribute<Tuple, String> contentType) {
        this.contentType = contentType;
    }
    @Parameter(optional = true, description = "Extra headers to send with request, format is `Header-Name: value`.")
    public void setExtraHeaders(String[] extraHeaders) {
        //This is never called get values in initialize method
    }
    
    /***************************************
     * request configuration
     ***************************************/
    @Parameter(optional=true, description="Names of the attributes which are part of the request body. The content of "
            + "these attributes are sent as request body in method POST. If parameter `requestAttributesAsUrlArguments` is true, "
            + "the request attributes are additionally appended as arguments to the url in method GET. "
            + "If this parameter is missing, "
            + "all attributes, excluding those that are used to specify the URL, method, content type, Request url arguments"
            + "or request attributes, are used in the request body. "
            + "One empty element defines an empty list which means no attributes are considered request attributes. ")
    public void setRequestAttributes(String[] requestAttributes) {
        //This is never called !? Is set in initialize
    }
    @Parameter(optional=true, description="If this parameter is true, the request attributes are appended as arguments to the url in method GET. "
            + "If this parameter is false, the request attributes are not appended to the url. Default is false. "
            + "If this parameter is true, all request attributes must have spl type `rstring` or `ustring`."
            + "This arguments are overwritten from a non empty value in parameter `requestUrlArguments`.")
    public void setRequestAttributesAsUrlArguments(boolean requestAttributesAsUrlArguments) {
        this.requestAttributesAsUrlArguments = requestAttributesAsUrlArguments;
    }
    @Parameter(optional=true, description="Request url arguments attribute. If this parameter is set and the value of "
            + "this attribute  is not empty, the content of this string is appended as arguments to the request url. "
            + "This overwrites the arguments which are generated from the request attributes. "
            + "The value is expected to be unescaped and may contain non ASCII characters")
    public void setRequestUrlArguments(TupleAttribute<Tuple, String> requestUrlArguments) {
        this.requestUrlArguments = requestUrlArguments;
    }
    @Parameter(optional=true, description="Request body attribute for for any method that accepts an entity (PUT / POST / PATCH). "
            + "In method PUT and PATCH the body of request is taken from this attribute. "
            + "In method POST, any non empty value overwrites the request attributes.")
    public void setRequestBody(TupleAttribute<Tuple, String> requestBody) {
        System.out.println("set attribute param");
        this.requestBody = requestBody;
    }
    
    /********************************
     * output parameters
     ********************************/
    @Parameter(optional=true, description="Name of the attribute to populate one line of the response data with. "
        + "If this parameter is set, the operators returns one tuple for each line in the resonse body but at least one tuple if the body is empty. "
        + "Only one of `outputDataLine` and `outputBody` must be specified."
        + "This parameter is not allowed if the operator has no output port. ")
        //+ "If the number of attributes of the output stream is greater than one, at least one of "
        //+ "`dataAttributeName`, `bodyAttributeName`, `statusAttributeName`, `statusCodeAttributeName`, `headerAttributeName, contentEncodingAttributeName or contentTypeAttributeName must be set.")
    public void setOutputDataLine(String outputDataLine) {
        this.outputDataLine = outputDataLine;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response body with. "
        + "If this parameter is set, the operators returns one tuple for each request. "
        + "Only one of `outputDataLine` and `outputBody` must be specified."
        + "This parameter is not allowed if the operator has no output port. ")
        //+ "This parameter is mandatory if the number of attributes of the output stream is greater than one.")
    public void setOutputBody(String outputBody) {
        this.outputBody = outputBody;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response status line with. "
        + "This parameter is not allowed if the operator has no output port. ")
        //+ "This parameter is mandatory if the number of attributes of the output stream is greater than one.")
    public void setOutputStatus(String outputStatus) {
        this.outputStatus = outputStatus;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response status code as integer with. "
        + "The type of this attribute must be int32. "
        + "This parameter is not allowed if the operator has no output port. ")
        //+ "This parameter is mandatory if the number of attributes of the output stream is greater than one.")
    public void setOutputStatusCode(String outputStatusCode) {
        this.outputStatusCode = outputStatusCode;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response header information with. "
        + "The type of this attribute must be string list. "
        + "This parameter is not allowed if the operator has no output port. ")
        //+ "This parameter is mandatory if the number of attributes of the output stream is greater than one.")
    public void setOutputHeader(String outputHeader) {
        this.outputHeader = outputHeader;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response data with. "
        + "This parameter is not allowed if the operator has no output port. ")
        //+ "This parameter is mandatory if the number of attributes of the output stream is greater than one.")
    public void setOutputContentEncoding(String outputContentEncoding) {
        this.outputContentEncoding = outputContentEncoding;
    }
    @Parameter(optional=true, description="Name of the attribute to populate the response data with. "
        + "This parameter is not allowed if the operator has no output port. ")
        //+ "This parameter is mandatory if the number of attributes of the output stream is greater than one.")
    public void setOutputContentType(String outputContentType) {
        this.outputContentType = outputContentType;
    }

    /************************************
     * connection config params 
     ************************************/
    /* streams error if there are more than one enum in a java op thus I use here a string */ 
    @Parameter(optional=true, description="The type of used authentication method. "
        + "Valid options are \\\"STANDARD\\\", \\\"OAUTH1,\\\" and \\\"OAUTH2\\\". Default is \\\"STANDARD\\\". "
        + "If \\\"STANDARD\\\" is selected, the authorization may be none, basic or digest authorization. "
        + "If the server requires basic or digest autorization one of the parameters `authenticationFile` or `authenticationProperties` is required. "
        + "If the \\\"OAUTH1\\\" option is selected, the requests will be singed using OAuth 1.0a "
        + "If the \\\"OAUTH2\\\" option is selected, the requests will be singed using OAuth 2.0.")
    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = AuthenticationType.valueOf(authenticationType);
    }
    @Parameter(optional=true, description="Path to the properties file containing authentication information. "
        + "Authentication file is recommended to be stored in the application_dir/etc directory. "
        + "Path of this file can be absolute or relative, if relative path is specified then it is relative to the application directory. "
        + "The content of this file depends on the `authenticationType`.\\n"
        + "* If `authenticationType` is `STANDARD`: "
        + "A valid line is composed from the authentication Scope (hostname or `ANY_HOST`, equal sign, user, colon, password. "
        + "E.g.: ANY_HOST=user:passwd\\n"
        + "* If `authenticationType` is `OAUTH1`: "
        + "The athentication file must contain key/value pairs for the keys: `consumerKey`, `consumerSecret`, `accessToken` and `accessTokenSecret`.\\n"
        + "* If `authenticationType` is `OAUTH2`: "
        + "The athentication file must contain one key/value pair for key `accessToken=myAccessToken`.\\n"
        + "The athentication file may contain one key/value pair for key `authMethod`.\\n"
        + "See `http_request_auth.properties`, `http_request_oauth1.properties` and `http_request_oauth2.properties` in the toolkits etc directory for a sample of authentication properties.")
    public void setAuthenticationFile(String val) {
        this.authenticationFile = val;
    }
    @Parameter(optional=true, description="Properties to override those in the authentication file.")
    public void setAuthenticationProperties(String[] authenticationProperties) {
        //This is never called !? Is set in initialize
    }
    @Parameter(optional=true, description="Accept all SSL certificates, even those that are self-signed. "
        + "If this parameter is set, parameter `sslTrustStoreFile` is not allowed. "
        + "Setting this option will allow potentially insecure connections. Default is false.")
    public void setSslAcceptAllCertificates(boolean sslAcceptAllCertificates) {
        this.sslAcceptAllCertificates = sslAcceptAllCertificates;
    }
    @Parameter(optional=true, description="Path to .jks trust store file used for TODO: ?server? and client authentication. "
        + "If this parameter is set, parameter `sslTrustStorePassword` is required.")
    public void setSslTrustStoreFile(String sslTrustStoreFile){
        this.sslTrustStoreFile = sslTrustStoreFile;
    }
    @Parameter(optional=true, description="Password for the trust store and the keys it contains")
    public void setSslTrustStorePassword(String sslTrustStorePassword){
        this.sslTrustStorePassword = sslTrustStorePassword;
    }
    @Parameter(optional=true, description="Hostname of the http-proxy to be used. If this parameter is omitted no proxy is used.")
    public void setProxy(String proxy) {
        this.proxy = proxy;
    }
    @Parameter(optional=true, description="The proxyport to be used. Default value is 8080. This parameter is ignored if no `proxy` parameter is specified.")
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }
    @Parameter(optional=true, description="Disables automatic redirect handling. Default is false.")
    public void setDisableRedirectHandling(boolean disableRedirectHandling) {
        this.disableRedirectHandling = disableRedirectHandling;
    }
    @Parameter(optional=true, description="Disables automatic content decompression. Default is false")
    public void setDisableContentCompression(boolean disableContentCompression) {
        this.disableContentCompression = disableContentCompression;
    }
    @Parameter(optional=true, description="Disables automatic request recovery and re-execution. Default is false")
    public void setDisableAutomaticRetries(boolean disableAutomaticRetries) {
        this.disableAutomaticRetries = disableAutomaticRetries;
    }
    @Parameter(optional=true, description="Set the connection timeout in milliseconds. If value is 0, the default connection timeout is used. Default is 0.")
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    /********************************************
     * compile time checks
     ********************************************/
    public static void checkInConsistentRegion(OperatorContextChecker checker) {
        ConsistentRegionContext consistentRegionContext = checker.getOperatorContext().getOptionalContext(ConsistentRegionContext.class);
        if(consistentRegionContext != null) {
            checker.setInvalidContext(Messages.getString("CONSISTENT_CHECK_2"), new String[] {OPER_NAME});
        }
    }
    public static void checkMethodParams(OperatorContextChecker occ) {
        Set<String> parameterNames = occ.getOperatorContext().getParameterNames();
        if (! parameterNames.contains("method") && ! parameterNames.contains("fixedMethod")) {
            occ.setInvalidContext(Messages.getString("PARAM_METHOD_CHECK"), new String[] {OPER_NAME});
        }
        if (! parameterNames.contains("url") && ! parameterNames.contains("fixedUrl")) {
            occ.setInvalidContext(Messages.getString("PARAM_URL_CHECK"), new String[] {OPER_NAME});
        }
        occ.checkExcludedParameters("method", "fixedMethod");
        occ.checkExcludedParameters("url", "fixedUrl");
        occ.checkExcludedParameters("contentType", "fixedContentType");
        occ.checkExcludedParameters("outpuData", "outputBody");
        //occ.checkExcludedParameters("requestAttributes", "requestBody");
        
        //The pair of these parameters is optional, we either need both to be present or neither of them
        boolean hasFile = parameterNames.contains("sslTrustStoreFile");
        boolean hasPassword = parameterNames.contains("sslTrustStorePassword");
        if(hasFile ^ hasPassword) {
            occ.setInvalidContext(Messages.getString("PARAM_TRUST_STORE_CHECK"), new String[] {OPER_NAME});
        }
        occ.checkExcludedParameters("sslAcceptAllCertificates", "sslTrustStoreFile");
    }

    /*****************************************
    * Initialize
    * @throws Exception 
    *****************************************/
    @Override
    public void initialize(com.ibm.streams.operator.OperatorContext context) throws Exception {
        tracer.log(TraceLevel.TRACE, "initialize(context)");
        super.initialize(context);

        Set<String> parameterNames = context.getParameterNames();

        //Url
        if (fixedUrl != null) {
            urlGetter = t -> fixedUrl;
        } else {
            urlGetter = tuple -> url.getValue(tuple);
        }
        //Method
        if (fixedMethod != null) {
            methodGetter = t -> fixedMethod;
        } else {
            methodGetter = tuple -> HTTPMethod.valueOf(method.getValue(tuple));
        }
        //content type
        if ((fixedContentType == null) && (contentType == null))
            fixedContentType = ContentType.APPLICATION_JSON.getMimeType();
        if (fixedContentType != null) {
            contentTypeToUse = ContentType.getByMimeType(fixedContentType);
            if (contentTypeToUse == null) {
                throw new IllegalArgumentException(Messages.getString("PARAM_CONTENT_TYPE_CHECK", fixedContentType));
            }
            contentTypeGetter = t -> contentTypeToUse;
        } else {
            contentTypeGetter = tuple -> ContentType.getByMimeType(contentType.getValue(tuple));
        }
        //get values of list type here
        if (parameterNames.contains("extraHeaders")) {
            extraHeaders = context.getParameterValues("extraHeaders");
        }
        if (parameterNames.contains("authenticationProperties")) {
            authenticationProperties = context.getParameterValues("authenticationProperties");
        }

        //Check whether all required request attributes are in input stream
        StreamSchema ss = getInput(0).getStreamSchema();
        Set<String> inputAttributeNames = ss.getAttributeNames();
        if (parameterNames.contains("requestAttributes")) {
            //read all attribute names from parameter
            List<String> reqAttrNames = new ArrayList<String>();
            reqAttrNames.addAll(context.getParameterValues("requestAttributes"));
            if ((reqAttrNames.size() == 1) && reqAttrNames.get(0).isEmpty()) //remove if there is only one empty element
                reqAttrNames.remove(0);
            if (inputAttributeNames.containsAll(reqAttrNames)) {
                requestAttributes.addAll(reqAttrNames);
            } else {
                throw new IllegalArgumentException(Messages.getString("PARAM_REQUEST_ATTRIBUTE_CHECK", reqAttrNames.toString()));
            }
        } else {
            //no parameter 'requestAttributes' -> collect remaining attributes
            // Assume request attributes (sent for any method that accepts an entity)
            // are all attributes, excluding those that are used to specify the URL, method, content type, request body or request url args.
            requestAttributes.addAll(inputAttributeNames);
            if (url != null)
                requestAttributes.remove(url.getAttribute().getName());
            if (method != null)
                requestAttributes.remove(method.getAttribute().getName());
            if (contentType != null)
                requestAttributes.remove(contentType.getAttribute().getName());
            if (requestUrlArguments != null)
                requestAttributes.remove(requestUrlArguments.getAttribute().getName());
            if (requestBody != null)
                requestAttributes.remove(requestBody.getAttribute().getName());
        }
        //Check whether all attributes are string type if fixed content type is ne json
        /*if (((fixedContentType != null) && (contentTypeToUse != ContentType.APPLICATION_JSON)) || requestAttributesAsUrlArguments) {
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
        }*/
        
        //output params ...
        boolean hasOutputAttributeParameter = false;
        if (parameterNames.contains("outputData")
         || parameterNames.contains("outputBody")
         || parameterNames.contains("outputStatus")
         || parameterNames.contains("outputStatusCode")
         || parameterNames.contains("outputHeader")
         || parameterNames.contains("outputContentEncoding")
         || parameterNames.contains("outputContentType")
         ) {
            hasOutputAttributeParameter = true;
        }
        if (context.getNumberOfStreamingOutputs() == 0) {
            if (hasOutputAttributeParameter)
                throw new Exception(Messages.getString("PARAM_NO_OUTPUT_PORT_CHECK"));
        } else {
            hasDataPort = true;
            
            Set<String> outPortAttributes = getOutput(0).getStreamSchema().getAttributeNames();
            String missingOutAttribute = null;
            if (outputDataLine != null) {
                if (outPortAttributes.contains(outputDataLine)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(outputDataLine).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException(Messages.getString("PARAM_ATTRIBUTE_TYPE_CHECK_2", MetaType.USTRING, MetaType.RSTRING, outputDataLine));
                } else {
                    missingOutAttribute = outputDataLine;
                }
            }
            if (outputBody != null) {
                if (outPortAttributes.contains(outputBody)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(outputBody).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException(Messages.getString("PARAM_ATTRIBUTE_TYPE_CHECK_2", MetaType.USTRING, MetaType.RSTRING, outputBody));
                } else {
                    missingOutAttribute = outputBody;
                }
            }
            if (outputStatus != null) {
                if (outPortAttributes.contains(outputStatus)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(outputStatus).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException(Messages.getString("PARAM_ATTRIBUTE_TYPE_CHECK_2", MetaType.USTRING, MetaType.RSTRING, outputStatus));
                } else {
                    missingOutAttribute = outputStatus;
                }
            }
            if (outputStatusCode != null) {
                if (outPortAttributes.contains(outputStatusCode)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(outputStatusCode).getType().getMetaType();
                    if(paramType!=MetaType.INT32)
                        throw new IllegalArgumentException(Messages.getString("PARAM_ATTRIBUTE_TYPE_CHECK_1", MetaType.INT32, outputStatusCode));
                } else {
                    missingOutAttribute = outputStatusCode;
                }
            }
            if (outputHeader != null) {
                if (outPortAttributes.contains(outputHeader)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(outputHeader).getType().getMetaType();
                    if(paramType==MetaType.LIST) {
                        Attribute attr = getOutput(0).getStreamSchema().getAttribute(outputHeader);
                        CollectionType collType = (CollectionType) attr.getType();
                        com.ibm.streams.operator.Type elemType = collType.getElementType();
                        MetaType lelemTypeM = elemType.getMetaType();
                        if (lelemTypeM != MetaType.RSTRING)
                            throw new IllegalArgumentException(Messages.getString("PARAM_ATTRIBUTE_ELEMENT_TYPE_CHECK_1", MetaType.RSTRING, outputHeader));
                    } else {
                        throw new IllegalArgumentException(Messages.getString("PARAM_ATTRIBUTE_TYPE_CHECK_1", MetaType.LIST, outputHeader));
                    }
                } else {
                    missingOutAttribute = outputHeader;
                }
            }
            if (outputContentEncoding != null) {
                if (outPortAttributes.contains(outputContentEncoding)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(outputContentEncoding).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException(Messages.getString("PARAM_ATTRIBUTE_TYPE_CHECK_2", MetaType.USTRING, MetaType.RSTRING, outputContentEncoding));
                } else {
                    missingOutAttribute = outputContentEncoding;
                }
            }
            if (outputContentType != null) {
                if (outPortAttributes.contains(outputContentType)) {
                    MetaType paramType = getOutput(0).getStreamSchema().getAttribute(outputContentType).getType().getMetaType();
                    if(paramType!=MetaType.USTRING && paramType!=MetaType.RSTRING)
                        throw new IllegalArgumentException(Messages.getString("PARAM_ATTRIBUTE_TYPE_CHECK_2", MetaType.USTRING, MetaType.RSTRING, outputContentType));
                } else {
                    missingOutAttribute = outputContentType;
                }
            }
            if (missingOutAttribute != null) 
                throw new IllegalArgumentException(Messages.getString("PARAM_MISSING_ATTRIBUTE", missingOutAttribute));
        }

        //trust store 
        URI baseConfigURI = context.getPE().getApplicationDirectory().toURI();
        sslTrustStoreFile = PathConversionHelper.convertToAbsPath(baseConfigURI, sslTrustStoreFile);
        authenticationFile = PathConversionHelper.convertToAbsPath(baseConfigURI, authenticationFile);
    }

    @Override
    public void shutdown() {
        shutdownRequested = true;
    }

    /*
     * Methods uses by implementation class
     */
    protected boolean isRequestAttribute(Object name) {
        return requestAttributes.contains(name);
    }
    protected HTTPMethod getMethod(Tuple tuple) { return methodGetter.apply(tuple); }
    protected String getUrl(Tuple tuple) { return urlGetter.apply(tuple); }
    protected ContentType getContentType(Tuple tuple) { return contentTypeGetter.apply(tuple); }

    //getter
    protected List<String>                  getExtraHeaders() { return extraHeaders; }
    
    //request config
    protected TupleAttribute<Tuple, String> getRequestBody() { return requestBody; }
    protected Set<String>                   getRequestAttributes() { return  requestAttributes; }
    protected boolean                       getRequestAttributesAsUrlArguments() { return requestAttributesAsUrlArguments; }
    protected TupleAttribute<Tuple, String> getRequestUrlArguments() { return requestUrlArguments; }

    
    //output parameters
    protected String getOutputDataLine() { return outputDataLine; }
    protected String getOutputBody() { return outputBody; }
    protected String getOutputStatus() { return outputStatus; }
    protected String getOutputStatusCode() {return outputStatusCode; }
    protected String getOutputHeader() { return outputHeader; }
    protected String getOutputContentEncoding() { return outputContentEncoding; }
    protected String getOutputContentType() { return outputContentType; }
    //connection configs
    protected AuthenticationType getAuthenticationType() { return authenticationType; }
    protected String             getAuthenticationFile() { return authenticationFile; }
    protected List<String>       getAuthenticationProperties() { return authenticationProperties; }
    protected boolean            getSslAcceptAllCertificates() { return sslAcceptAllCertificates; }
    protected String             getSslTrustStoreFile() { return sslTrustStoreFile; }
    protected String             getSslTrustStorePassword() { return sslTrustStorePassword; }
    protected String             getProxy() { return proxy; }
    protected int                getProxyPort() { return proxyPort; }
    protected boolean            getDisableRedirectHandling() { return disableRedirectHandling; }
    protected boolean            getDisableContentCompression() { return disableContentCompression; }
    protected boolean            getDisableAutomaticRetries() { return disableAutomaticRetries; }
    protected int                getConnectionTimeout() { return connectionTimeout; }
    //internal operator state
    protected boolean getShutdownRequested() { return shutdownRequested; }
    protected boolean hasDataPort() { return hasDataPort; }
    //protected boolean getHasOutputAttributeParameter() { return hasOutputAttributeParameter; }
}

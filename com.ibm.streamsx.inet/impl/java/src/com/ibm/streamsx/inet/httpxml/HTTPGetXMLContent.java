/*
 * Copyright (C) 2014, International Business Machines Corporation
 * All Rights Reserved.
 */
package com.ibm.streamsx.inet.httpxml;

import org.apache.http.HttpEntity;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.types.ValueFactory;
import com.ibm.streams.operator.types.XML;
import com.ibm.streamsx.inet.http.AbstractHTTPGetContent;

/**
 * HTTP GET of application/xml content.
 *
 */
@PrimitiveOperator(description = HTTPGetXMLContent.DESC, namespace = "com.ibm.streamsx.inet.http")
// Can't create a control port because TupleConsumer has process as final.
// @InputPortSet(optional=true, cardinality=1, controlPort=true,
// description="Control port to change the URL used for the HTTP GET.")
@OutputPortSet(cardinality = 1, windowPunctuationOutputMode = WindowPunctuationOutputMode.Free, description = "Content of the HTTP GET request as an XML attribute. Each successful HTTP request that returns a "
        + "single well-formed XML document results in a submitted tuple with an XML attribute containing the returned content.")
@Icons(location32 = "impl/java/icons/HTTPGetXMLContent_32.gif", location16 = "impl/java/icons/HTTPGetXMLContent_16.gif")
public class HTTPGetXMLContent extends AbstractHTTPGetContent<XML> {

    static final String DESC = "Periodically connects to an HTTP endpoint to GET XML content as a single tuple. "
            + "The XML content is assigned  to the first attribute in the output tuple which must be "
            + "of type `xml`."
            + ""
            + "The URL can have a single query parameter updated using the `updateParameter` parameter."
            + "When set the URL query string will be modified to set the named parameter to a new value."
            + "The default action is to set it to the number of milliseconds since the 1970 epoch.";

    private String updateParameter;
    private String updateXPath;
    private UpdateParameter updater;

    @Parameter(optional = true, description = "URL query parameter to update based upon content in a successful request.")
    public void setUpdateParameter(String updateParameter) {
        this.updateParameter = updateParameter;
    }

    public String getUpdateXPath() {
        return updateXPath;
    }

    @Parameter(name = "updateParameterFromContent", optional = true, description = "Update the query parameter set in `updateParameter` from the value of this XPath expression against the returned content.")
    public void setUpdateXPath(String updateXPath) {
        this.updateXPath = updateXPath;
    }

    @ContextCheck
    public static void checkParameters(OperatorContextChecker checker) {
        checker.checkDependentParameters("updateParameterFromContent",
                "updateParameter");
    }

    @Override
    public synchronized void initialize(OperatorContext context)
            throws Exception {
        super.initialize(context);

        if (updateParameter != null) {
            if (getUpdateXPath() != null) {
                // Update using XPath
                updater = new UpdateFromXPath(this, getUpdateXPath());
            } else {
                // default updater using the current time.
                updater = new UpdateCurrentTimeMills();
            }
        }
    }

    @Override
    protected String acceptedContentTypes() {
        return "application/xml, text/xml";
    }

    @Override
    protected void submitContents(OutputTuple tuple, HttpEntity content)
            throws Exception {
        final XML xml = ValueFactory.newXML(content.getContent());
        tuple.setXML(contentAttributeIndex, xml);
        if (updater != null) {
            if (!xml.isDefaultValue())
                updater.update(xml);
        }
    }

    /**
     * How the URL query parameter will be updated.
     */
    abstract class UpdateParameter {

        void update(XML xml) throws Exception {
            String value = getValue(xml);
            if (value != null) {
                builder.setParameter(updateParameter, value);
                get.setURI(builder.build());
                trace.info("Updated URL: " + get.getURI().toString());
            }
        }

        abstract String getValue(XML xml) throws Exception;
    }

    /**
     * Update the query attribute to the current time in milliseconds.
     */
    class UpdateCurrentTimeMills extends UpdateParameter {

        @Override
        public String getValue(XML xml) throws Exception {
            return Long.toString(System.currentTimeMillis());
        }
    }
}

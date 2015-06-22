/*
 * Copyright (C) 2014, International Business Machines Corporation
 * All Rights Reserved.
 */
package com.ibm.streamsx.inet.httpjson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;

import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streamsx.inet.http.AbstractHTTPGetContent;

/**
 * HTTP GET of application/json content.
 *
 */
@PrimitiveOperator(description = HTTPGetJSONContent.DESC, namespace = "com.ibm.streamsx.inet.http")
@OutputPortSet(cardinality = 1, windowPunctuationOutputMode = WindowPunctuationOutputMode.Free, description = "Content of the HTTP GET request as an JSON attribute. Each successful HTTP request that returns a "
        + "content results in a submitted tuple with an rstring attribute containing the returned content.")
@Icons(location32 = "impl/java/icons/HTTPGetXMLContent_32.gif", location16 = "impl/java/icons/HTTPGetXMLContent_16.gif")
public class HTTPGetJSONContent extends AbstractHTTPGetContent<String> {

    static final String DESC = "Periodically connects to an HTTP endpoint to GET JSON content as a single tuple. "
            + "The JSON content is assigned  to the `jsonString` attribute in the output tuple which must be "
            + "of type `rstring`.";

    @Parameter(optional = true, description = CA_DESC)
    public void setContentAttribute(
            TupleAttribute<Tuple, String> contentAttribute) {
        this.contentAttribute = contentAttribute;
    }

    @Override
    protected String acceptedContentTypes() {
        return ContentType.APPLICATION_JSON.getMimeType();
    }

    @Override
    protected int defaultContentAttributeIndex() {
        Attribute jsonString = getOutput(0).getStreamSchema().getAttribute(
                "jsonString");
        if (jsonString != null)
            return jsonString.getIndex();
        return super.defaultContentAttributeIndex();
    }

    @Override
    protected void submitContents(OutputTuple tuple, HttpEntity content)
            throws ParseException, IOException {
        String jsonString = EntityUtils.toString(content,
                StandardCharsets.UTF_8);
        tuple.setString(contentAttributeIndex, jsonString);
    }
}

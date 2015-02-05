/*
 * Copyright (C) 2014, International Business Machines Corporation
 * All Rights Reserved.
 */
package com.ibm.streamsx.inet.httpxml;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.InputSource;

import com.ibm.streams.operator.types.XML;

/**
 * Provide the value to update an attribute from an Xpath expression
 * executed against the XML content returned by the GET request.
 *
 */
class UpdateFromXPath extends HTTPGetXMLContent.UpdateParameter {
	
	private final XPathExpression xPathExpr;
	
	UpdateFromXPath(HTTPGetXMLContent owner, String expr) throws XPathExpressionException {
		
		owner.super();
		
        XPath xpath = XPathFactory.newInstance().newXPath();
        
        xPathExpr = xpath.compile(expr);
	}
	@Override
	String getValue(XML xml) throws Exception {
		
		return xPathExpr.evaluate(new InputSource(xml.getInputStream()));
	}
}

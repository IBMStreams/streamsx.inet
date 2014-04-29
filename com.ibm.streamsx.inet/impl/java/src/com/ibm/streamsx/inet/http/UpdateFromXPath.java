package com.ibm.streamsx.inet.http;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.ibm.streams.operator.types.XML;

class UpdateFromXPath extends HTTPGetXMLContent.UpdateParameter {
	
	private final XPathExpression xPathExpr;
	
	UpdateFromXPath(HTTPGetXMLContent owner, String expr) throws XPathExpressionException {
		
		owner.super();
		
        XPath xpath = XPathFactory.newInstance().newXPath();
        
        xPathExpr = xpath.compile(expr);
	}
	@Override
	boolean doUpdate(XML xml) throws Exception {
		
		final String value = xPathExpr.evaluate(xml.getStreamSource());
		if (value != null) {
			return true;
		}
		return false;
	}
}

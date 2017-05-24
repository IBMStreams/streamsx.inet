package com.ibm.streamsx.inet.rest.ops;

import com.ibm.streamsx.inet.rest.servlets.ReqWebMessage;

/**
* Licensed Materials - Property of IBM
* Copyright IBM Corp. 2014 
* @author - mags
 */
public interface ReqHandlerInterface {
	 void initiateRequestFromWeb(ReqWebMessage exchangeWebMessage) ;
}

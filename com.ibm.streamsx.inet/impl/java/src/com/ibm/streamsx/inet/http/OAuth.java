//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;

/**
 * Sign a request using oAuth1.0a
 * This class uses the Signpost library to sign the requests
 * Required properties are "consumerKey", "consumerSecret", "accessToken" and "accessTokenSecret"
 *
 */
class OAuth extends AAuthenticate {


	private OAuthConsumer cons = null;

	@Override
	public void init()  {
		cons = new CommonsHttpOAuthConsumer(getRequiredProperty("consumerKey"), getRequiredProperty("consumerSecret"));
		cons.setTokenWithSecret(getRequiredProperty("accessToken"), getRequiredProperty("accessTokenSecret"));
	}

	public void sign(HTTPRequest req) throws Exception {
		cons.sign(req.getReq());
	}
}

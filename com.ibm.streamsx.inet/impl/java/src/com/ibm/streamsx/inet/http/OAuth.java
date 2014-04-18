//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.net.HttpURLConnection;
import java.util.Properties;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;

//sign a request using oAuth
class OAuth extends AAuthenticate {


	private OAuthConsumer cons = null;

	@Override
	public void init()  {
		cons = new DefaultOAuthConsumer(getRequiredProperty("consumerKey"), getRequiredProperty("consumerSecret"));
		cons.setTokenWithSecret(getRequiredProperty("accessToken"), getRequiredProperty("accessTokenSecret"));
	}

	public HttpURLConnection sign(HTTPRequest req) throws Exception {
		
		HttpURLConnection connection = (HttpURLConnection)req.connect();
		cons.sign(connection);
		return connection;
	}
}

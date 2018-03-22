//
// *******************************************************************************
// * Copyright (C)2016, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import com.ibm.streams.operator.OperatorContext;

/**
 * Handles the HTTP client & authentication for the HTTPRequest operator.
 * TODO: All client support, just uses a default client atm.
 */
class HTTPRequestOperClient extends HTTPRequestOperAPI {
    
    private CloseableHttpClient client;
    
    public void initialize(OperatorContext context) throws Exception {
        super.initialize(context);
        
        //client = HttpClients.createDefault();
        HttpClientBuilder clientBuilder = HttpClients.custom();
        client = clientBuilder.build();
    }
    
    HttpClient getClient() {
        return client;
    }
}

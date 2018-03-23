//
// *******************************************************************************
// * Copyright (C)2016, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.File;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.logging.TraceLevel;

/**
 * Handles the HTTP client & authentication for the HTTPRequest operator.
 * TODO: All client support, just uses a default client atm.
 */
class HTTPRequestOperClient extends HTTPRequestOperAPI {
    
    private CloseableHttpClient client;
    
    public void initialize(OperatorContext context) throws Exception {
        super.initialize(context);
        
        SSLContext sslDefaultContext = SSLContext.getDefault();
        String[] sslDefaultProtocols = sslDefaultContext.getDefaultSSLParameters().getProtocols();

        if (tracer.isLoggable(TraceLevel.TRACE)) {
            String msg = "sslDefaultProtocols : ";
            for (int i=0; i<sslDefaultProtocols.length; i++) {
                Integer itg = new Integer(i);
                msg = msg + "sslDefaultProtocols[" + itg.toString() + "]=" + sslDefaultProtocols[i] + " ";
            }
            tracer.log(TraceLevel.TRACE, msg);
        }

        HttpClientBuilder clientBuilder = HttpClients.custom();

        //trust all certificates
        if (sslAcceptAllCertificates) {
            SSLContextBuilder sslContextBuilder = SSLContextBuilder.create();
            SSLContext sslContext = sslContextBuilder.build();
            
            sslContext.init(
                null,
                new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
                },
                new SecureRandom()
            );

            SSLConnectionSocketFactory sslcsf = new SSLConnectionSocketFactory(
                    sslContext,
                    sslDefaultProtocols,
                    null,
                    NoopHostnameVerifier.INSTANCE
            );
            
            clientBuilder.setSSLSocketFactory(sslcsf);
        }

        // Trust own CA and all self-signed certs
        if (sslTrustStoreFile != null) {
            //TODO: check this

            SSLContext sslcontext = SSLContexts.custom()
                .loadTrustMaterial(new File(sslTrustStoreFile), sslTrustStorePassword.toCharArray(), new TrustSelfSignedStrategy())
                .build();
            
            SSLConnectionSocketFactory sslcsf = new SSLConnectionSocketFactory(
                    sslcontext,
                    sslDefaultProtocols,
                    null,
                    SSLConnectionSocketFactory.getDefaultHostnameVerifier()
            );
            
            clientBuilder.setSSLSocketFactory(sslcsf);
        }
        client = clientBuilder.build();
        //client.getConnectionManager().getSchemeRegistry().
        //Dfauilt request config
        //RequestConfig defaultRequestConfig = RequestConfig.custom()
        //    .setConnectionRequestTimeout(15)
        //    .build();
        //client = HttpClients.createDefault();
        //clientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        //clientBuilder.setDefaultRequestConfig(defaultRequestConfig);
        //setDefaultConnectionConfig
        //setDefaultRequestConfig
    }
    
    HttpClient getClient() {
        return client;
    }
}

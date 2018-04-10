//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;

import com.ibm.streams.operator.logging.TraceLevel;

public class HTTPUtils {

	static final String CLASS_NAME="com.ibm.streamsx.inet.http.HTTPUtils";
	private static Logger trace = Logger.getLogger(CLASS_NAME);

	public static HttpURLConnection getNewConnection(String url) 
			throws IOException {
		return (HttpURLConnection)new URL(url).openConnection();
	}

	/**
	 * Reads all the data from the incoming stream and puts the contents in a string.
	 * @param stream
	 * @return
	 */
	public static String readFromStream(InputStream stream) {
		if(stream==null) return null;
		StringBuffer buf = new StringBuffer();
		BufferedReader in = new BufferedReader(new InputStreamReader(stream));
		String inputLine = null;
		try {
			while (((inputLine = in.readLine()) != null)) {
				buf.append(inputLine);
			}
		}catch(IOException e) {
			//ignore
		}
		return buf.toString();
	}

	public static Map<String, String> getHeaderMap(List<String> headers) {
		if(headers == null) return Collections.emptyMap();
		Map<String, String> headerMap = new HashMap<String, String>(headers.size());
		for(String header : headers) {
			String[] headerParts = header.split(":\\s*", 2);
			if(headerParts.length < 2) {
				trace.log(TraceLevel.ERROR, "No ':' found in extraHeaders element '" + header + "', skipping");
				continue;
			}
			String headerName = headerParts[0];
			String headerValue = headerParts[1];
			headerMap.put(headerName, headerValue);
		}
		return headerMap;
	}
	
	public static Map<String, String> getHeaderMapThrow(List<String> headers) {
		if(headers == null) return Collections.emptyMap();
		Map<String, String> headerMap = new HashMap<String, String>(headers.size());
		for(String header : headers) {
			String[] headerParts = header.split(":\\s*", 2);
			if(headerParts.length < 2) {
				trace.log(TraceLevel.ERROR, "No ':' found in extraHeaders element '" + header + "'");
				throw new IllegalArgumentException("No ':' found in extraHeaders element '" + header + "'");
			}
			String headerName = headerParts[0];
			String headerValue = headerParts[1];
			headerMap.put(headerName, headerValue);
		}
		return headerMap;
	}
	
	public static HttpClient getHttpClientWithNoSSLValidation() throws Exception {
		SSLContext sslContext = SSLContext.getInstance("SSL");
		sslContext.init(null, new TrustManager[] {
			new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}
				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			}
		}, new SecureRandom());

		SSLSocketFactory sf = new SSLSocketFactory(sslContext, new AllowAllHostnameVerifier());
		Scheme httpsScheme = new Scheme("https", 443, sf);
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(httpsScheme);

		ClientConnectionManager cm = new BasicClientConnectionManager(schemeRegistry);

		return new DefaultHttpClient(cm);
	}
	
	//returns an HttpClient that will only connect to servers that can be validated using the certificates in the keyStore
	//and where the client can identify itself using the keys in the keyStore
	//Note that will prevent the client from connecting to any server that cannot be validated using the keystore, even those that are in the default one
	public static HttpClient getHttpClientWithCustomSSlValidation(KeyStore keyStore, String password) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException{
		SSLContext sslContext = SSLContext.getInstance("SSL");
		
		KeyManagerFactory kmFactory = KeyManagerFactory.getInstance("IbmX509");
		kmFactory.init(keyStore, password.toCharArray());
		
		TrustManagerFactory tmFactory = TrustManagerFactory.getInstance("X509");
		tmFactory.init(keyStore);
		
		sslContext.init(kmFactory.getKeyManagers(), tmFactory.getTrustManagers(), new SecureRandom());
		
		SSLSocketFactory sf = new SSLSocketFactory(sslContext, new AllowAllHostnameVerifier());
		Scheme httpsScheme = new Scheme("https", 443, sf);
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(httpsScheme);

		ClientConnectionManager cm = new BasicClientConnectionManager(schemeRegistry);

		return new DefaultHttpClient(cm);
	}
	
	//returns an HttpClient where server certificates will not be checked but any private keys in the store
	//will be used as identification by the client
	public static HttpClient getHttpClientWithNoSSLServerValidation(KeyStore keyStore, String password) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, KeyManagementException {
		SSLContext sslContext = SSLContext.getInstance("SSL");
		
		KeyManagerFactory kmFactory = KeyManagerFactory.getInstance("IbmX509");
		kmFactory.init(keyStore, password.toCharArray());
		
		TrustManager[] DummyManager = new TrustManager[] {
			new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}
				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			}
		};
		
		sslContext.init(kmFactory.getKeyManagers(), DummyManager, new SecureRandom());
		
		SSLSocketFactory sf = new SSLSocketFactory(sslContext, new AllowAllHostnameVerifier());
		Scheme httpsScheme = new Scheme("https", 443, sf);
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(httpsScheme);

		ClientConnectionManager cm = new BasicClientConnectionManager(schemeRegistry);

		return new DefaultHttpClient(cm);
	}
}

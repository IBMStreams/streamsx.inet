//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

import com.ibm.streamsx.inet.http.HTTPRequest.RequestType;

class HTTPException extends Exception {

	private static final long serialVersionUID = 1L;

	private int responseCode = 0;
	private String data = "";
	public HTTPException(int responseCode, String message) {
		super(message);
		this.responseCode = responseCode;
	}
	public int getResponseCode() {
		return responseCode;
	}
	public void setData(String data) {
		this.data = data;
	}
	public String getData() {
		return data;
	}

	public String toString() {
		return "HttpException RC=" + responseCode + ", data= " + data; 
	}
}

class HTTPStreamReaderObj implements Runnable
{
	private BufferedReader in = null;
	private boolean shutdown = false;
	private HTTPRequest req = null;
	private IAuthenticate auth = null;
	private Map<String, String> postData = null;
	private HTTPStreamReader reader = null;
	
	public HTTPStreamReaderObj(String url, IAuthenticate auth, 
			HTTPStreamReader reader,  Map<String, String> postD, 
			boolean disableCompression) 
			throws Exception 	{
		this.auth = auth;
		this.reader = reader;
		this.postData = postD;
		req = new HTTPRequest(url);
		if(postData != null)
			req.setType(RequestType.POST);
		
		if(!disableCompression) {
			req.setHeader("Accept-Encoding", "gzip, deflate");
		}
		else {
			req.setHeader("Accept-Encoding", "identity");
		}
		req.setParams(postData);
	}
	

	public String getUrl() {
		return req.getUrl();
	}
	
	public HTTPResponse newConnection() throws Exception 	{
		HTTPResponse resp = req.sendRequest(auth);

		if(resp.getResponseCode() != HTTPResponse.HTTP_OK) {
			throw new HTTPException(resp.getResponseCode(), resp.getErrorStreamData());
		}
		return resp;
	}


	public void sendRequest() throws Exception 	{
		while(!shutdown) {
			try {
				sendSingleRequest();
				if(shutdown || !reader.connectionClosed())
					break;
			}catch(Exception e) {
				if(shutdown || !reader.onReadException(e))
					break;
			}
			finally {
				try {
					if(in!=null)
						in.close();
				}catch(Exception e) {}
				in = null;
			}
		}
	}

	public void sendSingleRequest() throws Exception {
		HTTPResponse resp = newConnection();
		in = new BufferedReader(new InputStreamReader(resp.getInputStream()));
		reader.connectionSuccess();
		String inputLine = null;
		while (!shutdown && ((inputLine = in.readLine()) != null)) {
			reader.processNewLine(inputLine);
		}
	}

	public void shutdown() throws Exception 	{
		shutdown = true;
		if(in != null){
			try {
				in.close();
			}catch(Exception e) {}
		}
	}
	
	@Override
	public void run() {
		try {
			sendRequest();
		}catch(Exception e) {
			System.exit(1);
		}
	}
}

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
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.eclipse.jetty.util.UrlEncoded;

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

public class HTTPStreamReaderObj implements Runnable
{
	private BufferedReader in = null;
	private boolean shutdown = false;
	private HTTPRequest req = null;
	private IAuthenticate auth = null;
	private String postData = null;
	private HTTPStreamReader reader = null;
	
	public HTTPStreamReaderObj(String url, IAuthenticate auth, HTTPStreamReader reader, String postD, boolean disableCompression) 
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
	}
	
	//for handling compressions
	static InputStream getInputStream(URLConnection conn) throws IOException {
		
		if(conn.getContentEncoding()!=null) { 
			if(conn.getContentEncoding().toLowerCase().indexOf("gzip") != -1)
				return new GZIPInputStream(conn.getInputStream());
			else if(conn.getContentEncoding().toLowerCase().indexOf("deflate") != -1)
				return new InflaterInputStream(conn.getInputStream(), new Inflater(true));
		}
		return conn.getInputStream();
	}


	public String getUrl() {
		return req.getUrl();
	}
	
	public URLConnection newConnection() throws Exception 	{
		HttpURLConnection streamconnection = auth.sign(req);
		if(postData != null) {
			OutputStreamWriter writer = new OutputStreamWriter(streamconnection.getOutputStream());
			writer.write(postData);
			writer.flush();
		}

		if(streamconnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
			HTTPException e = 
					new HTTPException(streamconnection.getResponseCode(), 
							readFromStream(streamconnection.getErrorStream()));
			try {
				e.setData(readFromStream(streamconnection.getInputStream()));
			} catch (Exception e1)  {
				//dont care
			}

			throw e;
		}
		return streamconnection;
	}


	public void sendRequest() throws Exception 	{
		while(!shutdown) {
			try {
				URLConnection urlConnection = newConnection();
				in = new BufferedReader(new InputStreamReader(getInputStream(urlConnection)));
				reader.connectionSuccess();
				String inputLine = null;
				while (!shutdown && ((inputLine = in.readLine()) != null)) {
					reader.processNewLine(inputLine);
				}
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

	static String readFromStream(InputStream is) throws IOException {
		if(is==null) return "null";
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String inputLine = null;
		StringBuffer sb = new StringBuffer();
		while (((inputLine = br.readLine()) != null)) {
			sb.append(inputLine);
		}
		return sb.toString();
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
			e.printStackTrace();
			System.exit(1);
		}
	}
}

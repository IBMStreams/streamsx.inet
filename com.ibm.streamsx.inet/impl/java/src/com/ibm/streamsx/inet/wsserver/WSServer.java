/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2013, 2013
# US Government Users Restricted Rights - Use, duplication or
# disclosure restricted by GSA ADP Schedule Contract with
# IBM Corp.
*/
package com.ibm.streamsx.inet.wsserver;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.apache.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.ibm.json.java.JSONObject;

/**
 * <P>Simple streams socket server - both receives and sends.<p> 
 * <p>This code acts as a WS server, applications connect up
 * and send to this server or receive messages from it. In the case that 
 * messages are received via WS the operator utilizing this is a source. 
 * In the case that the operator is acting as sink, the server is sending
 * messages out.</p>
 */
public class WSServer extends WebSocketServer {
	int count = 0;
	long totalSentCount = 0;
	private int ackCount = 0;
	Receive wsSource;
	Send wsSink;	

	public WSServer( int port ) throws UnknownHostException {
		super( new InetSocketAddress( port ) );
	}
	/**
	 * Our primary duty is to receive messages from WS and push them onto the stream as tuples.
	 * @param wsSource
	 */
	public void setWebSocketSource(Receive wsSource) {
		this.wsSource = wsSource;
	}
	/**
	 * Our primary duty is to receive tuples from the streams and send them to clients 
	 * that have connected to us.  
	 * @param wsSink
	 */
	public void setWebSocketSink(Send wsSink) {
		this.wsSink = wsSink;
	    Logger.getLogger(this.getClass()).trace("setWebSocketSink port : " + wsSink.getPort() );                    													
	}
	/**
	 * Do not do this unless you are going to be a client or a server.  
	 * @param address
	 */
	public WSServer( InetSocketAddress address ) {
		super( address );
	    Logger.getLogger(this.getClass()).trace("InetSocketAddress  : " + address.toString());                    											
	}

	public int getAckCount() {
		return ackCount;
	}
	public void setAckCount(int ackCount) {
		this.ackCount = ackCount;
	    Logger.getLogger(this.getClass()).trace("setAckCount()  : " + ackCount);                    									
	}

	@Override
	public void onOpen( WebSocket conn, ClientHandshake handshake ) {
		this.statusToAll( "OPEN",  "R:" + conn.getRemoteSocketAddress().getHostName() + " L:" + conn.getLocalSocketAddress().getHostName());				
	}

	@Override
	public void onClose( WebSocket conn, int code, String reason, boolean remote ) {
		this.statusToAll("CLOSE",  "R:" + conn.getRemoteSocketAddress().getHostName()  + " L:" + conn.getLocalSocketAddress().getHostName());				
	}

	@Override
	public void onMessage( WebSocket conn, String message ) {
		count++;
		// send the number of messages received to ALL the senders.
		if ((this.ackCount != 0) && (count % this.ackCount) == 0) {
			statusToAll("COUNT", String.format("%sd",count));
		}

		try {
			if (wsSource != null) {
				wsSource.produceTuples(message,conn.getRemoteSocketAddress().toString());
		        Logger.getLogger(this.getClass()).trace("WebSocket Source onMessage()" + conn.getRemoteSocketAddress().getAddress().getHostAddress() + "::" + message);                    				                    		        						
			} else {
		        Logger.getLogger(this.getClass()).trace("WebSocket as Sink onMessage()" + conn.getRemoteSocketAddress().getAddress().getHostAddress() + "::" + message);                    				                    		        						
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onError( WebSocket conn, Exception ex ) {
		ex.printStackTrace();
		if( conn != null ) {
			// some errors like port binding failed may not be assignable to a specific websocket
		}
	}

	/**
	 * Sends <var>text</var> to all currently connected WebSocket clients.
	 * 
	 * @param text
	 *            The String to send across the network.
	 * @throws InterruptedException
	 *             When socket related I/O errors occur.
	 * @return number of messages sent, thus the number of active connections.
	 */
	public int sendToAll( String text ) {
		Collection<WebSocket> con = connections();
        Logger.getLogger(this.getClass()).trace("sendToAll()::" + text);
		int cnt = 0;        
		synchronized ( con ) {
			for( WebSocket c : con ) {
		        Logger.getLogger(this.getClass()).trace("sendToAll()" + c.getRemoteSocketAddress().getAddress().getHostAddress() + "::" + cnt++ + " of " + con.size());                    				                    		        									
				c.send( text );
			}
		}
		totalSentCount += cnt;
		return cnt;
	}
	/**
	 * Get total number of messages sent.
	 * 
	 */
	public long getTotalSentCount() {
		return totalSentCount;
	}
	/**
	 * Send a status/control message to all, since we have control and data on the same 
	 * interface need a consistent way to send such a message.  
	 * @param status
	 * @param text
	 */
	private void statusToAll(String status, String text) {
		JSONObject controlMessage = new JSONObject();
		JSONObject controlBody = new JSONObject();
		controlBody.put("status", status);
		controlBody.put("text", text);
		controlMessage.put("control", controlBody);
        Logger.getLogger(this.getClass()).trace("statusToAll() : " + controlMessage.toString());                    				
		sendToAll(controlMessage.toString());
	}
	public long getClientCount() {
		return connections().size();
	}
}

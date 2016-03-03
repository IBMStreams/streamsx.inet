package com.ibm.streamsx.inet.wsserver;
/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2013, 2014
*/
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.apache.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.log4j.TraceLevel;

/**
 * <P>Simple streams socket server - both receives and sends.<p> 
 * <p>This code acts as a WS server, applications connect up
 * and send to this server or receive messages from it. In the case that 
 * messages are received via WS the operator utilizing this is a source. 
 * In the case that the operator is acting as sink, the server is sending
 * messages out.</p>
 */
public class WSServer extends WebSocketServer {
	static final String CLASS_NAME="com.ibm.streamsx.inet.wsserver";
	private static Logger trace = Logger.getLogger(CLASS_NAME);	
	
	int count = 0;
	long totalSentCount = 0;
	private int ackCount = 0;
	WebSocketInject wsSource;
	WebSocketSend wsSink;	

	public WSServer( int port ) throws UnknownHostException {
		super( new InetSocketAddress( port ) );
	}
	/**
	 * Our primary duty is to receive messages from WS and push them onto the stream as tuples.
	 * @param wsSource
	 */
	public void setWebSocketSource(WebSocketInject wsSource) {
		this.wsSource = wsSource;
	}
	/**
	 * Our primary duty is to receive tuples from the streams and send them to clients 
	 * that have connected to us.  
	 * @param wsSink
	 */
	public void setWebSocketSink(WebSocketSend wsSink) {
		this.wsSink = wsSink;
		trace.log(TraceLevel.INFO,"setWebSocketSink port : " + wsSink.getPort() );                    													
	}
	/**
	 * Do not do this unless you are going to be a client or a server.  
	 * @param address
	 */
	public WSServer( InetSocketAddress address ) {
		super( address );
		trace.log(TraceLevel.INFO,"InetSocketAddress  : " + address.toString());                    											
	}

	public int getAckCount() {
		return ackCount;
	}
	public void setAckCount(int ackCount) {
		this.ackCount = ackCount;
		trace.log(TraceLevel.INFO,"setAckCount()  : " + ackCount);                    									
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
			statusToAll("COUNT", String.format("%d",count));
		}

		try {
			if (wsSource != null) {
			  wsSource.produceTuples(message,conn.getRemoteSocketAddress().toString());
			  if (trace.isEnabledFor(TraceLevel.TRACE)) { trace.log(TraceLevel.TRACE, "onMessage() source-" + conn.getRemoteSocketAddress().getAddress().getHostAddress() + "::" + message); }
			} else {
              if (trace.isEnabledFor(TraceLevel.TRACE)) { trace.log(TraceLevel.TRACE, "onMessage() sink-" + conn.getRemoteSocketAddress().getAddress().getHostAddress() + "::" + message); }
			}
			
		} catch (Exception e) {
    		trace.log(TraceLevel.ERROR, "WSServer onMessage(): " + message + " err: " + e.getMessage() );
			//e.printStackTrace();
		}
	}

	@Override
	public void onError( WebSocket conn, Exception ex ) {
      trace.log(TraceLevel.ERROR, "WSServer onError(): client: " + ( conn!=null ? conn.getRemoteSocketAddress().toString() : "unknown" ) + " err: " + ex.getMessage() );
      //ex.printStackTrace();
      //if( conn != null ) {
      //	// some errors like port binding failed may not be assignable to a specific websocket
      //}
	}

	/**
	 * Sends <var>jsonMessage</var> to all currently connected WebSocket clients.
	 * 
	 * @param jsonMessage to transmit 
	 *            
	 * @throws InterruptedException
	 *             When socket related I/O errors occur.
	 * @return number of messages sent, thus the number of active connections.
	 */
	public int sendToAll( JSONObject jsonMessage ) {
		Collection<WebSocket> con = connections();
		String message = null;
		try {
			message = jsonMessage.serialize();
			if (trace.isEnabledFor(TraceLevel.TRACE)) { trace.log(TraceLevel.TRACE, "sendToAll() : " + message); }
		} catch (IOException e) {
    		trace.log(TraceLevel.ERROR, "WSServer sendToAll(): " + jsonMessage.toString() + " err: " + e.getMessage() );
		}
		int cnt = 0;        		
		if (message != null) {
			synchronized ( con ) {
				for( WebSocket c : con ) {
                  if (trace.isEnabledFor(TraceLevel.TRACE)) { trace.log(TraceLevel.TRACE, "sendToAll()" + c.getRemoteSocketAddress().getAddress().getHostAddress() + "::" + cnt++ + " of " + con.size()); }
					c.send( message );
				}
			}
			totalSentCount += cnt;
		}
		return cnt;
		
	}
	/**
	 * Get total number of messages sent: WSconnections * messages where WSConnections 
	 * varies over time. 
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
		controlBody.put("value", text);
		controlMessage.put("control", controlBody);
		sendToAll(controlMessage);

	}
	public long getClientCount() {
		return connections().size();
	}
}

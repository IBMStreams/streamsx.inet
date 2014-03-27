/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2013, 2013
# US Government Users Restricted Rights - Use, duplication or
# disclosure restricted by GSA ADP Schedule Contract with
# IBM Corp.
*/
package com.ibm.streamsx.inet.wsserver;
import org.apache.log4j.Logger;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.samples.patterns.TupleProducer;


/**
 * A source operator that does not receive any input streams and produces new tuples. 
 * The method <code>produceTuples</code> is called to begin submitting tuples.
 * <P>
 * For a source operator, the following event methods from the Operator interface can be called:
 * </p>
 * <ul>
 * <li><code>initialize()</code> to perform operator initialization</li>
 * <li>allPortsReady() notification indicates the operator's ports are ready to process and submit tuples</li> 
 * <li>shutdown() to shutdown the operator. A shutdown request may occur at any time, 
 * such as a request to stop a PE or cancel a job. 
 * Thus the shutdown() may occur while the operator is processing tuples, punctuation marks, 
 * or even during port ready notification.</li>
 * </ul>
 * <p>With the exception of operator initialization, all the other events may occur concurrently with each other, 
 * which lead to these methods being called concurrently by different threads.</p> 
 */

@PrimitiveOperator(name="Receive", namespace="com.ibm.streamsx.inet.wsserver",description=Receive.primDesc)
@OutputPorts({@OutputPortSet(description=Receive.outPortDesc, cardinality=1, optional=false, windowPunctuationOutputMode=WindowPunctuationOutputMode.Free)})
@Libraries("lib/java_websocket.jar")
public class Receive extends TupleProducer {
	final static String primDesc = 
			" This operator starts WebSocket server that receives messages via the WebSocket protocol." +
			" Each received message is output as tuple. The data received is dependent upon" + 
			" the input ports schema.";

	final static String outPortDesc = 
			"First attribute will have the message received via the WebSocket, this must be a rstring. " +
			"Second attribute (if provided) will have the senders unique id, this must be a rstring as well." +
            "Subsequent attribute(s) are allowed and will not be poplulated.";					
	final static String parmPortDesc = 
			"WebSocket network port that messages arrive on. The WebSocket client(s) " +
			"use this port to transmit on.";
	final static String parmAckDesc = 
			"The operator sends out an ack message to all connected clients. " +
			"An ack is transmitted after every *ackCount* messages are transmitted.";
	final static String messageAttrDesc = 
			"Input port's attribute that the data received will be stored to. " +
			"If the port has more than one attribute this parameter is required. ";
	final static String senderIdAttrDesc = 
			"Input port attribute that will we loaded with the message sender's identifier, this " +
			"identifier is consistent during the lifetime of the sender's session.";

	
    private WSServer wsServer;
    private int portNum;
    private int ackCount;
    private String messageAttrName = null;
    private String senderIdAttrName = null;
    
    private Metric nMessagesReceived;
    private Metric nClientsConnected;
    

    @CustomMetric(description="Number of messages received via WebSocket", kind=Kind.COUNTER)
    public void setnMessagesReceived(Metric nMessagesReceived) {
        this.nMessagesReceived = nMessagesReceived;
    }
    public Metric getnMessagesReceived() {
        return nMessagesReceived;
    }        

    @CustomMetric(description="Number of clients currently connected to WebSocket port.", kind=Kind.GAUGE)
    public void setnClientsConnected(Metric nClientsConnected) {
        this.nClientsConnected = nClientsConnected;
    }
    public Metric getnClientsConnected() {
        return nClientsConnected;
    }    
        
    @Parameter(name="messageAttribute", optional=true,description=messageAttrDesc)
	public void setMessageAttrName(String messageAttrName) {
    	this.messageAttrName = messageAttrName;
    }
	public String getMessageAttrName() {
		return this.messageAttrName;
	}    
    @Parameter(name="senderIdAttribute", optional=true,description=senderIdAttrDesc)
	public void setSenderIdAttrName(String senderIdAttrName) {
    	this.senderIdAttrName = senderIdAttrName;
    }
	public String getSenerIdAttrName() {
		return this.senderIdAttrName;
	}    

	
    
    
    @Parameter(name="ackCount", optional=true,description=parmAckDesc)
	public void setAckCount(int ackCount) {
    	this.ackCount = ackCount;
    }
	public int ackCount() {
		return this.ackCount;
	}    
    
	// Mandatory port     
    @Parameter(name="port", optional=false,description=parmPortDesc)
	public void setPort(int portNum) {
    	this.portNum = portNum;
    }
	public int getPort() {
		return this.portNum;
	}
	
    /**
     * Initialize this operator. Called once before any tuples are processed.
     * @param context OperatorContext for this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void initialize(OperatorContext context)
            throws Exception {
    	// Must call super.initialize(context) to correctly setup an operator.
        super.initialize(context);
        // This is important!!! you need this or the Stream is marked dead before 
        // any data is pushed down. 
        createAvoidCompletionThread();    
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        
        wsServer = new WSServer(portNum);
        wsServer.setAckCount(ackCount);
        wsServer.setWebSocketSource(this);
    }

    /**
     * Notification that initialization is complete and all input and output ports 
     * are connected and ready to receive and submit tuples.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void allPortsReady() throws Exception {
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("ContextName: " + context.getName() + "  all ports are ready in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        wsServer.start();        
    }

    @Override
    protected void startProcessing() throws Exception {
        Logger.getLogger(this.getClass()).trace("startProcessing" );    	
    }
    
    String attrIdTypeName = null, attrIdName = null;    
    String attrMsgTypeName = null, attrMsgName = null;            
    
    /**
     * Submit new tuples to the output stream
     * @throws Exception if an error occurs while submitting a tuple
     */
    public void produceTuples(String msg, String id) throws Exception  {
        final StreamingOutput<OutputTuple> out = getOutput(0);
        
        OutputTuple tuple = out.newTuple();
        // Set attributes in tuple
        Logger.getLogger(this.getClass()).trace("Receiving  :  " + msg ); 
        if (attrMsgName == null) { // only once 
        	if (out.getStreamSchema().getAttributeCount() == 1) { // port only 1 attribute        	
        		attrMsgTypeName = out.getStreamSchema().getAttribute(0).getType().getLanguageType();                
        		attrMsgName = out.getStreamSchema().getAttribute(0).getName();
        		if ((messageAttrName != null) && (messageAttrName != attrMsgName)) {
        			throw new IllegalArgumentException("Attribute '" + attrMsgName + "' expected, found messageAttribute value '" + messageAttrName +"' - invalid attribute name.");        			
        		}
        	} else {  // more than one attribute on port
        		if (messageAttrName == null) {
        			throw new IllegalArgumentException("MessageAttribute note specified, must be specified if port has more than 1 attribute.");        			        			
        		}
        		attrMsgName = messageAttrName;

        		attrIdName = senderIdAttrName;
        		if (attrIdName != null) {
        			if (out.getStreamSchema().getAttribute(attrIdName) == null) {        		
        			   throw new IllegalArgumentException("No such attribute named '" + attrIdName + "'.");        			        			        			
        			}
        			attrIdTypeName = out.getStreamSchema().getAttribute(attrIdName).getType().getLanguageType();
            		if (attrIdTypeName != "rstring") {
            			throw new IllegalArgumentException("Attibute '" + attrMsgName + "' type must be rstring, found '" + attrIdTypeName +"'.");
            		}        			
        		}
        	}
    		if (out.getStreamSchema().getAttribute(attrMsgName) == null) {
    			throw new IllegalArgumentException("No such attribute named '" + attrMsgName + "' found.");        			        			        			
    		}
    		attrMsgTypeName = out.getStreamSchema().getAttribute(attrMsgName).getType().getLanguageType();        	
        	if (attrMsgTypeName != "rstring") {
        		throw new IllegalArgumentException("Attibute '" + attrMsgName + "' type must be rstring, found '" + attrMsgTypeName +"'.");
        	}
        } // done with first time check 
        if (attrIdName != null) { 
        	tuple.setString(attrIdName, id);
            Logger.getLogger(this.getClass()).trace("Tuple Build Id Name:" + attrIdName + " Value:[" + id + "]");                            	
        }
        tuple.setString(attrMsgName, msg);
        Logger.getLogger(this.getClass()).trace("Tuple Build Msg Name:" + attrMsgName + " Value:[" + msg + "]");                            	        
        // Submit tuple to output stream
        try {
            Logger.getLogger(this.getClass()).trace("WebSocket-submit.  ");                    
        	out.submit(tuple);
            getnMessagesReceived().incrementValue(1L);
            getnClientsConnected().setValue(wsServer.getClientCount());
        } catch (Exception iae) {
        	iae.printStackTrace();
        	
        }
    }

    /**
     * Shutdown this operator, which will interrupt the thread
     * executing the <code>produceTuples()</code> method.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {
        super.shutdown();
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        wsServer.stop();


    }



    
}

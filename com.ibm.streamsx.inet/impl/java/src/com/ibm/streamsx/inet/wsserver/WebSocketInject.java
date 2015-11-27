/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2013, 2013
*/
package com.ibm.streamsx.inet.wsserver;
import org.apache.log4j.Logger;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Type;
import com.ibm.streams.operator.log4j.TraceLevel;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.model.Icons;
import com.ibm.streams.operator.samples.patterns.TupleProducer;

/* TODO - enable ability to specify the network device the operator listens on */

/**
 * A source type operator that receives websockets messages from multiple clients. 
 * Each message arriving on the WebSocket is converted to a tuple and injected into 
 * the stream.  
 * 
 * Optionally the tuple being injected can include and identifier of the sender,
 * the identifier is unique for the lifetime of the session. 
 *  
 */

@PrimitiveOperator(description=WebSocketInject.primDesc)
@OutputPorts({@OutputPortSet(description=WebSocketInject.outPortDesc, cardinality=1, optional=false, windowPunctuationOutputMode=WindowPunctuationOutputMode.Free)})
@Libraries("opt/wssupport/Java-WebSocket-1.3.0.jar")
@Icons(location32="icons/WebSocketInject_32.gif", location16="icons/WebSocketInject_16.gif")

public class WebSocketInject extends TupleProducer {
	final static String primDesc = 
			" Operator recieves messages from WebSocket clients and generates a tuple which is sent to streams. " +
			" Each received message is output as tuple. The data received is dependent upon" + 
			" the input ports schema.";

	final static String outPortDesc = 
			"First attribute will have the message received via the WebSocket, of type rstring. " +
			"Second attribute (if provided) will have the senders unique id, or type rstring." +
            "Subsequent attribute(s) are allowed and will not be poplulated.";					
	final static String parmPortDesc = 
			"WebSocket network port that messages arrive on. The WebSocket client(s) " +
			"use this port to transmit on.";
	final static String parmAckDesc = 
			"The operator sends out an ack message to all currently connected clients.  " +
			"An ack message is sent when the (totaslNumberOfMessagesRecieved % ackCount) == 0, " +
			"The ack message is a in JSON format " + "\\\\{" + " status:'COUNT', text:<totalNumberOfMessagesReceived>" + "\\\\}. " +
			"Default value is 0, no ack messages will be sent.";

	final static String messageAttrDesc = 
			"Input port's attribute that the data received will be stored to. " +
			"If the port has more than one attribute this parameter is required. ";
	final static String senderIdAttrDesc = 
			"Input port attribute that will we loaded with the message sender's identifier, this " +
			"identifier is consistent during the lifetime of the sender's session.";

	static final String CLASS_NAME="com.ibm.streamsx.inet.wsserver";
	private static Logger trace = Logger.getLogger(CLASS_NAME);

	
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
        trace.log(TraceLevel.INFO,"initalize():Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        
        wsServer = new WSServer(portNum);
        wsServer.setAckCount(ackCount);
        wsServer.setWebSocketSource(this);
    }

    @Override
    protected void startProcessing() throws Exception {    	
         OperatorContext context = getOperatorContext();
         trace.log(TraceLevel.INFO,"startProcessing():ContextName: " + context.getName() + "  all ports are ready in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
         wsServer.start();        
    	 
    }

    String attrIdName = null;    
    String attrMsgTypeName = null, attrMsgName = null;            
    
    /**
     * Submit new tuples to the output stream
     * @throws Exception if an error occurs while submitting a tuple
     * 
     */
    public void produceTuples(String msg, String id) throws Exception  {
        final StreamingOutput<OutputTuple> out = getOutput(0);
        OutputTuple tuple = out.newTuple();
        // Set attributes in tuple
        if (attrMsgName == null) { // only once  ]
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
        		attrIdName  = senderIdAttrName;
        		
        		if (attrIdName != null) {
        			if (out.getStreamSchema().getAttribute(attrIdName) == null) {        		
        			   throw new IllegalArgumentException("No such attribute named '" + attrIdName + "'.");        			        			        			
        			}
            		if (out.getStreamSchema().getAttribute(attrIdName).getType().getMetaType() != Type.MetaType.RSTRING) {
            			String typeString = out.getStreamSchema().getAttribute(attrIdName).getType().getLanguageType();             			
            			throw new IllegalArgumentException("Attribute '" + attrIdName + "' type must be 'rstring', found '" + typeString +"'.");
            		}        			
        		}
        	}
    		if (out.getStreamSchema().getAttribute(attrMsgName) == null) {
    			throw new IllegalArgumentException("No such attribute named '" + attrMsgName + "' found.");        			        			        			
    		}

    		if (out.getStreamSchema().getAttribute(attrMsgName).getType().getMetaType() != Type.MetaType.RSTRING) {
        		String typeString = out.getStreamSchema().getAttribute(attrMsgName).getType().getLanguageType();        	    			
        		throw new IllegalArgumentException("Attribute '" + attrMsgName + "' type must be 'rstring', found '" + typeString +"'.");
        	}
        } // done with first time check 
        if (attrIdName != null) { 
        	tuple.setString(attrIdName, id);
        	 trace.log(TraceLevel.INFO, "produceTuples(): idName:" + attrIdName + " Value:[" + id + "]");                            	
        }
        tuple.setString(attrMsgName, msg);
        if (trace.isEnabledFor(TraceLevel.TRACE)) { trace.log(TraceLevel.TRACE, "produceTuples(): ATTR:" + attrMsgName + " Value:[" + msg + "]"); }
        // Submit tuple to output stream
        try {
          if (trace.isEnabledFor(TraceLevel.TRACE)) { trace.log(TraceLevel.TRACE, "produceTuples():submit id: " + id + " len:" + msg.length());; }
	    out.submit(tuple);
            getnMessagesReceived().incrementValue(1L);
            getnClientsConnected().setValue(wsServer.getClientCount());
        } catch (Exception iae) {
    		trace.log(TraceLevel.ERROR, "Failed to submit tuple - msg:" + iae.getLocalizedMessage()); 
            throw(iae);
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
        trace.log(TraceLevel.INFO,
        		  "shutdown();Operator " +  context.getName() +
        		  " shutting down in PE: " + context.getPE().getPEId() + 
        		  " in Job: " + context.getPE().getJobId() );
        wsServer.stop();
    }



    
}

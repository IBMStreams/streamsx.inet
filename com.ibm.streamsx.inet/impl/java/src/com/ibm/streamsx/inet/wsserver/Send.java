package com.ibm.streamsx.inet.wsserver;
/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2013, 2013
# US Government Users Restricted Rights - Use, duplication or
# disclosure restricted by GSA ADP Schedule Contract with
# IBM Corp.
*/
import java.util.Iterator;
import java.util.Queue;

import org.apache.log4j.Logger;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.encoding.EncodingFactory;
import com.ibm.streams.operator.encoding.JSONEncoding;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.samples.patterns.TupleConsumer;

/**
 * Class for an operator that consumes tuples and does not produce an output stream. 
 * This pattern supports a number of input streams and no output streams. 
 * <P>
 * The following event methods from the Operator interface can be called:
 * </p>
 * <ul>
 * <li><code>initialize()</code> to perform operator initialization</li>
 * <li>allPortsReady() notification indicates the operator's ports are ready to process and submit tuples</li> 
 * <li>process() handles a tuple arriving on an input port 
 * <li>processPuncuation() handles a punctuation mark arriving on an input port 
 * <li>shutdown() to shutdown the operator. A shutdown request may occur at any time, 
 * such as a request to stop a PE or cancel a job. 
 * Thus the shutdown() may occur while the operator is processing tuples, punctuation marks, 
 * or even during port ready notification.</li>
 * </ul>
 * <p>With the exception of operator initialization, all the other events may occur concurrently with each other, 
 * which lead to these methods being called concurrently by different threads.</p> 
 */



@PrimitiveOperator(name="Send", namespace="com.ibm.streamsx.inet.wsserver",description=Send.primDesc)
@InputPorts({@InputPortSet(description=Send.parmPortDesc, cardinality=1, optional=false, windowingMode=WindowMode.NonWindowed)})
@Libraries("lib/java_websocket.jar")
public class Send extends TupleConsumer {

	final static String primDesc =
			"Upon startup, starts WebSocket Server. As tuple arrive on the input port they're converted into" +
			"JSON formatted messages " +
			"and transmitted to all currently connected clients. Clients can connect and disconnect at anytime.";

			

	final static String parmPortDesc =
			"Port that clients connect to and tuples formatted as JSON message are transmitted over.";

	
	private WSServer wsServer;
    private int portNum;
    private Metric nMessagesSent;
    private Metric nClientsConnected;

    

    @CustomMetric(description="Number of messages sent using WebSocket", kind=Kind.COUNTER)
    public void setnMessagesSent(Metric nPostRequests) {
        this.nMessagesSent = nPostRequests;
    }
    public Metric getnMessagesSent() {
        return nMessagesSent;
    }    

    @CustomMetric(description="Number of clients currently using WebSocket", kind=Kind.GAUGE)
    public void setnClientsConnected(Metric nClientsConnected) {
        this.nClientsConnected = nClientsConnected;
    }
    public Metric getnClientsConnected() {
        return nClientsConnected;
    }    
    
	private boolean active = false;

    
	// Mandatory port     
    @Parameter(name="port", optional=false, description=parmPortDesc)
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
		super.initialize(context);
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );

        // we will be batching....
        setBatchSize(getBatchSize());
        
        // Setup connection 
        // TODO what to do if you have error here
        wsServer = new WSServer(portNum);
        active = true;
        wsServer.setWebSocketSink(this);
	}

    /**
     * Notification that initialization is complete and all input and output ports 
     * are connected and ready to receive and submit tuples.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void allPortsReady() throws Exception {
    	// This method is commonly used by source operators. 
    	// Operators that process incoming tuples generally do not need this notification. 
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " all ports are ready in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        // Start the server, it will wait for new client connections - send tuples to all connected clients. 
        wsServer.start();
    }

    @Override
    protected final boolean processBatch(Queue<BatchedTuple> batch)
            throws Exception {

    	JSONEncoding jsonEncoding = EncodingFactory.getJSONEncoding();
    	
        int tuplesInRequest = 0;
        Logger.getLogger(this.getClass()).trace("processBatch() : batchSize:" + getBatchSize());
        JSONArray tuples = new JSONArray();
        for (Iterator<BatchedTuple> iter = batch.iterator(); iter.hasNext(); ) {

            if (tuplesInRequest++ == getBatchSize())
                break;

            BatchedTuple item = iter.next();
            iter.remove();
            // StreamSchema schema = item.getStream().getStreamSchema();
            JSONObject tuple = new JSONObject();
            tuple.put("tuple", jsonEncoding.encodeAsString(item.getTuple()));
            tuples.add(tuple);
        }
        JSONObject message = new JSONObject();
        message.put("tuples", tuples);
        Logger.getLogger(this.getClass()).trace("processBatch() : sending tuplesInRequest:" + tuplesInRequest);        
        int sentCount = wsServer.sendToAll(message.toString());
        getnClientsConnected().setValue(sentCount);
        getnMessagesSent().setValue(wsServer.getTotalSentCount());
        return true;
    }    

    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void shutdown() throws Exception {
        super.shutdown();    	
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        active = false;
        wsServer.stop();        
        // Must call super.shutdown()

    }

}


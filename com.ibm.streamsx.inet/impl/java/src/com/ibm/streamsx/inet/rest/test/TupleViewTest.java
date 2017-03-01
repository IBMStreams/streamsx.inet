/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
 */
package com.ibm.streamsx.inet.rest.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

import org.junit.Test;

import com.ibm.json.java.JSON;
import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONArtifact;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.flow.declare.InputPortDeclaration;
import com.ibm.streams.flow.declare.OperatorGraph;
import com.ibm.streams.flow.declare.OperatorGraphFactory;
import com.ibm.streams.flow.declare.OperatorInvocation;
import com.ibm.streams.flow.javaprimitives.JavaOperatorTester;
import com.ibm.streams.flow.javaprimitives.JavaTestableGraph;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.types.RString;
import com.ibm.streamsx.inet.rest.ops.ServletOperator;
import com.ibm.streamsx.inet.rest.ops.TupleView;

public class TupleViewTest {

    /**
     * Test an embedded jsonString attribute is converted
     * to JSON rather than a string that is serialized JSON.
     */
    @Test
    public void testJSONStringAttribute() throws Exception {
        OperatorGraph graph = OperatorGraphFactory.newGraph();

        OperatorInvocation<TupleView> op = graph.addOperator("TestJSONStringAttribute", TupleView.class);
        op.setIntParameter("port", 0);
        // Set the content to have a fixed URL
        op.setStringParameter("context", "TupleViewTest");
        op.setStringParameter("contextResourceBase", "/tmp"); // not actually serving any static content

        InputPortDeclaration tuplesToView = op
                .addInput("tuple<int32 a, rstring jsonString>");
        
        // Just need to see the last tuple in the HTTP request.
        tuplesToView.sliding().evictCount(1).triggerCount(1);

        // Create the testable version of the graph
        JavaTestableGraph testableGraph = new JavaOperatorTester()
                .executable(graph);

        // Create the injector to inject test tuples.
        StreamingOutput<OutputTuple> injector = testableGraph.getInputTester(tuplesToView);
        
        // Execute the initialization of operators within graph.
        testableGraph.initialize().get().allPortsReady().get();
        
        // Create a JSON object for the jsonString attribute.
        JSONObject json = new JSONObject();
        json.put("b", 37l);
        json.put("c", "HelloWorld!");
        
        // and submit to the operator
        injector.submitAsTuple(32, new RString(json.serialize()));
        
        URL url = new URL(getJettyURLBase(testableGraph, op) + "/TupleViewTest/TestJSONStringAttribute/ports/input/0/tuples");
        
        JSONArray tuples = getJSONTuples(url);
        assertEquals(1, tuples.size());
        
        JSONObject jtuple = (JSONObject) tuples.get(0);
        assertEquals(32l, jtuple.get("a")); // integral values always returned as long
        
        Object js = jtuple.get("jsonString");
        assertTrue(js instanceof JSONObject);
        
        JSONObject jso = (JSONObject) js;
        
        assertEquals(37l, jso.get("b"));
        assertEquals("HelloWorld!", jso.get("c"));

        testableGraph.shutdown().get();
    }
    /**
     * Test an rstring jsonString tuple is converted
     * to JSON rather than a string that is serialized JSON.
     */
    @Test
    public void testJSONStringTuple() throws Exception {
        OperatorGraph graph = OperatorGraphFactory.newGraph();

        OperatorInvocation<TupleView> op = graph.addOperator("TestJSONStringTuple", TupleView.class);
        op.setIntParameter("port", 0);
        // Set the content to have a fixed URL
        op.setStringParameter("context", "TupleViewTest");
        op.setStringParameter("contextResourceBase", "/tmp"); // not actually serving any static content

        InputPortDeclaration tuplesToView = op
                .addInput("tuple<rstring jsonString>");
        
        // Just need to see the last tuple in the HTTP request.
        tuplesToView.sliding().evictCount(2).triggerCount(1);

        // Create the testable version of the graph
        JavaTestableGraph testableGraph = new JavaOperatorTester()
                .executable(graph);

        // Create the injector to inject test tuples.
        StreamingOutput<OutputTuple> injector = testableGraph.getInputTester(tuplesToView);
        
        // Execute the initialization of operators within graph.
        testableGraph.initialize().get().allPortsReady().get();
        
        // Create a JSON object for the jsonString attribute.
        JSONObject json = new JSONObject();
        json.put("b", 93l);
        json.put("c", "Bonjour!");
        
        // and submit to the operator
        injector.submitAsTuple(new RString(json.serialize()));
        
        json.put("c", "Hola!");
        json.put("d", 423l);
        injector.submitAsTuple(new RString(json.serialize()));
               
        URL url = new URL(getJettyURLBase(testableGraph, op) + "/TupleViewTest/TestJSONStringTuple/ports/input/0/tuples");
        
        JSONArray tuples = getJSONTuples(url);
        assertEquals(2, tuples.size());
        
        JSONObject jtuple = (JSONObject) tuples.get(0);
        assertEquals(93l, jtuple.get("b"));
        assertEquals("Bonjour!", jtuple.get("c"));
        
        jtuple = (JSONObject) tuples.get(1);
        assertEquals(93l, jtuple.get("b"));
        assertEquals("Hola!", jtuple.get("c"));
        assertEquals(423l, jtuple.get("d"));


        testableGraph.shutdown().get();
    }    
    
    /**
     * Get the server port from the operator's metric.
     */
    public static int getJettyPort(JavaTestableGraph tg,  OperatorInvocation<? extends ServletOperator> op) {
        return (int) tg.getOperatorInstance(op).getServerPort().getValue();       
    }
    
    /**
     * Get the base part of the URL for a ServletOperator instance.
     */
    public static String getJettyURLBase(JavaTestableGraph tg,  OperatorInvocation<? extends ServletOperator> op) throws UnknownHostException {
        int port = getJettyPort(tg, op);
        return "http://" + InetAddress.getLocalHost().getHostName() + ":" + port;    
    }
    
    /**
     * Get the JSON array of tuples from a URL assumed to be HTTPTupleView (TupleView.class).
     */
    public static JSONArray getJSONTuples(URL url) throws IOException {
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(); 
        assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
        assertTrue(conn.getContentType().startsWith("application/json"));
        JSONArtifact jsonResponse = JSON.parse(new BufferedInputStream(conn.getInputStream(), 4096));
        conn.disconnect();
        assertTrue(jsonResponse instanceof JSONArray);
        return (JSONArray) jsonResponse;
    }
}

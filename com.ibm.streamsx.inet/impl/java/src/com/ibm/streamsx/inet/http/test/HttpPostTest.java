/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
 */
package com.ibm.streamsx.inet.http.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.ibm.json.java.JSON;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.flow.declare.InputPortDeclaration;
import com.ibm.streams.flow.declare.OperatorGraph;
import com.ibm.streams.flow.declare.OperatorGraphFactory;
import com.ibm.streams.flow.declare.OperatorInvocation;
import com.ibm.streams.flow.declare.OutputPortDeclaration;
import com.ibm.streams.flow.handlers.MostRecent;
import com.ibm.streams.flow.javaprimitives.JavaOperatorTester;
import com.ibm.streams.flow.javaprimitives.JavaTestableGraph;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.types.RString;
import com.ibm.streamsx.inet.http.HTTPPostOper;

public class HttpPostTest {

    /**
     * Test HTTPPost sends JSON as-is 
     * when content type is application/json
     * and the input schema is tuple<rstring jsonString>
     * the standard JSON type.
     */
    @Test
    public void testJSONInputAsis() throws Exception {
        OperatorGraph graph = OperatorGraphFactory.newGraph();

        OperatorInvocation<HTTPPostOper> op = graph.addOperator("TestJSONStringAttribute", HTTPPostOper.class);
        op.setStringParameter("headerContentType", "application/json");
        op.setStringParameter("url", "http://httpbin.org/post");

        InputPortDeclaration tuplesToPost = op
                .addInput("tuple<rstring jsonString>");
        
        OutputPortDeclaration postReturn = op.addOutput("tuple<rstring data, rstring errorMessage, int32 responseCode, int32 dataSize>");
        
        // Create the testable version of the graph
        JavaTestableGraph testableGraph = new JavaOperatorTester()
                .executable(graph);

        // Create the injector to inject test tuples.
        StreamingOutput<OutputTuple> injector = testableGraph.getInputTester(tuplesToPost);
        
        MostRecent<Tuple> mr = new MostRecent<>();
        
        testableGraph.registerStreamHandler(postReturn, mr);
        
        // Execute the initialization of operators within graph.
        testableGraph.initialize().get().allPortsReady().get();
        
        // Create a JSON object for the jsonString attribute.
        JSONObject json = new JSONObject();
        json.put("b", 37l);
        json.put("c", "HelloWorld!");
        
        // and submit to the operator
        injector.submitAsTuple(new RString(json.serialize()));
        
        String returnedData = mr.getMostRecentTuple().getString("data");
        JSONObject returnedJson = (JSONObject) JSON.parse(returnedData);
        assertEquals("application/json", ((JSONObject) returnedJson.get("headers")).get("Content-Type"));
        
        String jsonData = (String) returnedJson.get("data");
        assertEquals(json, JSON.parse(jsonData));

        testableGraph.shutdown().get();
    }
}

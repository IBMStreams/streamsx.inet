/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Random;

import org.junit.Test;

import com.ibm.json.java.JSONObject;
import com.ibm.streams.flow.declare.OperatorGraph;
import com.ibm.streams.flow.declare.OperatorGraphFactory;
import com.ibm.streams.flow.declare.OperatorInvocation;
import com.ibm.streams.flow.declare.OutputPortDeclaration;
import com.ibm.streams.flow.handlers.MostRecent;
import com.ibm.streams.flow.javaprimitives.JavaOperatorTester;
import com.ibm.streams.flow.javaprimitives.JavaTestableGraph;
import com.ibm.streams.operator.Tuple;
import com.ibm.streamsx.inet.rest.ops.PostJSON;

public class InjectJSONTest {
	
	@Test
	public void testGoodOnlyJSONSchemaFirstPort() throws Exception {
		OperatorGraph graph = OperatorGraphFactory.newGraph();
		OperatorInvocation<PostJSON> op = graph.addOperator(PostJSON.class);
		op.addOutput("tuple<rstring jsonString>");
		
		assertTrue(graph.compileChecks());
	}
	
	@Test
	public void testGoodJSONSchemaFirstPort() throws Exception {
		OperatorGraph graph = OperatorGraphFactory.newGraph();
		OperatorInvocation<PostJSON> op = graph.addOperator(PostJSON.class);
		op.addOutput("tuple<int32 a, rstring jsonString>");
		
		assertTrue(graph.compileChecks());
	}
	
	@Test
	public void testGoodStringSchemaFirstPort() throws Exception {
		OperatorGraph graph = OperatorGraphFactory.newGraph();
		OperatorInvocation<PostJSON> op = graph.addOperator(PostJSON.class);
		op.addOutput("tuple<rstring a, int32 b>");
		
		assertTrue(graph.compileChecks());
	}
	
	@Test
	public void testBadSchemaFirstPort() throws Exception {
		OperatorGraph graph = OperatorGraphFactory.newGraph();
		OperatorInvocation<PostJSON> op = graph.addOperator(PostJSON.class);
		op.addOutput("tuple<int32 a>");
		
		assertFalse(graph.compileChecks());
	}
	
	@Test
	public void testInjectSinglePort() throws Exception {	
		OperatorGraph graph = OperatorGraphFactory.newGraph();

		// Declare a HTTPJSONInjection operator
		OperatorInvocation<PostJSON> op = graph.addOperator(PostJSON.class);
		op.setIntParameter("port", 8081);
		
		OutputPortDeclaration injectedTuples = op.addOutput("tuple<rstring jsonString>");
		
		// Create the testable version of the graph
		JavaTestableGraph testableGraph = new JavaOperatorTester()
				.executable(graph);
		
		MostRecent<Tuple> mrt = new MostRecent<Tuple>();
		testableGraph.registerStreamHandler(injectedTuples, mrt);

		// Execute the initialization of operators within graph.
		testableGraph.initialize().get().allPortsReady().get();
		
		assertNull(mrt.getMostRecentTuple());
		
		URL postTuple = new URL("http://" + InetAddress.getLocalHost().getHostName() + ":8081/" + op.getName() + "/ports/output/0/inject");
		
		// Make an JSON POST request with an empty JSON object
		postJSONAndTest(postTuple, new JSONObject(), mrt);
		

		JSONObject json = new JSONObject();
		json.put("a", 37l); // JSON library always reads ints back as long values
		json.put("b", "Hello!");
		postJSONAndTest(postTuple, json, mrt);

		testableGraph.shutdown().get();
	}
	
	@Test
	public void testInjectTwoPorts() throws Exception {	
		OperatorGraph graph = OperatorGraphFactory.newGraph();

		// Declare a HTTPJSONInjection operator
		OperatorInvocation<PostJSON> op = graph.addOperator(PostJSON.class);
		op.setIntParameter("port", 8082);
		
		OutputPortDeclaration injectedTuples0 = op.addOutput("tuple<rstring jsonString>");
		OutputPortDeclaration injectedTuples1 = op.addOutput("tuple<rstring a>");
		
		// Create the testable version of the graph
		JavaTestableGraph testableGraph = new JavaOperatorTester()
				.executable(graph);
		
		MostRecent<Tuple> mrt0 = new MostRecent<Tuple>();
		testableGraph.registerStreamHandler(injectedTuples0, mrt0);
		
		MostRecent<Tuple> mrt1 = new MostRecent<Tuple>();
		testableGraph.registerStreamHandler(injectedTuples1, mrt1);

		// Execute the initialization of operators within graph.
		testableGraph.initialize().get().allPortsReady().get();
		
		URL postTuple0 = new URL("http://" + InetAddress.getLocalHost().getHostName() + ":8082/" + op.getName() + "/ports/output/0/inject");
		JSONObject json0 = new JSONObject();
		json0.put("a", 37l); // JSON library always reads ints back as long values
		json0.put("b", "Hello!");
		postJSONAndTest(postTuple0, json0, mrt0);
		assertNull(mrt1.getMostRecentTuple());
		
		URL postTuple1 = new URL("http://" + InetAddress.getLocalHost().getHostName() + ":8082/" + op.getName() + "/ports/output/1/inject");
		JSONObject json1 = new JSONObject();
		json1.put("a", 99l); // JSON library always reads ints back as long values
		json1.put("b", "Goodbye!");
		postJSONAndTest(postTuple1, json1, mrt1);
		assertNull(mrt0.getMostRecentTuple());


		testableGraph.shutdown().get();
	}
	
	@Test
	public void testBigInjectFails() throws Exception {	
		// Make an JSON POST request with an 800KB+ JSON object
		_testBigInject(800);
	}
	
	public void _testBigInject(int nk) throws Exception {
		OperatorGraph graph = OperatorGraphFactory.newGraph();

		// Declare a HTTPJSONInjection operator
		OperatorInvocation<PostJSON> op = graph.addOperator(PostJSON.class);
		op.setIntParameter("port", 0);
		
		OutputPortDeclaration injectedTuples = op.addOutput("tuple<rstring jsonString>");
		
		// Create the testable version of the graph
		JavaTestableGraph testableGraph = new JavaOperatorTester()
				.executable(graph);
		
		MostRecent<Tuple> mrt = new MostRecent<Tuple>();
		testableGraph.registerStreamHandler(injectedTuples, mrt);

		// Execute the initialization of operators within graph.
		testableGraph.initialize().get().allPortsReady().get();
		
		assertNull(mrt.getMostRecentTuple());
		
		URL postTuple = new URL(TupleViewTest.getJettyURLBase(testableGraph, op) + "/" + op.getName() + "/ports/output/0/inject");
		
		try {


			Random r = new Random();
			char[] chars = new char[nk * 1000];
			for (int i = 0; i < chars.length; i++) {
				chars[i] = (char) ('a' + (char) r.nextInt(26));
			}
			String s = new String(chars);
			JSONObject j = new JSONObject();
			j.put("bigString", s);
			postJSONAndTest(postTuple, j, mrt);
		} finally {

			testableGraph.shutdown().get();
		}
	}

	
	private static void postJSONAndTest(URL postTuple, JSONObject json, MostRecent<Tuple> mrt) throws IOException {
		byte[] dataBytes = json.serialize().getBytes("UTF-8");
		HttpURLConnection conn = (HttpURLConnection) postTuple.openConnection(); 
		conn.setDoOutput(true);
	    conn.setRequestProperty("Content-Type", "application/json");
	    conn.setRequestProperty("Content-Length", String.valueOf(dataBytes.length));
	    OutputStream out = conn.getOutputStream();
	    out.write(dataBytes);
	    out.flush();
	    out.close();	 
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, conn.getResponseCode());
		conn.disconnect();
		
		JSONObject tuple = JSONObject.parse(mrt.getMostRecentTuple().getString(0));
		assertEquals(json, tuple);
		
		mrt.clear();
	}
}

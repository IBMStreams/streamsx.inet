/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.junit.Test;

import com.ibm.streams.flow.declare.OperatorGraph;
import com.ibm.streams.flow.declare.OperatorGraphFactory;
import com.ibm.streams.flow.declare.OperatorInvocation;
import com.ibm.streams.flow.declare.OutputPortDeclaration;
import com.ibm.streams.flow.handlers.MostRecent;
import com.ibm.streams.flow.javaprimitives.JavaOperatorTester;
import com.ibm.streams.flow.javaprimitives.JavaTestableGraph;
import com.ibm.streams.operator.Tuple;
import com.ibm.streamsx.inet.rest.ops.PostTuple;

public class InjectFormTest {
	
	@Test
	public void testInjectSinglePort() throws Exception {	
		OperatorGraph graph = OperatorGraphFactory.newGraph();

		// Declare a beacon operator
		OperatorInvocation<PostTuple> op = graph.addOperator(PostTuple.class);
		op.setIntParameter("port", 0);
		
		OutputPortDeclaration injectedTuples = op.addOutput("tuple<int32 a, rstring b>");
		
		// Create the testable version of the graph
		JavaTestableGraph testableGraph = new JavaOperatorTester()
				.executable(graph);
		
		MostRecent<Tuple> mrt = new MostRecent<Tuple>();
		testableGraph.registerStreamHandler(injectedTuples, mrt);

		// Execute the initialization of operators within graph.
		testableGraph.initialize().get().allPortsReady().get();
		
		assertNull(mrt.getMostRecentTuple());
		
		// Make an empty POST request which submits a default tuple.
		URL postTuple = new URL(TupleViewTest.getJettyURLBase(testableGraph, op) + "/" + op.getName() + "/ports/output/0/inject");
		HttpURLConnection conn = (HttpURLConnection) postTuple.openConnection(); 
		conn.setDoOutput(true);
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, conn.getResponseCode());
		conn.disconnect();
		assertEquals(0, mrt.getMostRecentTuple().getInt(0));
		assertEquals("", mrt.getMostRecentTuple().getString(1));
		mrt.clear();
		
		// Just set one attribute
		String quote = "It's a beautiful thing, the destruction of words.";
		String data = "b=" + URLEncoder.encode(quote, "UTF-8");
		byte[] dataBytes = data.getBytes("UTF-8");
		conn = (HttpURLConnection) postTuple.openConnection(); 
		conn.setDoOutput(true);
	    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
	    conn.setRequestProperty("Content-Length", String.valueOf(dataBytes.length));
	    OutputStream out = conn.getOutputStream();
	    out.write(dataBytes);
	    out.flush();
	    out.close();	 
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, conn.getResponseCode());
		conn.disconnect();
		assertEquals(0, mrt.getMostRecentTuple().getInt(0));
		assertEquals(quote, mrt.getMostRecentTuple().getString(1));
		mrt.clear();
		
		// And now both attributes
		data = "a=73&b=" + URLEncoder.encode(quote, "UTF-8");
		dataBytes = data.getBytes("UTF-8");
		conn = (HttpURLConnection) postTuple.openConnection(); 
		conn.setDoOutput(true);
	    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
	    conn.setRequestProperty("Content-Length", String.valueOf(dataBytes.length));
	    out = conn.getOutputStream();
	    out.write(dataBytes);
	    out.flush();
	    out.close();	 
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, conn.getResponseCode());
		conn.disconnect();
		assertEquals(73, mrt.getMostRecentTuple().getInt(0));
		assertEquals(quote, mrt.getMostRecentTuple().getString(1));
		mrt.clear();
		
		// Verify a form exists
		URL form = new URL(postTuple.toExternalForm().replace("/inject", "/form"));
		HttpURLConnection connForm = (HttpURLConnection) form.openConnection(); 
		assertEquals(HttpURLConnection.HTTP_OK, connForm.getResponseCode());
		connForm.disconnect();

		// Verify a info servlet exists
		URL info = new URL(postTuple.toExternalForm().replace("/inject", "/info"));
		HttpURLConnection connInfo = (HttpURLConnection) info.openConnection(); 
		assertEquals(HttpURLConnection.HTTP_OK, connInfo.getResponseCode());
		connInfo.disconnect();

		testableGraph.shutdown().get();
	}
}

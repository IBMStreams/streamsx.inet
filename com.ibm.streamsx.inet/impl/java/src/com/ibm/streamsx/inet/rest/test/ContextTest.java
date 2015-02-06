/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

import org.junit.Test;

import com.ibm.streams.flow.declare.OperatorGraph;
import com.ibm.streams.flow.declare.OperatorGraphFactory;
import com.ibm.streams.flow.javaprimitives.JavaOperatorTester;
import com.ibm.streams.flow.javaprimitives.JavaTestableGraph;
import com.ibm.streamsx.inet.rest.ops.PostTuple;
import com.ibm.streamsx.inet.rest.ops.PostXML;
import com.ibm.streamsx.inet.rest.ops.TupleView;
import com.ibm.streamsx.inet.rest.ops.WebContext;

public class ContextTest {
	
	@Test
	public void testContext() throws Exception {
	
		File cf1 = File.createTempFile("webc", ".txt");
		File cf2 = File.createTempFile("webc", ".txt");
		cf2.delete();
		
		assertEquals(cf1.getParentFile(), cf2.getParentFile());

		OperatorGraph graph = OperatorGraphFactory.newGraph();

		// Declare a beacon operator
		graph.addOperator(WebContext.class).setStringParameter("context", "cwc").setStringParameter("contextResourceBase", cf1.getParent());
		graph.addOperator(TupleView.class).setStringParameter("context", "ctv").setStringParameter("contextResourceBase", cf1.getParent());
		graph.addOperator(PostTuple.class).setStringParameter("context", "cpt").setStringParameter("contextResourceBase", cf1.getParent());
		graph.addOperator(PostXML.class).setStringParameter("context", "cpx").setStringParameter("contextResourceBase", cf1.getParent());

		
		// Create the testable version of the graph
		JavaTestableGraph testableGraph = new JavaOperatorTester()
				.executable(graph);

		// Execute the initialization of operators within graph.
		testableGraph.initialize().get().allPortsReady().get();
		
		testContextURL("cwc", cf1, cf2);
		testContextURL("ctv", cf1, cf2);
		testContextURL("cpt", cf1, cf2);
		testContextURL("cpx", cf1, cf2);

		testableGraph.shutdown().get();
		cf1.delete();
	}
	
	private void testContextURL(String context, File cf1, File cf2) throws Exception {
		URL url1 = new URL("http://" + InetAddress.getLocalHost().getHostName() + ":8080/" + context + "/" + cf1.getName());
		URL url2 = new URL("http://" + InetAddress.getLocalHost().getHostName() + ":8080/" + context + "/" + cf2.getName());
		
		HttpURLConnection conn1 = (HttpURLConnection) url1.openConnection(); 
		assertEquals(HttpURLConnection.HTTP_OK, conn1.getResponseCode());
		conn1.disconnect();

		HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection(); 
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, conn2.getResponseCode());
		conn2.disconnect();
	}
}

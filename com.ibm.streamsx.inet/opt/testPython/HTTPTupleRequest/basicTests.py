import unittest

from streamsx.topology.topology import *
#from streamsx.topology.topology import Topology
from streamsx.topology.tester import Tester

from streamsx.topology.schema import *
import streamsx.topology.context
from streamsx import rest

import streamsx.spl.op as op
import streamsx.spl.toolkit as tk

import json
import requests
import time
import argparse



PORT = 8080
IP =  '172.16.49.167'
IP = '172.16.119.167'
PROTOCOL = "http://"

inetToolkit = "../../../../com.ibm.streamsx.inet"
#inetToolkit = "/home/streamsadmin/Development/streamsx.inet/com.ibm.streamsx.inet"


def upperString(tuple):
    tuple["response"] = tuple["request"].upper()
    return tuple

def tupleToJson(tuple):
    return json.dumps(tuple)

def webEntryLog(inTuple):
    print("webEntryLog:", inTuple, flush=True)
    return None


class TestSimpleFilter(unittest.TestCase):

    def setUp(self):
        Tester.setup_distributed(self)
        # Testing cannot workout when application to start the local_check()
        #   not enough api's to look into the application. Does not work yet.
        # Tester.setup_standalone(self)



    def test_filter(self):
        """basic validation of testing framework"""

        topology = Topology()
        s = topology.source([8, 7, 2, 4, 10])
        s = s.filter(lambda x: x> 5)

        # Create tester and assign contions
        tester = Tester(topology)
        tester.contents(s,[8,7,10])

        # submit the application for test
        #tester.test(self.test_ctxtype, self.test_config)
        tester.test(self.test_ctxtype, self.test_config)


        """ The diagram illustrates the flow, 

	TupleRequest: the HTTPTupleRequest operator where the inputPort 
        is sent to the web, the outputPort is injected to streams.

        Pending/PendingComplete : Makes the looping possible. 

                          		    -------------
                                     	   (     WWW   	 )
                                            -----+------\
						 ^   	 \
      +---------+      +--------+   +---------+  +-------++   +-------+	 +-------+
      | onRamp  +-->---+ Union  |   | Format  |	 |Tuple	  +->-+input  +->+ upper |
      |         |    +>|        +->-+ Response+>-+ Request|   | Filter|	 |  Case |
      +---------+   /  +--------+   +---------+	 +--------+   +-------+	 +---\---+
      +---------+  /  							 +----\---+
      | Pending +>+                                  			 |Pending |
      |-        |-------------<--------------------------<---------------|Continue|
      +---------+							 +--------+
      """

    def test_basic(self):
        topo = Topology("SmokePending")
        self.tester = Tester(topo)

        tk.add_toolkit(topo, inetToolkit)

        # Loop back not natural in a directed graph, need
        # to have place holder while the graph gets built,
        # At the end of the graph, connect back to the
        # begining. 
        pending_source = PendingStream(topo)

        # Directed graph has start this loop loop does not.
        # Need to give topology an "onRamp" so it can build the graph.
        rsp = pending_source.stream.map(lambda t : t)
        ss = topo.source([], name="onRamp")
        rsp = ss.union({rsp})
        # FormatResponse : 
        rspFormatted = rsp.map(lambda x : json.dumps(x) ).as_string();
        rawRequest = op.Map("com.ibm.streamsx.inet.rest::HTTPTupleRequest",
                            stream=rspFormatted,
                            schema='tuple<int64 key, rstring request, rstring method, rstring pathInfo >',
                            params={'port': PORT,
                                    'webTimeout':5.0,
                                    'responseJsonAttributeName':'string',
                                    'context':'/base'},
                            name = "TupleRequest")

        rawRequest.stream.sink(webEntryLog) ## log what we have received.

        # determine what to work on
        onlyTuple = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/Tuple", 
                                             name="inputFilter")
        # do the work
        upperDone = onlyTuple.transform(upperString, 
                                        name="upperCase")

        #
        self.tester.tuple_count(upperDone, 1)

        # loopback to sending
        pending_source.complete(upperDone)  # loopback

        ## All done building the graph......

        # setup the code that will invoke this test. 
        self.tester.local_check = self.basic_request

        # submit the application for test
        self.tester.test(self.test_ctxtype, self.test_config)

    def jobHealthy(self, count):
        """test to see if the application is ready to be tested
        """
        job = self.tester.submission_result.job
        for idx in range(count - 1):
            if (job.health == 'healthy'):
                return True
            print("health check fail : %d" % idx )
            time.sleep(1)
            job.refresh()
        self.assertEqual('healthy', job.health)
        return False


    def basic_request(self):
        """Test the application, this runs in the Python VM"""
        self.jobHealthy(4)
        testMessage = "THIS+is+a+test+MESSAGE"
        contentBase = '/base'
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/Tuple?' + testMessage
        print("REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertEqual(rsp.content, b"THIS+IS+A+TEST+MESSAGE")


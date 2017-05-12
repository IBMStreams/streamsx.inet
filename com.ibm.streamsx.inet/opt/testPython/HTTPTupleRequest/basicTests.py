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
IP = 'localhost'
PROTOCOL = "http://"

inetToolkit = "../../../../com.ibm.streamsx.inet"
#inetToolkit = "/home/streamsadmin/Development/streamsx.inet/com.ibm.streamsx.inet"


def upperString(tuple):
    tuple["response"] = tuple["request"].upper()
    return tuple

def lowerString(tuple):
    tuple["response"] = tuple["request"].lower()
    return tuple

def sleepMacbeth(tuple):
    """ He does -  """
    time.sleep(3);
    return True

def tupleToJson(tuple):
    return json.dumps(tuple)


def webEntryLog(inTuple):
    print("webEntryLog:", inTuple, flush=True)
    return None

class setResult():
    def __init__(self, text):
        self.preamble = text

    def __call__(self, tuple):
        tuple['response'] = self.preamble + tuple['request']

class reflectResult():
    def __init__(self, text):
        self.preamble = text

    def __call__(self, tuple):
        tuple['response'] = self.preamble + str(tuple)
        return tuple


class TestSimpleFilter(unittest.TestCase):

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


    def setUp(self):
        Tester.setup_distributed(self)
        # Testing cannot workout when application to start the local_check()
        #   not enough api's to look into the application. Does not work yet.
        # Tester.setup_standalone(self)



    def xtest_filter(self):
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


        """TEST: test_basic  validate that the operator can talk 

        The diagram illustrates the flow, 

	TupleRequest: the HTTPTupleRequest operator where the inputPort 
        is sent to the web, the outputPort is injected to streams.

        Pending/PendingComplete : Makes the looping possible. 

                                           -------------
                                          (     WWW   	)
                                           ------+------ 
                                             ^      \
 +---------+      +--------+   +---------+   +-------++   +-------+   +-------+
 | onRamp  +-->---+ Union  |   | Format  |   |Tuple   +->-+input  +->-+ upper |
 |         |    +>|        +->-+ Response+->-+ Request|   | Filter|   |  Case |
 +---------+   /  +--------+   +---------+   +--------+   +-------+   +--/----+
 +---------+  /  						 +------/--+
 | Pending +>+                                  		 |Pending |
 |-        |-------------<--------------------------<------------|Continue|
 +---------+							  +--------+

      """

    def xtest_basic(self):
        topo = Topology("Basic")
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



    def xtest_pathInfo(self):
        """TEST test_routine, validate that the pathInfo can be used for routing. 

        The context defines the base of incoming messages, beyond that is in 
        the pathInfo. If the context is '/base' and the request url is 
        http://node:port/base/fred/route/my/request, the pathInfo 
        will be /fred/route/my/request.

        """
        topo = Topology("PathInfo")
        self.tester = Tester(topo)
       
        tk.add_toolkit(topo, inetToolkit)

        pending_source = PendingStream(topo)

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
        onlyUpper = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/Upper", 
                                             name="upperFilter")
        onlyLower = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/Lower", 
                                             name="lowerFilter")
        onlyLong = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/L/O/N/G/L/O/W/E/R", 
                                             name="longFilter")
        onlyCount = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/Count", 
                                             name="countFilter")


        # do the work
        upperDone = onlyUpper.transform(upperString, 
                                        name="upperCase")
        lowerDone = onlyLower.transform(lowerString, 
                                        name="lowerCase")
        longDone = onlyLong.transform(lowerString, 
                                        name="longCase")
        longCount = onlyCount.transform(upperString, 
                                        name="countCase")

        # do tests on what we have processed.
        self.tester.tuple_count(lowerDone, 1)
        self.tester.tuple_count(upperDone, 1)
        self.tester.tuple_count(longDone, 1)
        self.tester.tuple_count(longCount, 10)


        # union 
        unionResult = upperDone.union({lowerDone, longDone, longCount})

        # loopback to sending
        pending_source.complete(unionResult)  # loopback


        ## All done building the graph......

        # setup the code that will invoke this test. 
        self.tester.local_check = self.pathInfo_request

        # submit the application for test
        self.tester.test(self.test_ctxtype, self.test_config)



    def pathInfo_request(self):
        """Test the application, this runs in the Python VM"""
        self.jobHealthy(4)
        testMessage = "THIS+is+a+test+MESSAGE"
        contentBase = '/base'
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/Upper?' + testMessage
        print("Upper REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertEqual(rsp.content, b"THIS+IS+A+TEST+MESSAGE")

        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/Lower?' + testMessage
        print("Long REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertEqual(rsp.content, b"this+is+a+test+message")

        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/L/O/N/G/L/O/W/E/R?' + testMessage
        print("LONGLOWER REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertEqual(rsp.content, b"this+is+a+test+message")

        for idx in range(10):
             self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/Count?' + testMessage
             print("Count[%d] REQ: %s" % (idx, self.url), flush=True)
             rsp = requests.get(url=self.url)
             print("Count[%d] RSP: %s\nSTATUS:%s\nCONTENT:%s" % (idx, rsp, rsp.status_code, rsp.content), flush=True)
             self.assertEqual(rsp.status_code, 200)
             self.assertEqual(rsp.content, b"THIS+IS+A+TEST+MESSAGE")




    def xtest_method(self):
        """TEST validate method. 

        The rest call can make a number of different invocation, get, post, put and delete
        that identify the method is applied to the reqeust. Test that we can handle
        the standard methods.

        """
        topo = Topology("Method")
        self.tester = Tester(topo)
       
        tk.add_toolkit(topo, inetToolkit)

        pending_source = PendingStream(topo)

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
                                    'context':'/MethodTest'},
                            name = "TupleRequest")

        rawRequest.stream.sink(webEntryLog) ## log what we have received.

        # determine what to work on
        onlyGet = rawRequest.stream.filter(lambda t : t["method"]=="get", 
                                             name="getFilter")
        onlyPut = rawRequest.stream.filter(lambda t : t["method"]=="PUT", 
                                             name="putFilter")
        onlyPost = rawRequest.stream.filter(lambda t : t["method"]=="POST", 
                                             name="postFilter")
        onlyDelete = rawRequest.stream.filter(lambda t : t["method"]=="DELETE", 
                                             name="deleteFilter")

        # do the work
        getDone = onlyGet.transform(setResult("GOTaGET:"), 
                                        name="getCase")
        putDone = onlyPut.transform(setResult("GOTaPUT:"), 
                                        name="putCase")
        postDone = onlyPost.transform(setResult("GOTaPOST"), 
                                        name="postCase")
        deleteDone = onlyDelete.transform(setResult("GOTaDELETE"), 
                                        name="deleteCase")

        # do tests on what we have processed.
        self.tester.tuple_count(getDone, 1)
        self.tester.tuple_count(putDone, 1)
        self.tester.tuple_count(postDone, 1)
        self.tester.tuple_count(deleteDone, 1)


        # union - put it all back together
        unionResult = getDone.union({putDone, postDone, deleteDone})

        # loopback to sending
        pending_source.complete(unionResult)  # loopback


        ## All done building the graph......

        # setup the code that will invoke this test. 
        self.tester.local_check = self.method_request

        # submit the application for test
        self.tester.test(self.test_ctxtype, self.test_config)



    def method_request(self):
        """Test the application, this runs in the Python VM"""
        self.jobHealthy(4)
        testMessage = "THIS+is+a+test+MESSAGE"
        contentBase = '/MethodTest'
        # get 
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/get?' + testMessage
        print("Methdo REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertTrue(rsp.content.startswith(b"GOTaGET"))
        # put
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/put?' + testMessage
        print("Long REQ:" + self.url, flush=True)
        rsp = requests.put(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertTrue(rsp.content.startswith(b"GOTaPUT"))
        # post
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/post?' + testMessage
        print("LONGLOWER REQ:" + self.url, flush=True)
        rsp = requests.post(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertTrue(rsp.content.startswith(b"GOTaPOST"))
        # delete
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/delete?' + testMessage
        print("LONGLOWER REQ:" + self.url, flush=True)
        rsp = requests.delete(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertTrue(rsp.content.startswith(b"GOTaDELETE"))


    def test_reflect(self):
        """Reflect request back to request, check results in Python VM. 

        Reflect the request back in order that we can check check in the Python VM.
        """
        topo = Topology("Reflect")
        self.tester = Tester(topo)
       
        tk.add_toolkit(topo, inetToolkit)

        pending_source = PendingStream(topo)

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
                                    'context':'/Reflect'},
                            name = "TupleRequest")

        rawRequest.stream.sink(webEntryLog) ## log what we have received.

        # wait for 
        lastAct = rawRequest.stream.filter(sleepMacbeth)

        # do the work
        getReflect = lastAct.transform(reflectResult("RAW:"), 
                                        name="reflectResult")

        # do tests on what we have processed.
        self.tester.tuple_count(getReflect, 1)

        # loopback to sending
        pending_source.complete(getReflect)  # loopback


        ## All done building the graph......

        # setup the code that will invoke this test. 
        self.tester.local_check = self.reflect_request

        # enable tracing info.
        job_config = streamsx.topology.context.JobConfig(job_name='Reflect', tracing="info")        
        job_config.add(self.test_config) 

        # submit the application for test
        self.tester.test(self.test_ctxtype, self.test_config)



    def reflect_request(self):
        """Test the application, this runs in the Python VM"""
        self.jobHealthy(4)
        testMessage = "THIS+is+a+test+MESSAGE"
        contentBase = '/Reflect'
        # do a reflection
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/get?' + testMessage
        print("Method REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertTrue(rsp.content.startswith(b"RAW:"))



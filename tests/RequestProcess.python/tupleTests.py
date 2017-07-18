import unittest

from streamsx.topology.topology import *
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

"""
Notes : 
Testing the tuple aspect of the operator. Python likes JSON it's not very comfortable
with Streams tuples. This is moving Streams Tuples out/in of the operator in the Python 
world.

The JSONToTuple operator does not process the header since it's map it 
not handled. I drops the fields on the way out, doing a .spl 
application with no JSONToTuple is ok.
"""

CONTEXTBASE = "base"
PORT = 8080
IP =  '172.16.49.167'
IP = 'localhost'
PROTOCOL = "http://"
inetToolkit = "../../com.ibm.streamsx.inet"



def upperString(tuple):
    tuple["response"] = tuple["request"].upper()
    return tuple

def addHeader(tuple):
    tuple["reader"] = {'head1':'value1', 'head2':'value2', 'head3':'value3'}
    return tuple

def lowerString(tuple):
    tuple["response"] = tuple["request"].lower()
    return tuple

def sleepMacbeth(tuple):
    """ He does -  """
    time.sleep(1);
    return True

def tupleToJson(tuple):
    return json.dumps(tuple)

def strToDict(str):
    t1 = str.decode('UTF-8')
    t2 = t1.replace("\'", '\"')
    return json.loads(t2)

def webEntryLog(inTuple):
    print("webEntryLog:", inTuple, flush=True)
    return None

def webLog(inTuple):
    print("webLog:", inTuple, flush=True)
    return None



class setResult():
    def __init__(self, text):
        self.preamble = text

    def __call__(self, tuple):
        tuple['response'] = self.preamble + tuple['request']

class reflect2Result():
    def __init__(self, text):
        self.preamble = text

    def __call__(self, tuple):
        tuple['response'] = self.preamble + str(tuple)
        return tuple


class TestTupleProcessing(unittest.TestCase):

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
        # Tester.setup_standalone(self)



        """TEST: test_basic  validate that the operator can talk 

        The diagram illustrates the flow, 

	RequestProcess: the HTTPRequestProcess operator where the inputPort 
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

    def test_basic(self):
        topo = Topology("TupleBasic")
        self.tester = Tester(topo)

        tk.add_toolkit(topo, inetToolkit)

        # Loop back not natural in a directed graph, need
        # to have place holder while the graph gets built,
        # At the end of the graph, connect back to the
        # begining. 
        pending_source = PendingStream(topo)

        # Within a topology a directed graph has a start, this loop does not. 
        # Create an 'onRamp' that creates and input (that does nothing) in order 
        # that a graph graph can be built. 

        rsp = pending_source.stream
        ss = topo.source([], name="onRamp")
        rsp = ss.union({rsp})

        # FormatResponse : 
        rspFormatted = rsp.map(lambda x : json.dumps(x) ).as_string();

        # Convert the tuple json object that we have been working with to 
        # 'tuple' that is native to Streams. 
        toTuple = op.Map("com.ibm.streamsx.json::JSONToTuple", 
                             stream=rspFormatted, 
                             schema='tuple<int64 key, rstring response>',
                             params = {'ignoreParsingError':True}, 
                             name = "JSONToTuple")

        # Get to the Streams. 
        rspFormatted = toTuple.stream     

        rawRequest = op.Map("com.ibm.streamsx.inet.rest::HTTPRequestProcess",
                            stream = rspFormatted,
                            schema ='tuple<int64 key, rstring request, rstring method, rstring pathInfo >',
                            params={'port': PORT,
                                    'webTimeout':5.0,
                                    'contextResourceBase':'opt/base',
                                    'context':CONTEXTBASE},
                            name = "HttpRequestProcess")

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
        
        self.test_config['topology.keepArtifacts']=True 

        # setup the code that will invoke this test. 
        self.tester.local_check = self.basic_client

        # enable tracing info.
        job_config = streamsx.topology.context.JobConfig(job_name='Tuple', tracing="trace")        
        job_config.add(self.test_config) 

        # submit the application for test
        self.tester.test(self.test_ctxtype, self.test_config)


    def basic_client(self):
        """Test the application, this runs in the Python VM"""
        self.jobHealthy(20)
        testMessage = "THIS+is+a+test+MESSAGE"
        time.sleep(1)
        contextBuilt = '/' + CONTEXTBASE + '/HttpRequestProcess/ports/analyze/0'
        self.url = PROTOCOL + IP + ':' + str(PORT) + contextBuilt + '/Tuple?' + testMessage
        print("REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertEqual(rsp.content, b"THIS+IS+A+TEST+MESSAGE")



    def test_reflect(self):
        topo = Topology("TupleReflect")
        self.tester = Tester(topo)

        tk.add_toolkit(topo, inetToolkit)

        # Looping
        pending_source = PendingStream(topo)

        # Build the onramp.
        rsp = pending_source.stream
        ss = topo.source([], name="onRamp")
        rsp = ss.union({rsp})

        # FormatResponse : 
        rspFormatted = rsp.map(lambda x : json.dumps(x) ).as_string();

        # Convert to 'tuple' that is native to Streams, stripping of everything: key, response.
        toTuple = op.Map("com.ibm.streamsx.json::JSONToTuple", 
                             stream=rspFormatted, 
                             schema='tuple<int64 key, rstring response>',
                             params = {'ignoreParsingError':True}, 
                             name = "JSONToTuple")

        # Get to the Streams. 
        rspFormatted = toTuple.stream     

        # Output : return  to web, 
        # Input :  get from web
        rawRequest = op.Map("com.ibm.streamsx.inet.rest::HTTPRequestProcess",
                            stream = rspFormatted,
                            #schema ='tuple<int64 key, rstring request, rstring method, rstring pathInfo >',
                            schema='tuple<int64 key, rstring request, rstring contentType, map<rstring, rstring> header, rstring response, rstring method,rstring pathInfo, int32 status, rstring statusMessage>',

                            params={'port': PORT,
                                    'webTimeout':5.0,
                                    'contextResourceBase':'opt/base',
                                    'context':CONTEXTBASE},
                            name = "HttpRequestProcess")

        rawRequest.stream.sink(webEntryLog) ## log what we have received.

        # determine what to work on
        onlyTuple = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/Tuple", 
                                             name="inputFilter")
        # do the work
        reflected = onlyTuple.transform(reflect2Result("REF:"), name="refectStream")
        #
        self.tester.tuple_count(reflected, 3)

        # loopback to sending
        pending_source.complete(reflected)  # loopback

        ## All done building the graph......

        ## Set parameters
        self.test_config['topology.keepArtifacts']=True 

        # setup the code that will invoke this test. 
        self.tester.local_check = self.reflect_client

        # enable tracing info.
        job_config = streamsx.topology.context.JobConfig(job_name='Tuple', tracing="trace")        
        job_config.add(self.test_config) 

        # submit the application for test
        self.tester.test(self.test_ctxtype, self.test_config)


    def reflect_client(self):
        """Test the application, this runs in the Python VM"""
        self.jobHealthy(20)
        testMessage = "THIS+is+a+test+MESSAGE"
        # Send a reqeust
        contextBuilt = '/' + CONTEXTBASE + '/HttpRequestProcess/ports/analyze/0'
        self.url = PROTOCOL + IP + ':' + str(PORT) + contextBuilt  + '/Tuple?' + testMessage
        print("REQ:" + self.url, flush=True)

        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)

        # convert response to dict that fields can be validated
        p0 = rsp.content
        p1 = p0.decode('utf-8')
        p2 = p1.replace("'", "\"")
        p3 = p2[4:]
        js = json.loads(p3)

        # start checking fields, do they exist
        self.assertIn('contentType', js)
        self.assertIn('pathInfo', js)
        self.assertEqual(js['pathInfo'], u"/Tuple")

        self.assertIn('request', js)
        self.assertIn('response', js)
        self.assertIn('header', js)
        # fields within the header....
        self.assertIn('Accept', js['header'])
        self.assertIn('Connection', js['header'])
        self.assertIn('Host', js['header'])

        # build/send a request - 
        self.url = PROTOCOL + IP + ':' + str(PORT) + contextBuilt  + '/Tuple?' + testMessage
        print("REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)

        # unpack
        p0 = rsp.content
        p1 = p0.decode('utf-8')
        p2 = p1.replace("'", "\"")
        p3 = p2[4:]
        js = json.loads(p3)

        # check
        self.assertIn('contentType', js)
        self.assertIn('pathInfo', js)

        self.assertIn('request', js)
        self.assertIn('response', js)
        self.assertIn('header', js)

        self.assertIn('User-Agent', js['header'])
        self.assertTrue(js['header']['User-Agent'].startswith("python"))

        # do they have the right value
        self.assertEqual(js['pathInfo'], u"/Tuple")


        # build/send 
        testHeaders = {'test1':'value1', 'test2':'value2', 'test3':'value3'}
        self.url = PROTOCOL + IP + ':' + str(PORT) + contextBuilt  + '/Tuple?' + testMessage
        print("REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url, headers=testHeaders)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        
        #unpack
        p0 = rsp.content
        p1 = p0.decode('utf-8')
        p2 = p1.replace("'", "\"")
        p3 = p2[4:]
        js = json.loads(p3)

        # check 
        self.assertIn('contentType', js)
        self.assertIn('pathInfo', js)

        self.assertIn('request', js)
        self.assertIn('response', js)
        self.assertIn('header', js)

        self.assertIn('User-Agent', js['header'])
        self.assertTrue(js['header']['User-Agent'].startswith("python"))

        # check if header. 
        self.assertIn('test1', js['header'])
        self.assertEqual('value1', js['header']['test1'])
        self.assertIn('test2', js['header'])
        self.assertEqual('value2', js['header']['test2'])
        self.assertIn('test3', js['header'])
        self.assertEqual('value3', js['header']['test3'])

        # do they have the right value
        self.assertEqual(js['pathInfo'], u"/Tuple")


        
        




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
inetToolkit = "../../com.ibm.streamsx.inet"


def upperString(tuple):
    tuple["response"] = tuple["request"].upper()
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
    print("strToDict:" + t1)
    t2 = t1.replace("\'", '\"')
    return json.loads(t2)

def webEntryLog(inTuple):
    print("webEntryLog:", inTuple, flush=True)
    return None

def webExitLog(inTuple):
    print("webExitLog:", inTuple, flush=True)
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
        """ removes the Content-Length """
        del tuple['Content-Length']
        tuple['response'] = self.preamble + str(tuple)
        return tuple

class buildHeaderResponse():
    """ Generate a response
    
    """
    def __init__(self, text):
        self.preamble = text

    def __call__(self, tuple):
        tuple['response']  = self.preamble + str(tuple['header'])
        return tuple



class buildResponse():
    """ Generate a response
    
    """
    def __init__(self, text):
        self.preamble = text

    def __call__(self, tuple):
        """get the request and get rid of the '&' which may be messing up the resonse message"""
        del tuple['header']['Content-Length']  # Do NOT include length in 
        params = tuple['request'].split('&')
        count = 10
        for param in params:
            namVal = param.split('=')
            if (namVal[0] == "REPEAT"):
                count = int(namVal[1])
                break
        fillString = "-" * count
        tuple['response']  = self.preamble + fillString + tuple['request'].replace("&", '*')
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
        # Standalone does ot work yet....
        # Testing cannot work out when application to start the local_check(),
        # not enough api's to look into the application. 
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

	RequestProcess: the HTTPRequestProcess operator where the inputPort 
        is sent to the web, the outputPort is injected to streams.

        Pending/PendingComplete : Makes the looping possible. 

                                           -------------
                                          (     WWW   	)
                                           ------+------ 
                                             ^      \
 +---------+      +--------+   +---------+   +-------++   +-------+   +-------+
 | onRamp  +-->---+ Union  |   | Format  |   |Request +->-+input  +->-+ upper |
 |         |    +>|        +->-+ Response+->-+ Process|   | Filter|   |  Case |
 +---------+   /  +--------+   +---------+   +--------+   +-------+   +--/----+
 +---------+  /  						 +------/--+
 | Pending +>+                                  		 |Pending |
 |-        |-------------<--------------------------<------------|Continue|
 +---------+							  +--------+

      """

    def testx_basic(self):
        topo = Topology("header")
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
        rawRequest = op.Map("com.ibm.streamsx.inet.rest::HTTPRequestProcess",
                            stream=rspFormatted,
                            schema='tuple<int64 key, rstring request, rstring method, rstring pathInfo >',
                            params={'port': PORT,'webTimeout':5.0,'responseJsonAttributeName':'string','context':'base', 'contextResourceBase':'opt/base'},
                            name = "RequestProcess")

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
        contentBase = '/base/RequestProcess/ports/analyze/0'
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/Tuple?' + testMessage
        print("REQ:" + self.url, flush=True)
        rsp = requests.get(url=self.url)
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.content), flush=True)
        self.assertEqual(rsp.status_code, 200)
        self.assertEqual(rsp.content, b"THIS+IS+A+TEST+MESSAGE")




    def reflectHeaders(self, expected_requests, local_check_function ):
        """Reflect base : reflecting back post. 

        Ran into a problem with reflecting back the header, if you include a Content-Length
        in the header a corrected Content-Length is *not* sent. When the data arrives
        back at the client the data is too short - thus the buildRsp()
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
        rawRequest = op.Map("com.ibm.streamsx.inet.rest::HTTPRequestProcess",
                            stream=rspFormatted,
                            schema='tuple<int64 key, rstring request, rstring contentType, map<rstring, rstring> header, rstring response, rstring method,rstring pathInfo, int32 status, rstring statusMessage>',
                            params={'port': PORT,
                                    'webTimeout':5.0,
                                    'responseJsonAttributeName':'string',
                                    'context':'Reflect',
                                    'contextResourceBase': 'opt/Reflect'},
                            name = "RequestProcess")

        rawRequest.stream.sink(webEntryLog) ## log what we have received.

        # wait for 
        lastAct = rawRequest.stream.filter(sleepMacbeth)
        # do the work
        getReflect = rawRequest.stream.transform(buildHeaderResponse("HED:"), 
                                        name="buildHeaderResponse")
        getReflect.sink(webExitLog) ## log what we are sending back
        # do tests on what we have processed.
        self.tester.tuple_count(getReflect, expected_requests)
        # loopback to sending
        pending_source.complete(getReflect)  # loopback

        ## All done building the graph......

        # setup the code that will invoke this test. 
        self.tester.local_check = local_check_function

        # enable tracing info.
        job_config = streamsx.topology.context.JobConfig(job_name='Reflect', tracing="info")        
        job_config.add(self.test_config) 

        # submit the application for test
        self.tester.test(self.test_ctxtype, self.test_config)





    def test_getHeaders(self):
        """ Send headers to the streams make sure they get reflected back. 
           
        """
        self.reflectHeaders(expected_requests=1, local_check_function=self.header_getHeaders)



    def header_getHeaders(self):
        """Simple get headers

        """
        self.jobHealthy(4)
        contentBase = '/Reflect/RequestProcess/ports/analyze/0'
        # request : 
        headerTest = 3
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/get?' + "this+is+a+test"
        print("Method REQ:" + self.url, flush=True)
        payload = {}
        reqHeaders = {}
        for idx in range(headerTest):
            reqHeaders['header' + str(idx)] = 'hValue'  + str(idx)

        rsp = requests.get(url=self.url, headers=reqHeaders)

        # response
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.text), flush=True)
        print("RSP::%s" % (rsp.text), flush=True)
        self.assertEqual(rsp.status_code, 200, "incorrect completion code")
        self.assertTrue(rsp.content.startswith(b"HED:"), msg="preamble missing - data loss")
        
        for idx in range(headerTest):
            head = 'header' + str(idx)
            value = 'hValue' + str(idx)
            self.assertTrue(head in rsp.content.decode('utf8'), msg="failed to find:" + head)
            self.assertTrue(value in rsp.content.decode('utf8'), msg="failed to find:"+ value)



    def reflectPost(self, expected_requests, local_check_function ):
        """Reflect base : reflecting back post. 

        Ran into a problem with reflecting back the header, if you include a Content-Length
        in the header a corrected Content-Length is *not* sent. When the data arrives
        back at the client the data is too short - thus the buildRsp()
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
        rawRequest = op.Map("com.ibm.streamsx.inet.rest::HTTPRequestProcess",
                            stream=rspFormatted,
                            schema='tuple<int64 key, rstring request, rstring contentType, map<rstring, rstring> header, rstring response, rstring method,rstring pathInfo, int32 status, rstring statusMessage>',
                            params={'port': PORT,
                                    'webTimeout':5.0,
                                    'responseJsonAttributeName':'string',
                                    'context':'Reflect',
                                    'contextResourceBase': 'opt/Reflect'},
                            name = "RequestProcess")

        rawRequest.stream.sink(webEntryLog) ## log what we have received.

        # wait for 
        lastAct = rawRequest.stream.filter(sleepMacbeth)
        # do the work
        getReflect = rawRequest.stream.transform(buildResponse("RSP:"), 
                                        name="buildResponse")
        getReflect.sink(webExitLog) ## log what we are sending back
        # do tests on what we have processed.
        self.tester.tuple_count(getReflect, expected_requests)
        # loopback to sending
        pending_source.complete(getReflect)  # loopback

        ## All done building the graph......

        # setup the code that will invoke this test. 
        self.tester.local_check = local_check_function

        # enable tracing info.
        job_config = streamsx.topology.context.JobConfig(job_name='Reflect', tracing="info")        
        job_config.add(self.test_config) 

        # submit the application for test
        self.tester.test(self.test_ctxtype, self.test_config)


    def test_postForm(self):
        """ Test the reflect facility. 
           
        """
        self.reflectPost(expected_requests=1, local_check_function=self.header_postForm)

    def header_postForm(self):
        """More complicated posts with a headers, this is how forms with name/values are sent.

        """
        self.jobHealthy(4)
        contentBase = '/Reflect/RequestProcess/ports/analyze/0'
        # request : 
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/post?'
        print("Method REQ:" + self.url, flush=True)
        fillCount = 100
        payload = {"REPEAT":str(fillCount)}
        for idx in range(3):
            payload['payload' + str(idx)] = 'pValue'  + str(idx)
        reqHeaders = {}
        for idx in range(3):
            reqHeaders['header' + str(idx)] = 'hValue'  + str(idx)

        rsp = requests.post(url=self.url, headers=reqHeaders, data=payload)

        # response
        print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.text), flush=True)
        print("RSP::%s" % (rsp.text), flush=True)
        self.assertEqual(rsp.status_code, 200, "incorrect completion code")
        self.assertTrue(rsp.content.startswith(b"RSP:"), msg="preamble missing - data loss")
        self.assertGreater(len(rsp.text), fillCount, msg="under fill count - data loss" )


        



    



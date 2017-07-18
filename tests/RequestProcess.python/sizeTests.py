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
    """ Before Duncan's visit....  """
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



class TestSize(unittest.TestCase):

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
        # This is how todo standalone, it does not work, yet??
        # Tester.setup_standalone(self)



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




    def reflectPost(self, expected_requests, local_check_function ):
        """Reflect base : Reflect back the data that is send as well as sending amount 
           specified in the repeat paramter. 

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

        """
        Various length responses.
        """
    def test_postResp100(self):
        print("doing the postForm")
        self.tstCount = 100
        self.reflectPost(expected_requests=1, local_check_function=self.header_postRsp)


    def test_postResp1000(self):
        print("doing the postForm")
        self.tstCount = 1000
        self.reflectPost(expected_requests=1, local_check_function=self.header_postRsp)

    def test_postResp10000(self):
        self.tstCount = 10000
        self.reflectPost(expected_requests=1, local_check_function=self.header_postRsp)

    def test_postResp100000(self):
        self.tstCount = 100000
        self.reflectPost(expected_requests=1, local_check_function=self.header_postRsp)

    def test_postReq(self):
        self.tstCount = 100
        self.reflectPost(expected_requests=1, local_check_function=self.header_postReq)

    def test_postReq1000(self):
        self.tstCount = 1000
        self.reflectPost(expected_requests=1, local_check_function=self.header_postReq)

    def test_postReq10000(self):
        self.tstCount = 10000
        self.reflectPost(expected_requests=1, local_check_function=self.header_postReq)

    def test_postReq10000(self):
        self.tstCount = 10000
        self.reflectPost(expected_requests=1, local_check_function=self.header_postReq)




    def header_postRsp(self):
        """More complicated posts with a headers, this is how forms with name/values are sent.

        """
        self.jobHealthy(4)
        contentBase = '/Reflect/RequestProcess/ports/analyze/0'
        # request : 
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/post?'
        payload = {"REPEAT":str(self.tstCount)}
        print("** REQ: %s  Payload len:%d" % (self.url, len(payload)),  flush=True)
        rsp = requests.post(url=self.url, data=payload)

        # response
        #print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.text), flush=True)
        #print("RSP::%s" % (rsp.text), flush=True)
        self.assertEqual(rsp.status_code, 200, "incorrect completion code")
        self.assertTrue(rsp.content.startswith(b"RSP:"), msg="preamble missing - data loss")
        print("** RSP: expected: %d received: %d" % (self.tstCount, len(rsp.text)))
        self.assertGreater(len(rsp.text), self.tstCount, msg="under fill count - data loss" )


    def header_postReq(self):
        self.jobHealthy(4)
        contentBase = '/Reflect/RequestProcess/ports/analyze/0'
        # request : 
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/post'
        payData = "x" * self.tstCount 
        payload = {"DATA": payData}
        print("** REQ: %s  Payload len:%d" % (self.url, len(payData)),  flush=True)
        rsp = requests.post(url=self.url, data=payload)

        # response
        #print("RSP: %s\nSTATUS:%s\nCONTENT:%s" % (rsp, rsp.status_code, rsp.text), flush=True)
        #print("RSP::%s" % (rsp.text), flush=True)
        self.assertEqual(rsp.status_code, 200, "incorrect completion code")
        self.assertTrue(rsp.content.startswith(b"RSP:"), msg="preamble missing - data loss")
        print("** RSP: expected: %d received: %d" % (self.tstCount, len(rsp.text)))
        self.assertGreater(len(rsp.text), self.tstCount, msg="under fill count - data loss" )



        



    



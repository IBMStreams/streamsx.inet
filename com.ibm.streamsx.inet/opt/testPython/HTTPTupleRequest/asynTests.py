import unittest

from streamsx.topology.topology import *
from streamsx.topology.tester import Tester

from streamsx.topology.schema import *
import streamsx.topology.context
from streamsx import rest

import streamsx.spl.op as op
import streamsx.spl.toolkit as tk

import datetime
import json
import requests
import time
import argparse



PORT = 8080
IP =  '172.16.49.167'
IP = 'localhost'
PROTOCOL = "http://"
inetToolkit = "../../../../com.ibm.streamsx.inet"


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



class sleepResponse():
    """ Generate a response
    
    """
    def __init__(self, text):
        self.preamble = text

    def __call__(self, tuple):
        """get the request and get rid of the '&' which may be messing up the resonse message"""
        del tuple['header']['Content-Length']  # Do NOT include length in 
        print("*** ENTERED sleepResponse ___call___ ")
        params = tuple['request'].split('&')
        slp = 1
        slpStr = "Did not find 'SLEEP'"
        for param in params:
            namVal = param.split('=')
            if (namVal[0] == "SLEEP"):
                slpStr = namVal[1]
                slp = int(namVal[1])
                break
        startTime = datetime.datetime.utcnow();
        time.sleep(slp)
        endTime = datetime.datetime.utcnow();        
        tuple['response']  = self.preamble + slpStr + "[" + " processed sleep " +  "] KEY:" + str(tuple['key'])
        return tuple



class TestAsync(unittest.TestCase):

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



    def reflectPost(self, expected_requests, local_check_function ):
        """
        Reflect back after a set number of seconds. 
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
                                    'webTimeout':10.0,
                                    'responseJsonAttributeName':'string',
                                    'context':'Reflect',
                                    'contextResourceBase': 'opt/Reflect'},
                            name = "RequestProcess")

        rawRequest.stream.sink(webEntryLog) ## log what we have received.

        # wait for 
        lastAct = rawRequest.stream.filter(sleepMacbeth)
        # do the work
        getReflect = rawRequest.stream.transform(sleepResponse("SLP:"), 
                                        name="sleepResponse")
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
    def test_multiRequests(self):
        print("doing the postForm")
        self.slpCount = 4
        self.reflectPost(expected_requests=11, local_check_function=self.asyn_multiResponse)


    def asyn_multiResponse(self):
        """More complicated posts with a headers, this is how forms with name/values are sent.

        """
        self.jobHealthy(4)
        contentBase = '/Reflect/RequestProcess/ports/analyze/0'
        # request : 1
        self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/post?'
        payload = {"SLEEP":str(self.slpCount)}
        print("** REQ: %s  Payload len/data:%d/%s" % (self.url, len(payload), payload),  flush=True)
        rsp = requests.post(url=self.url, data=payload)

        # response
        self.assertEqual(rsp.status_code, 200, "incorrect completion code")
        print("** RSP: text:%s" % (rsp.text))
        self.assertTrue(rsp.content.startswith(b"SLP:"), msg="preamble missing - data loss")
        self.assertGreater(len(rsp.text), self.slpCount, msg="under fill count - data loss" )


        # request : 2-11
        for idx in range(10):
            self.url = PROTOCOL + IP + ':' + str(PORT) + contentBase + '/post?'
            payload = {"SLEEP":str(self.slpCount)}
            print("** REQ: %s  Payload len/data:%d/%s" % (self.url, len(payload), payload),  flush=True)
            rsp = requests.post(url=self.url, data=payload)

        # response
            self.assertEqual(rsp.status_code, 200, "incorrect completion code")
            print("** RSP: text:%s" % (rsp.text))
            self.assertTrue(rsp.content.startswith(b"SLP:"), msg="preamble missing - data loss")
            self.assertGreater(len(rsp.text), self.slpCount, msg="under fill count - data loss" )






        



    



import unittest
from threading import Thread

from streamsx.topology.topology import *
from streamsx.topology.tester import Tester

from streamsx.topology.schema import *
import streamsx.topology.context
from streamsx import rest

import streamsx.spl.op as op
import streamsx.spl.toolkit as tk

from time import gmtime, strftime
import time
import datetime
import json
#import grequests   ! interesting including this causes bug. 
from requests import get, post, put, patch, delete, options, head

import argparse

PORT = 8080
IP = 'localhost'
PROTOCOL = "http://"
inetToolkit = "../../com.ibm.streamsx.inet"

# map/transform : input -> output 
def upperString(tuple):
    tuple["response"] = tuple["request"].upper()
    return tuple
# map/transform : input -> output
def lowerString(tuple):
    tuple["response"] = tuple["request"].lower()
    return tuple

# filter : return true, tuple moves on. 
def sleepMacbeth(tuple):
    """ Before Duncan's visit....  """
    time.sleep(1);
    return True

# sink : no output. 
def tupleToJson(tuple):
    return json.dumps(tuple)

# map/transform - 
def strToDict(str):
    t1 = str.decode('UTF-8')
    print("strToDict:" + t1)
    t2 = t1.replace("\'", '\"')
    return json.loads(t2)

# sink - no output
def webEntryLog(inTuple):
    print("webEntryLog:", inTuple, flush=True)
    return None
# sink - no output
def webExitLog(inTuple):
    print("webExitLog:", inTuple, flush=True)
    return None


request_methods = {
    'get': get,
    'post': post,
    'put': put,
    'patch': patch,
    'delete': delete,
    'options': options,
    'head': head,
}

def async_request(method, *args, callback=None, timeout=15, **kwargs):
    """Makes request on a different thread, and optionally passes response to a
    `callback` function when request returns.
    """
    print(*args)
    method = request_methods[method.lower()]
    if callback:
        def callback_with_args(response, *args, **kwargs):
            callback(response)
        kwargs['hooks'] = {'response': callback_with_args}
    kwargs['timeout'] = timeout
    thread = Thread(target=method, args=args, kwargs=kwargs)
    thread.start()




class buildHeaderResponse():
    """ Generate a response
    
    """
    def __init__(self, text):
        self.preamble = text

    def __call__(self, tuple):
        tuple['response']  = self.preamble + str(tuple['header'])
        return tuple


# transform/map : _init_() @ compilation, __call__ @ runtime
class sleepResponse():
    """ 
    Request has form of name:value&name:value, extact the 'SLEEP' value
    and wait for that period. Build a response using the orinal request 
    along with the key. 

    Note the setting of the headers content length, important. 
    """
    def __init__(self, text):
        self.preamble = text

    def __call__(self, tuple):
        """get the request and get rid of the '&' which may be messing up the resonse message"""
        # del tuple['header']['Content-Length']  # Do NOT include length in 
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
        sTime = strftime("%M:%S", gmtime())
        time.sleep(slp)
        eTime = strftime("%M:%S", gmtime())
        tuple['response']  = self.preamble + slpStr + "[" + tuple['request']  + "|" + sTime +".."+eTime  + "] KEY:" + str(tuple['key'])
        tuple['header']['Content-Length']  = str(len(tuple['response']))
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
                                    'webTimeout':15.0,
                                    'responseJsonAttributeName':'string',
                                    'context':'Reflect',
                                    'contextResourceBase': 'opt/Reflect'},
                            name = "RequestProcess")

        rawRequest.stream.sink(webEntryLog) ## log what we have received.

        
        onlyFirst = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/first", 
                                             name="firstFilter")
        onlySecond = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/second", 
                                             name="secondFilter")
        onlyThird = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/third", 
                                             name="thirdFilter")
        onlyFourth = rawRequest.stream.filter(lambda t : t["pathInfo"]=="/fourth", 
                                             name="fourthFilter")

        # do the work
        firstReflect = onlyFirst.transform(sleepResponse("SLP1:"), 
                                        name="firstResponse")
        secondReflect = onlySecond.transform(sleepResponse("SLP2:"), 
                                        name="secondResponse")
        thirdReflect = onlyThird.transform(sleepResponse("SLP3:"), 
                                        name="thirdResponse")
        fourthReflect = onlyFourth.transform(sleepResponse("SLP4:"), 
                                        name="fourthResponse")

        allReflect = firstReflect.union({secondReflect,thirdReflect,fourthReflect})


        allReflect.sink(webExitLog) ## log what we are sending back
        # do tests on what we have processed.
        self.tester.tuple_count(allReflect, expected_requests)
        # loopback to sending
        pending_source.complete(allReflect)  # loopback

        ## All done building the graph......

        # setup the code that will invoke this test. 
        self.tester.local_check = local_check_function

        # enable tracing info.
        job_config = streamsx.topology.context.JobConfig(job_name='Async', tracing="info")        
        job_config.add(self.test_config) 

        # submit the application for test
        self.tester.test(self.test_ctxtype, self.test_config)

        """
        Various length responses.
        """

    def test_asynRequests(self):
        print("Do the async test....")
        self.slpCount = 4
        self.reflectPost(expected_requests=4, local_check_function=self.asyncRequest)


    def asyncRequest(self):
        """
          Async requests, first submitted with longes wait time. 
          If return is in order we have problem
        """
        self.jobHealthy(4)
        self.urls = [
            'http://localhost:8080/Reflect/RequestProcess/ports/analyze/0/fourth?SLEEP=8&ORDER=forth',
            'http://localhost:8080/Reflect/RequestProcess/ports/analyze/0/third?SLEEP=4&ORDER=third',
            'http://localhost:8080/Reflect/RequestProcess/ports/analyze/0/second?SLEEP=2&ORDER=second',
            'http://localhost:8080/Reflect/RequestProcess/ports/analyze/0/first?SLEEP=1&ORDER=first'
        ]

        self.arrive = []
        cnt = 20
        notOrdered = False
        ## send request
        for url in self.urls:
            async_request('get', url, callback=lambda r: self.arrive.append(r.text))
            time.sleep(1)
        ## start waiting for response
        while((len(self.arrive) != len(self.urls)) and (cnt > 0)):
            cnt -= 1
            print("Waiting %d self.arrive %d expected" % (len(self.arrive), len(self.urls)))
            time.sleep(1)
        if (cnt != 0):
            print("All messages arrived %d self.arrive %d expected" % (len(self.arrive), len(self.urls)))            
        self.assertNotEqual(cnt, 0, msg="Missing return value, received less responses that expected?")

        # Rip apart the responses, make sure that they're out of order. 
        # KEY is the correlation get that is generated when on the receipt of a
        # request, it's a sequece number. Using this value to determine the order 
        # response. 
        firstArrive = int(self.arrive[0].split("KEY:")[1])
        print("firstArrive KEY:%d " % (firstArrive))

        for ele in self.arrive[1:]:
            keyCnt = ele.split("KEY:")
            if (len(keyCnt) != 2):
                print("raise error: maybe timed out");
            if (firstArrive > int(keyCnt[1])):
                notOrdered = True
                break

        for ele in self.arrive:
            print("Arrival list element: %s" % (ele))

        self.assertTrue(notOrdered, msg="All messages are in order. Synchronized in operator?")


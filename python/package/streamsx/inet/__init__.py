# coding=utf-8
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2018

"""
Overview
++++++++

Provides functions to run HTTP requests.

Use this package with the following services on IBM Cloud:

  * `Streaming Analytics <https://www.ibm.com/cloud/streaming-analytics>`_

Sample
++++++

A simple example of a Streams application that emits http requests::

    from streamsx.topology.topology import *
    from streamsx.topology.schema import CommonSchema, StreamSchema
    from streamsx.topology.context import submit
    import streamsx.inet as inet

    topo = Topology()
    url = 'http://httpbin.org/get'
    s = topo.source(['get-sample-with-fix-url']).as_string()
    res_http = inet.request_get(s, url)
    res_http.print()
    submit('STREAMING_ANALYTICS_SERVICE', topo)


"""

__version__='0.1.0'

#__all__ = ['request_get','request_post','request_put','request_delete']
#from streamsx.inet._inet import request_get,request_post,request_put,request_delete
__all__ = ['request_get']
from streamsx.inet._inet import request_get

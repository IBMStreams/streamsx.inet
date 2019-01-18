# coding=utf-8
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2019

"""
Overview
++++++++

Provides functions to run HTTP requests.

This package is compatible with Streaming Analytics service on IBM Cloud:

  * `IBM Streaming Analytics <https://www.ibm.com/cloud/streaming-analytics>`_

Sample
++++++

A simple example of a Streams application that emits http requests::

    from streamsx.topology.topology import *
    from streamsx.topology.schema import CommonSchema, StreamSchema
    from streamsx.topology.context import submit
    import streamsx.inet as inet

    topo = Topology()
    s = topo.source(['http://httpbin.org/get']).as_string()
    result_http_get = inet.request_get(s)
    result_http_get.print()

    submit('STREAMING_ANALYTICS_SERVICE', topo)


"""

__version__='0.1.0'

#__all__ = ['request_get','request_post','request_put','request_delete']
#from streamsx.inet._inet import request_get,request_post,request_put,request_delete
__all__ = ['request_get']
from streamsx.inet._inet import request_get

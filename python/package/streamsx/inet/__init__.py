# coding=utf-8
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2019

"""
Overview
++++++++

Provides functions to run HTTP requests.

The following method types are supported:
 * GET
 * DELETE
 * PUT
 * POST


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

    # HTTP GET REQUEST
    s1 = topo.source(['http://httpbin.org/get']).as_string()
    result_http_get = inet.request_get(s1)
    result_http_get.print()

    # HTTP PUT REQUEST
    s2 = topo.source(['hello world']).as_string()
    result_http_put = inet.request_put(s2, url='http://httpbin.org/put', content_type='text/plain')
    result_http_put.print()

    submit('STREAMING_ANALYTICS_SERVICE', topo)


Types
+++++

:py:const:`~streamsx.inet.HttpResponseSchema` - Structured schema containing HTTP GET/PUT/POST/DELETE response values.
The functions returns a stream with this schema.
``tuple<
rstring status,
int32 statusCode,
rstring contentEncoding,
rstring contentType,
list<rstring> responseHeader,
rstring responseData
>``


"""

__version__='0.3.0'

__all__ = ['request_delete', 'request_get', 'request_post','request_put', 'HttpResponseSchema']
from streamsx.inet._inet import request_delete, request_get, request_post, request_put, HttpResponseSchema

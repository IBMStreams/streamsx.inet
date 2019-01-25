from unittest import TestCase

import streamsx.inet as inet

from streamsx.topology.topology import Topology
from streamsx.topology.tester import Tester
from streamsx.topology.schema import CommonSchema, StreamSchema
import streamsx.spl.op as op
import streamsx.spl.toolkit
import streamsx.rest as sr

import datetime
import os
import json

##
## Test assumptions
##
## Streaming analytics service running
##

class TestHTTP(TestCase):

    def test_request_get_fixed_url(self):
        topo = Topology('test_request_get_fixed_url')

        url_sample = 'http://httpbin.org/get'
        s = topo.source(['fixed-url-test']).as_string()
        res_http = inet.request_get(s, url_sample)
        res_http.print()
        tester = Tester(topo)
        tester.tuple_count(res_http, 1)
        tester.run_for(60)
        tester.test(self.test_ctxtype, self.test_config, always_collect_logs=True)

    def test_request_get_url_in_input_stream_string_type(self):
        topo = Topology('test_request_get_url_in_input_stream_string_type')

        s = topo.source(['http://httpbin.org/get']).as_string()
        res_http = inet.request_get(s)
        res_http.print()
        tester = Tester(topo)
        tester.tuple_count(res_http, 1)
        tester.run_for(60)
        tester.test(self.test_ctxtype, self.test_config, always_collect_logs=True)

    def test_request_get_url_in_input_stream(self):
        topo = Topology('test_request_get_url_in_input_stream')

        pulse = op.Source(topo, "spl.utility::Beacon", 'tuple<rstring url>', params = {'iterations':1})
        pulse.url = pulse.output('"http://httpbin.org/get"')

        res_http = inet.request_get(pulse.stream, url_attribute='url', ssl_accept_all_certificates=True)
        res_http.print()

        tester = Tester(topo)
        tester.tuple_count(res_http, 1)
        tester.run_for(60)
        tester.test(self.test_ctxtype, self.test_config, always_collect_logs=True)

    def test_request_delete_url_in_input_stream_string_type(self):
        topo = Topology('test_request_delete_url_in_input_stream_string_type')

        s = topo.source(['http://httpbin.org/delete']).as_string()
        res_http = inet.request_delete(s, ssl_accept_all_certificates=True)
        res_http.print()
        tester = Tester(topo)
        tester.tuple_count(res_http, 1)
        tester.run_for(60)
        tester.test(self.test_ctxtype, self.test_config, always_collect_logs=True)

    def test_request_post_url_in_input_stream_string_type(self):
        topo = Topology('test_request_post_url_in_input_stream_string_type')

        s = topo.source(['http://httpbin.org/post']).as_string()
        res_http = inet.request_post(s)
        res_http.print()
        tester = Tester(topo)
        tester.tuple_count(res_http, 1)
        tester.run_for(60)
        tester.test(self.test_ctxtype, self.test_config, always_collect_logs=True)

    def test_request_post_url_in_input_stream_string_type_content_type_param(self):
        topo = Topology('test_request_post_url_in_input_stream_string_type_content_type_param')

        s = topo.source(['http://httpbin.org/post']).as_string()
        res_http = inet.request_post(s, content_type='application/x-www-form-urlencoded', ssl_accept_all_certificates=True)
        res_http.print()
        tester = Tester(topo)
        tester.tuple_count(res_http, 1)
        tester.run_for(60)
        tester.test(self.test_ctxtype, self.test_config, always_collect_logs=True)

    def test_request_put_with_url_contt_params_body_in_input_stream_string_type(self):
        topo = Topology('test_request_put_with_url_contt_params_body_in_input_stream_string_type')

        s = topo.source(['hello world']).as_string()
        result_http_put = inet.request_put(s, url='http://httpbin.org/put', content_type='text/plain', ssl_accept_all_certificates=False)
        result_http_put.print()
        tester = Tester(topo)
        tester.tuple_count(result_http_put, 1)
        tester.run_for(60)
        tester.test(self.test_ctxtype, self.test_config, always_collect_logs=True)

class TestHTTPDistributed(TestHTTP):
    def setUp(self):
        Tester.setup_distributed(self)

class TestHTTPStreaminAnalytics(TestHTTP):
    def setUp(self):
        Tester.setup_streaming_analytics(self, force_remote_build=True)

    @classmethod
    def setUpClass(self):
        # start streams service
        connection = sr.StreamingAnalyticsConnection()
        service = connection.get_streaming_analytics()
        result = service.start_instance()


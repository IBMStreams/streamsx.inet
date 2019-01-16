from unittest import TestCase

import streamsx.inet as inet

from streamsx.topology.topology import Topology
from streamsx.topology.tester import Tester
from streamsx.topology.schema import CommonSchema, StreamSchema
import streamsx.spl.op as op
import streamsx.spl.toolkit

import datetime
import os
import json

##
## Test assumptions
##
## Streaming analytics service running
##

class TestHTTP(TestCase):
    def setUp(self):
        Tester.setup_streaming_analytics(self, force_remote_build=True)

    def test_get_fixed_url_string_type_input(self):
        topo = Topology('test_String_input')

        url_sample = 'http://httpbin.org/get'
        s = topo.source(['fixed-url-test']).as_string()
        res_http = inet.request_get(s, url_sample)
        res_http.print()
        tester = Tester(topo)
        tester.tuple_count(res_http, 1)
        tester.run_for(60)
        tester.test(self.test_ctxtype, self.test_config, always_collect_logs=True)


#    def test_mixed_types(self):
#        creds_file = os.environ['DB2_CREDENTIALS']
#        with open(creds_file) as data_file:
#            credentials = json.load(data_file)
#        topo = Topology()
#
#        topo = Topology('test_SPLBeacon_Functor_JDBC')
#        pulse = op.Source(topo, "spl.utility::Beacon", 'tuple<rstring A, rstring B>', params = {'iterations':1})
#        pulse.A = pulse.output('"hello"')
#        pulse.B = pulse.output('"world"')
#
#        sample_schema = StreamSchema('tuple<rstring A, rstring B>')
#        query_schema = StreamSchema('tuple<rstring sql>')
#
#        sql_create = 'CREATE TABLE RUN_SAMPLE (A CHAR(10), B CHAR(10))'
#        create_table = db.run_statement(pulse.stream, credentials, schema=sample_schema, sql=sql_create)
# 
#        sql_insert = 'INSERT INTO RUN_SAMPLE (A, B) VALUES (?, ?)'
#        inserts = db.run_statement(create_table, credentials, schema=sample_schema, sql=sql_insert, sql_params="A, B")
#
#        query = op.Map('spl.relational::Functor', inserts, schema=query_schema)
#        query.sql = query.output('"SELECT A, B FROM RUN_SAMPLE"')
#
#        res_sql = db.run_statement(query.stream, credentials, schema=sample_schema, sql_attribute='sql')
#        res_sql.print()
#
#        sql_drop = 'DROP TABLE RUN_SAMPLE'
#        drop_table = db.run_statement(res_sql, credentials, sql=sql_drop)
#
#        tester = Tester(topo)
#        tester.tuple_count(drop_table, 1)
#        tester.test(self.test_ctxtype, self.test_config)


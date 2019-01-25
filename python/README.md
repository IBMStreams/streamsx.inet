# Python streamsx.inet package

This exposes SPL operators in the `com.ibm.streamsx.inet` toolkit as Python methods.

Package is organized using standard packaging to upload to PyPi.

The package is uploaded to PyPi in the standard way:
```
cd package
python setup.py sdist bdist_wheel upload -r pypi
```
Note: This is done using the `ibmstreams` account at pypi.org and requires `.pypirc` file containing the credentials in your home directory.

Package details: https://pypi.python.org/pypi/streamsx.inet

Documentation is using Sphinx and can be built locally using:
```
cd package/docs
make html
```
and viewed using
```
firefox python/package/docs/build/html/index.html
```

The documentation is also setup at `readthedocs.io`.

Documentation links:
* http://streamsxinet.readthedocs.io/en/pypackage

## Test

Package can be tested with TopologyTester using the [Streaming Analytics](https://www.ibm.com/cloud/streaming-analytics) service.

```
cd package
python3 -u -m unittest streamsx.inet.tests.test_inet.TestHTTPStreamingAnalytics
```

Package can be tested with TopologyTester using a local and running Streams domain.
Make shure that the streams environment is set, the domain and instance is running and the environment variables:
STREAMS_USERNAME
STREAMS_PASSWORD
are setup.

```
cd package
python3 -u -m unittest streamsx.inet.tests.test_inet.TestHTTPDistributed
```

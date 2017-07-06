#!/usr/bin/env python3

import requests

# Send contents from two binary files.
binaryFiles = ['data/sample1.tgz', 'data/sample2.tgz']

for file in binaryFiles:
    data = open(file, 'rb').read()
    res = requests.post(url='http://localhost:8080/BinData/ports/output/0/inject',
        data=data,
        headers={'Content-Type': 'application/octet-stream'})
    print("Done ", file)


#data = open('./http_bin_test.py', 'r').read()
#res = requests.post(url='http://streamsqse:8080/BinData/ports/output/0/inject',
#                    data=data,
#                    headers={'Content-Type': 'text/plain'})


# let's check if what we sent is what we intended to send...
#import json
#import base64

#assert base64.b64decode(res.json()['data'][len('data:application/octet-stream;base64,'):]) == data

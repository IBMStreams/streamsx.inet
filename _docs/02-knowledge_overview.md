---
title: "Toolkit technical background overview"
permalink: /docs/knowledge/overview/
excerpt: "Basic knowledge of the toolkits technical domain."
last_modified_at: 2017-08-04T12:37:48-04:00
redirect_from:
   - /theme-setup/
sidebar:
   nav: "knowledgedocs"
---
{% include toc %}
{% include editme %}


The Internet Toolkit provides support for common internet protocol client functions and operators.

This toolkit separates its functionality into a number of namespaces:
* namespace:com.ibm.streamsx.inet: General purpose internet operator supporting a number of protocols.
* namespace:com.ibm.streamsx.inet.ftp: Operators that interact with external FTP servers.
* namespace:com.ibm.streamsx.inet.http: Operators that interact with external HTTP servers.

The http rest functions and the WebSocket server functions are now moved into [streamsx.inetserver toolkit](https://github.com/IBMStreams/streamsx.inetserver/releases)

This version of the toolkit is intended for use with IBM Streams release 4.0.1 and later.


/*
*******************************************************************************
* Copyright (C) 2007, 2012-2014, International Business Machines Corporation. 
* All Rights Reserved. *
*******************************************************************************
*/


static const char* IBM_COPYRIGHT(void) __attribute__ ((used));
static const char* IBM_COPYRIGHT(void) { return 
" Copyright 2013 IBM Corporation                                    "
"                                                                   "
" Licensed under the Apache License, Version 2.0 (the License);     "
" You may not use this file except in compliance with the License.  "
" You may obtain a copy of the License at                           "
"                                                                   "
" http://www.apache.org/licenses/LICENSE-2.0                        ";
}

#include <iostream>
#include <cstring>
#include <regex.h>
#include <vector>
#include <URIHelper.h>
#include <sstream>


using namespace std;
using namespace com_ibm_streams_adapters;

//---- URIQueryComponents class

void URIQueryComponents::print(ostream& o) const {
  for(map<string, string>::const_iterator i=nameValuePairs.begin();
    i!=nameValuePairs.end();++i) {
    o << "name: '" << i->first
      << "' value: '" << i->second
      << "' ";
  }
}

ostream& com_ibm_streams_adapters::operator<< (ostream& o, const URIQueryComponents& qc) {
  qc.print(o);
  return o;
}

//---- URIHelper class

// subset regex of URI defined in RFC 2396
const char* URIHelper::URIREGEX=
  /* protocol (opt) */ \
  "^(([[:alpha:]][[:alnum:]+.-]*):)?" \
  /* userinfo (opt) */ \
  "//(([[:alnum:]_.!~*\'();:&=+$,-]*)@)?" \
  /* hostname (opt) */ \
  "(((([[:alnum:]]|[[:alnum:]][[:alnum:]-]*[[:alnum:]])\\.)*" \
  "([[:alpha:]]|[[:alpha:]][[:alnum:]-]*[[:alnum:]]))\\.?|" \
  /* IPv4address */ \
  "([[:digit:]]+\\.[[:digit:]]+\\.[[:digit:]]+\\.[[:digit:]]+))?" \
  /* port (opt) */ \
  "(:([[:digit:]]+))?" \
  /* path (opt) */ \
  "/(([[:alnum:]_.!~*\'():@&=+$,-]|%[[:digit:]a-fA-F][[:digit:]a-fA-F])*" \
  "(;([[:alnum:]_.!~*\'():@&=+$,-]|%[[:digit:]a-fA-F][[:digit:]a-fA-F])*)*" \
  "(/([[:alnum:]_.!~*\'():@&=+$,-]|%[[:digit:]a-fA-F][[:digit:]a-fA-F])*" \
  "(;([[:alnum:]_.!~*\'():@&=+$,-]|%[[:digit:]a-fA-F][[:digit:]a-fA-F])*)*" ")*" ")" \
  /* query (opt) */ \
  "(\\?([[:alnum:]_.!~*\'():@&=+$,;/?:@&=+$,-]|%[[:digit:]a-fA-F][[:digit:]a-fA-F])*)?$";

URIHelper::URIHelper(void) : proto(UNDEFINED), portnum(-1) {
}

URIHelper::URIHelper(const string& uri) : proto(UNDEFINED), portnum(-1) {
  init(uri);
}

void URIHelper::init(const string& uri) {
  string str=trim(uri);
    if (!parseURI(str)) {
      ostringstream err;
      err << "URI is not syntatically correct '" << uri << "'";
      throw std::exception();
   }
  if (protocol == "file") {
    proto=FILE;
  }
  else if (protocol == "http" || protocol == "https") {
    proto=HTTP;
  }
  else if (protocol == "ftp" || protocol == "ftps") {
    proto=FTP;
  }
  else if (!protocol.empty()) {
    ostringstream err;
    err << "Protocol not supported: '" << protocol << "'";
    throw std::exception();
  }
  if (!port.empty())
    fromString<int>(portnum,port);
}

bool URIHelper::parseURI(const string& uri) {
  regex_t reg;
  regmatch_t match[NMATCH];

  if(uri.empty())
    return false;

  int ret1=regcomp(&reg, URIREGEX, REG_EXTENDED);
  int ret2=regexec(&reg, uri.c_str(), NMATCH, match, 0);
  regfree(&reg);

  if(ret1 != 0 || ret2 != 0) {
    return false;
  }

  if (match[SCHEME_NUM].rm_so > -1) {
    char temp[match[SCHEME_NUM].rm_eo-match[SCHEME_NUM].rm_so+1];
    memset(temp,0,match[SCHEME_NUM].rm_eo-match[SCHEME_NUM].rm_so+1);
    strncpy(temp, uri.c_str() + match[SCHEME_NUM].rm_so,
	     match[SCHEME_NUM].rm_eo-match[SCHEME_NUM].rm_so);
    protocol.assign(temp);
  }

  if (match[USERINFO_NUM].rm_so > -1) {
    char temp[match[USERINFO_NUM].rm_eo-match[USERINFO_NUM].rm_so+1];
    memset(temp,0,match[USERINFO_NUM].rm_eo-match[USERINFO_NUM].rm_so+1);
    strncpy(temp, uri.c_str() + match[USERINFO_NUM].rm_so,
	     match[USERINFO_NUM].rm_eo-match[USERINFO_NUM].rm_so);
    userinfo.assign(temp);
  }

  if (match[HOST_NUM].rm_so > -1) {
    char temp[match[HOST_NUM].rm_eo-match[HOST_NUM].rm_so+1];
    memset(temp,0,match[HOST_NUM].rm_eo-match[HOST_NUM].rm_so+1);
    strncpy(temp, uri.c_str() + match[HOST_NUM].rm_so,
	     match[HOST_NUM].rm_eo-match[HOST_NUM].rm_so);
    host.assign(temp);
  }

  if (match[PORT_NUM].rm_so > -1) {
    char temp[match[PORT_NUM].rm_eo-match[PORT_NUM].rm_so+1];
    memset(temp,0,match[PORT_NUM].rm_eo-match[PORT_NUM].rm_so+1);
    strncpy(temp, uri.c_str() + match[PORT_NUM].rm_so,
	     match[PORT_NUM].rm_eo-match[PORT_NUM].rm_so);
    port.assign(temp);
  }

  if (match[PATH_NUM].rm_so > -1) {
    char temp[match[PATH_NUM].rm_eo-match[PATH_NUM].rm_so+1];
    memset(temp,0,match[PATH_NUM].rm_eo-match[PATH_NUM].rm_so+1);
    strncpy(temp, uri.c_str() + match[PATH_NUM].rm_so,
	     match[PATH_NUM].rm_eo-match[PATH_NUM].rm_so);
    path.assign(temp);
  }

  if (match[QUERY_NUM].rm_so > -1) {
    char temp[match[QUERY_NUM].rm_eo-match[QUERY_NUM].rm_so+1];
    memset(temp,0,match[QUERY_NUM].rm_eo-match[QUERY_NUM].rm_so+1);
    strncpy(temp, uri.c_str() + match[QUERY_NUM].rm_so,
	     match[QUERY_NUM].rm_eo-match[QUERY_NUM].rm_so);
    query.assign(temp);
    retrieveQueryComponents();
  }

  return true;
}

void URIHelper::retrieveQueryComponents(void) {
  size_t found=query.find_first_of("?");
  string parameters(query.substr(found+1));
  /*
  cout << "---> " << query << endl;
  cout << "---> " << parameters << endl;
  */
  vector<string> tokens;
  tokenize(parameters,tokens,"&",false);
  for(vector<string>::const_iterator i=tokens.begin(); i!=tokens.end(); ++i) {
    vector<string> nameValue;
    /*
    cout << "-----> " << *i << endl;
    */
    tokenize(*i,nameValue,"=",false);
    if (nameValue.size()==1)
      qc.nameValuePairs.insert(make_pair(trim(nameValue[0]),""));
    else if (nameValue.size()==2)
      qc.nameValuePairs.insert(make_pair(trim(nameValue[0]),trim(nameValue[1])));
  }
}

bool URIHelper::isMulticastAddress(void) const {
  size_t found=host.find_first_of(".");
  if (found==string::npos)
    return false;
  string fstoctet(host.substr(0,found));
  /*
  cout << fstoctet << endl;
  */
  try {
    short octet;
    fromString(octet,fstoctet);
    if (octet>=224 && octet<=239)
      return true;
  }
  catch(...) {
    // whatever!
  }
  return false;
}

void URIHelper::print(ostream& o) const {
  o << "protocol: '" << protocol
    << "' userinfo: '" << userinfo
    << "' host: '" << host
    << "' port: '" << port
    << "' path: '" << path
    << "' query: '" << query
    << "'";
}

ostream& com_ibm_streams_adapters::operator<< (ostream& o, const URIHelper& uri) {
  uri.print(o);
  return o;
}


/*
*******************************************************************************
* Copyright (C) 2007,2012-2014, International Business Machines Corporation. 
* All Rights Reserved. *
*******************************************************************************
*/

// Class Description:
//
// Implements a helper for parsing and identifying what an URI refers to

#ifndef URIHELPER_H
#define URIHELPER_H
#include<map>
#include<sstream>

namespace com_ibm_streams_adapters {

typedef std::map<std::string, std::string> URIQueryComponentsNVP_t;
typedef std::map<std::string, std::string>::iterator URIQueryComponentsNVP_it_t;
typedef std::map<std::string, std::string>::const_iterator URIQueryComponentsNVP_cit_t;

/// Return the input string with spaces suffixing it being removed
/// @param str input string
/// @param t character to be removed from right side of input string
/// @return input string without spaces suffixing it
inline std::string rtrim(const std::string& source,
  const std::string& t=" ") {
  std::string str=source;
  return str.erase(str.find_last_not_of(t)+1);
}

/// Return the input string with spaces prefixing it being removed
/// @param str input string
/// @param t character to be removed from left side of input string
/// @return input string without spaces prefixing it
inline std::string ltrim(const std::string& source,
  const std::string & t = " " ) {
  std::string str = source;
  return str.erase (0,source.find_first_not_of(t));
}

/// Return the input string with spaces suffixing and prefixing it being
/// removed
/// @param str input string
/// @param t character to be removed from left and right sides of input string
/// @return input string without spaces prefixing and suffixing it
inline std::string trim(const std::string& source,
  const std::string& t=" ") {
  std::string str=source;
  return ltrim(rtrim(str,t),t);
}


/// Tokenize a string
/// @param str string to be tokenized
/// @param tokens vector with the list of tokens
/// @param delimiters string with the characters delimiting each token
/// @param keepEmptyTokens keep empty tokens
void tokenize(const std::string& str, std::vector<std::string>& tokens,
              const std::string& delimiter, bool keepEmptyTokens) ;

/// Converts a string to a different (numerical) type
/// @param t converted value
/// @param s input string
template <class T> void fromString(T& t, const std::string& s) {
  if (s.empty()) {
    t=static_cast<T>(0);
  }
  else {
    std::istringstream iss(s);
    iss >> t;
    if (iss.fail())
      //THROW(FailedConversion,"string '" << s << "' conversion failed");
      throw std::exception();
    if (!iss.eof())
      throw std::exception();
      //      THROW(SpuriousCharacterFound,"string '" << s << "' contains spurious character");
  }
}

class URIQueryComponents {
  public:
    URIQueryComponentsNVP_t nameValuePairs;
  protected:
    /// Print internal state to an output stream
    /// @param o output stream
    void print(std::ostream& o) const;
  friend std::ostream& operator<< (std::ostream& o, const URIQueryComponents& qc);
};

class URIHelper {
  public:
    enum ProtocolType {
      UNDEFINED = -1,
      FILE = 8,
      HTTP = 9,
      FTP = 10
    };

    /// Default constructor
    URIHelper(void);

    /// Construct a valid URI
    /// @param uri a string with the URI
    URIHelper(const std::string& uri);

    /// Initialize an empty URI object
    /// @param uri a string with the URI
    void init(const std::string& uri);

    /// Retrieve the protocol specified as part of the URI
    /// @return the protocol
    inline ProtocolType getProtocol(void) const { return proto; };

    /// Retrieve the protocol specified as part of the URI
    /// @return the protocol
    inline const std::string& getProtocolName(void) const { return protocol; };

    /// Retrieve the host specified as part of the URI
    /// @return the host
    inline const std::string& getHost(void) const { return host; };

    /// Return whether the host is a multicast address
    /// @return true or false
    bool isMulticastAddress(void) const;

    /// Retrieve the user information specified as part of the URI
    /// @return the user info
    inline const std::string& getUserInfo(void) const { return userinfo; };

    /// Retrieve the port number specified as part of the URI
    /// @return the port number
    inline int getPort(void) const { return portnum; };

    /// Retrieve the path specified as part of the URI
    /// @return the path
    inline const std::string& getPath(void) const { return path; };

    /// Retrieve the query specified as part of the URI
    /// @return the query
    inline const std::string& getQuery(void) const { return query; };

    /// Retrieve a reference to the internal URIQueryComponents object
    /// @return a reference to the underlying URIQueryComponents object
    const URIQueryComponents& getQueryComponents(void) const {
      return qc;
    };

    /// Retrieve a reference to the name-value pairs in the underlying
    /// URIQueryComponents object
    /// @return a reference to the collection of name-value pairs
    const URIQueryComponentsNVP_t& getQCNameValuePairs(void) const {
      return qc.nameValuePairs;
    };

    /// Destructor
    ~URIHelper(void) {};
  protected:
    enum {
      SCHEME_NUM = 2,
      USERINFO_NUM = 4,
      HOST_NUM = 5,
      HOSTNAME_NUM = 6,
      IPV4ADDR_NUM = 10,
      PORT_NUM = 12,
      PATH_NUM = 13,
      QUERY_NUM = 21,
      NMATCH = QUERY_NUM+1
    };

    /// Parse a URI into its components
    /// @param uri the string with the URI to be parsed
    /// @return true if the string was properly formatted as an URI
    bool parseURI(const std::string& uri);

    /// Retrieve the query components from the query string and
    /// build URIQueryComponents object
    void retrieveQueryComponents(void);

    /// Print internal state to an output stream
    /// @param o output stream
    void print(std::ostream& o) const;

    ProtocolType proto;
    std::string protocol;
    std::string userinfo;
    std::string host;
    int portnum;
    std::string port;
    std::string path;
    std::string query;
    URIQueryComponents qc;

    static const char* URIREGEX;
    friend std::ostream& operator<< (std::ostream& o, const URIHelper& uri);
};

}

#endif

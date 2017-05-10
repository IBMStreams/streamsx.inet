#include "httpFunctions.h"
#include "SPL/Runtime/Common/RuntimeDebug.h"
#include <SPL/Runtime/Type/List.h>
#include <tr1/unordered_map>
#include <vector>

/**
 * Some notes.
 * (1) This has a constructor.  We need to call curl_global_init when we start the program.  
 * See http://curl.haxx.se/libcurl/c/curl_global_init.html for details
 * 
 * (2) All CURL* handles are thread local static local variables.  They are thread local so we
 * don't have to worry about concurrency, and they are static because it's more efficient for
 * libcurl to re-use curl handles.  I was not able to re-use handles successfully between 
 * operations, so each of them is also local to the function its used in. 
 *
 * (3) We keep a thread local variable of the active CURL pointers.  Whenever one of the pointers
 * is initialized, we push it onto this vector.  That way, we can de-allocate it in the destructor.
 *
 * (4) Extra headers arrive as SPL::list<SPL::rstring>.  We need to make that a curl_slist.  Both
 * to prevent memory leaks and to save some time, we cache these header lists.  We expect the same
 * header list is typically used over and over again.  These are also destroyed in the destructor.
 */

namespace com_ibm_streamsx_inet_http {


    typedef std::tr1::unordered_map<SPL::list<SPL::rstring>,struct curl_slist*> HeaderCache;

    /// Store the active CURL pointers so we can clean them up on exit.
    static __thread std::vector<CURL*>* activeCurlPointers = NULL;

    const char* logTag = "httpForStreams";

    /// Store the header lists that are used, both so we can don't have to re-create them with 
    /// every call and so that we don't need to worry about freeing them until the cache is full
    /// or until the destructor is called.
    static __thread HeaderCache* headerCache = NULL;

    static bool asyncDnsSupport;
    /**
     * curl_global_init is not thread-safe, so we must call it before the program is multi-threaded.
     */
    __attribute__((constructor)) void initializeCurl() {
        curl_global_init(CURL_GLOBAL_ALL);
        //read the async dns support flag and store into static var
        curl_version_info_data * dat =  curl_version_info(CURLVERSION_NOW);
        if (dat->features & CURL_VERSION_ASYNCHDNS) asyncDnsSupport = true; else asyncDnsSupport = false;
        SPLAPPTRC(L_INFO, "Async DNS supported="<< asyncDnsSupport, logTag);
        std::cout << logTag << ": Async DNS supported="<< asyncDnsSupport << std::endl;
    }

    /**
     * Cleanup the curl pointers and headers.
     */

    __attribute__((destructor)) void cleanupCurl() {


        while (activeCurlPointers != NULL && activeCurlPointers->size() > 0 ){
            curl_easy_cleanup(activeCurlPointers->back());
            activeCurlPointers->pop_back();
        }
        delete activeCurlPointers;

        if (headerCache != NULL) {
            for (HeaderCache::const_iterator del = headerCache->begin(); del != headerCache->end(); del++) {
                curl_slist_free_all(del->second);
            }
            headerCache->clear();
            delete headerCache;
        }
        curl_global_cleanup();
    }

    // We save the set of active curl handles so we can clean them up on shutdown.
    void addCurlHandle(CURL * handle) {

        if (activeCurlPointers == NULL) {
            activeCurlPointers = new std::vector<CURL*>(6);
        }
        activeCurlPointers->push_back(handle);

    }


    // I'm not sure caching these is actually faster, but it does mean
    // that the calling functions don't have to worry about freeing the memory.

    struct curl_slist * getSList(SPL::list<SPL::rstring> theList) {
        if (headerCache == NULL) {
            headerCache = new HeaderCache();
        }
        HeaderCache::iterator it = headerCache->find(theList);
        if (it == headerCache->end()) {
            SPLAPPTRC(L_DEBUG,"Adding to cache",logTag);
            // Not cached, so build a new slist.
            struct curl_slist * toReturn = NULL;
            for ( SPL::list<SPL::rstring>::const_iterator it = theList.begin(); it != theList.end(); it++) {
                toReturn = curl_slist_append(toReturn,it->c_str());
            }
            // Lots of saved stuff; lets clear out the cache.
            if (headerCache->size() > 20) {
                for (HeaderCache::const_iterator del = headerCache->begin(); del != headerCache->end(); del++) {
                    curl_slist_free_all(del->second);
                }
                headerCache->clear();
            }
            std::pair<HeaderCache::iterator,bool> res = headerCache->insert(std::make_pair<SPL::list<SPL::rstring>,struct curl_slist *>(theList,toReturn));
            if (!res.second) {
                SPLAPPTRC(L_ERROR,"Internal error",logTag);
            }
            return toReturn;
        }
        else {
            SPLAPPTRC(L_DEBUG,"Using cached value for header list",logTag);
            return it->second;
        }
    }

    // create the blob with the bytes from the file.
    size_t populate_blob(char *ptr,size_t size, size_t nmemb, void*userdata) {
        SPLAPPTRC(L_DEBUG,"Populate blob called with " << size  << " and nmemb " << nmemb,logTag);
        SPL::blob * toReturn = (SPL::blob*)userdata;
        if (toReturn->getSize() == 0) {
            toReturn->setData((const unsigned char*)ptr,size*nmemb);
        }
        else {
            toReturn->append((const unsigned char*)ptr,size*nmemb);
        }
        return size*nmemb;
    }

    /**
     * fill the rstring with data.
     * @param ptr data
     * @param size size of each data item
     * @param nmemb number of data items.
     * @param userdata pointer to an rstring
     */
    size_t populate_rstring(char *ptr,size_t size, size_t nmemb, void*userdata) {
        SPLAPPTRC(L_DEBUG,"Populate blob called with " << size  << " and nmemb " << nmemb,logTag);
        SPL::rstring * toReturn = (SPL::rstring*)userdata;
        if (toReturn->size() == 0) {
            toReturn->assign((const  char*)ptr,size*nmemb);
        }
        else {
            toReturn->append((const  char*)ptr,size*nmemb);
        }
        return size*nmemb;
    }

    /**
     * Used to add a header to the header list.  
     * @param buffer buffer containing the header.
     * @param size size of the data items in the buffer
     * @param nitems nubmer of data items in teh buffer
     * @param userdata pointer to an list of rstring.
     */
    size_t addToHeaderList(char * buffer, size_t size, size_t nitems, void * userdata) {
        SPL::list<SPL::rstring> * theList = (SPL::list<SPL::rstring>*)userdata;
        //std::cout << "addToHeaderList size=" << size << " nitems=" << nitems << std::endl;
        SPL::rstring newString(buffer,size*nitems);
        //std::cout << "newString=" << newString << std::endl;
        theList->push_back(newString);
        return size*nitems;
    }

    /**
     * Add common options to the curl pointer
     */
    CURLcode addCommonOpts(CURL * curl, const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, const SPL::int32 * requestTimeout, const SPL::int32 * connectionTimeout) {

        CURLcode res = curl_easy_setopt(curl,CURLOPT_URL,url.c_str());
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error " << res << " setting URL", logTag);
            return res;
        }

        if (extraHeaders.size() > 0) {
            struct curl_slist * extraHeadersSlist = getSList(extraHeaders);
            res = curl_easy_setopt(curl,CURLOPT_HTTPHEADER,extraHeadersSlist);
        }
        else {
            res = curl_easy_setopt(curl,CURLOPT_HTTPHEADER,NULL);
        }
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error " << res << " setting http header", logTag);
            return res;
        }

        res=curl_easy_setopt(curl,CURLOPT_SSL_VERIFYPEER,0);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error " << res << " setting no-verify", logTag);
            return res;
        }

        if (username.length() > 0 ) {
            CURLcode res=curl_easy_setopt(curl,CURLOPT_USERNAME,(char*)(username.c_str()));
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR, "Error " << res << " setting username", logTag);
                return res;
            }
        }
        if (password.length() > 0) {
            CURLcode res = curl_easy_setopt(curl,CURLOPT_PASSWORD,(char*)(password.c_str()));
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR, "Error " << res << " setting password", logTag);
                return res;
            }
        }
        if (requestTimeout) {
            if (! asyncDnsSupport) {
                //std::cout << "Setting CURLOPT_NOSIGNAL=1" << std::endl;
                long nosig = 1;
                res = curl_easy_setopt(curl, CURLOPT_NOSIGNAL, nosig);
                if (res != CURLE_OK) {
                    SPLAPPTRC(L_ERROR, "Error " << res << " CURLOPT_NOSIGNAL=1", logTag);
                    return res;
                }
            }
            res = curl_easy_setopt(curl, CURLOPT_TIMEOUT, *requestTimeout);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR, "Error " << res << " requestTimeout=" << *requestTimeout, logTag);
                return res;
            }
            res = curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, *connectionTimeout);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR, "Error " << res << " connectionTimeout=" << *connectionTimeout, logTag);
                return res;
            }
        } else {
            if (! asyncDnsSupport) {
                //std::cout << "Setting CURLOPT_NOSIGNAL=0" << std::endl;
                long nosig = 0;
                res = curl_easy_setopt(curl, CURLOPT_NOSIGNAL, nosig);
                if (res != CURLE_OK) {
                    SPLAPPTRC(L_ERROR, "Error " << res << " CURLOPT_NOSIGNAL=0", logTag);
                    return res;
                }
            }
            res = curl_easy_setopt(curl, CURLOPT_TIMEOUT, 0);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR, "Error " << res << " requestTimeout=0", logTag);
                return res;
            }
            res = curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 120);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR, "Error " << res << " connectionTimeout=120", logTag);
                return res;
            }
        }
        return CURLE_OK;
    }

    /**
     * urlEncode the input string
     * @param raw string to URL encode
     * @returns url encoding of raw
     */

    SPL::rstring urlEncode(const SPL::rstring & raw) {
        static __thread CURL* encode = NULL;
        if (encode == NULL) {
            encode = curl_easy_init();
            addCurlHandle(encode);
        }
        char * result = curl_easy_escape(encode,raw.data(),raw.size());
        SPL::rstring toReturn(result);
        curl_free(result);
        return toReturn;
    }

    /**
     * urlDecode an rstring
     * @param URL encoded string
     * @returns decoded version of string
     */
    SPL::rstring urlDecode(const SPL::rstring & encoded) {
        static __thread CURL* decode = NULL;
        if (decode == NULL) {
            decode = curl_easy_init();
            addCurlHandle(decode);
        }
        int length = 0;
        char * result = curl_easy_unescape(decode,encoded.data(), encoded.size(),&length);
        SPL::rstring toReturn(result,length);
        curl_free(result);
        return toReturn;
    }

    /**
     * Set the options necessary to read the result of a call as an rstring
     */

    CURLcode readResultAsRstring(CURL* curl , SPL::rstring * resultPtr) {
        // Now handle read, for error checking and exception handling
        SPL::rstring toReturn;
        CURLcode res = curl_easy_setopt(curl,CURLOPT_WRITEDATA,resultPtr);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error " << res << "setting data pointer", logTag);
            return res;
        }
        res = curl_easy_setopt(curl,CURLOPT_WRITEFUNCTION,&(populate_rstring));
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error " << res << " setting write function", logTag);
        }
        return res;
    }

    /**
     * Convenience class for holding an rstring and a pointer to what's already been sent.
     */
    class RstringAndIndex {
        public:
            const SPL::rstring * theData;
            size_t numSent;
    };

    /**
     * read data from an rstring.
     * @param instream a RstringAndIndex object
     */
    size_t readFromRstring(char * buffer, size_t size, size_t nitems, void *instream) {
        SPLAPPTRC(L_DEBUG,"readFromRstring size " << size << " nitems " << nitems, logTag);
        RstringAndIndex* theStruct = (RstringAndIndex*)instream;
        const SPL::rstring * toSend = theStruct->theData;

        uint32_t i = 0;
        // probably can use mem copy.
        for (; theStruct->numSent < toSend->length() && i < size*nitems; i++,theStruct->numSent++) {
            buffer[i] = *(toSend->data() + theStruct->numSent);
        }
        if (size*nitems < toSend->length()) {
            SPLAPPTRC(L_DEBUG,"Libcurl buffer size is " << size*nitems << " blob size is " << toSend->length(), logTag);
        }
        SPLAPPTRC(L_DEBUG,"sent " << i << " bytes, numSent is " << theStruct->numSent,logTag);
        return i;
    }

    /*
     * use httpDelete
     */
    SPL::rstring httpDelete_(const SPL::rstring & url, const SPL::list<SPL::rstring> &extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::int32 & error, const SPL::int32 * requestTimeout, const SPL::int32 * connectionTimeout) {
        static __thread CURL* curlDelete = NULL;
        error = 0;
        if (curlDelete == NULL) {
            curlDelete = curl_easy_init();
            if (curlDelete == NULL) {
                SPLAPPTRC(L_ERROR, "Error on curl_easy_init", logTag);
                error = -1;
                return "";
            }
            addCurlHandle(curlDelete);
        }

        CURLcode res=addCommonOpts(curlDelete, url, extraHeaders, username, password, requestTimeout, connectionTimeout);
        if (res != CURLE_OK) {
            error = res;
            return "";
        }
        res = curl_easy_setopt(curlDelete,CURLOPT_CUSTOMREQUEST,"DELETE");
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error code " << res << " setting delete request", logTag);
            error = res;
            return "";
        }
        SPL::rstring toReturn;
        res = readResultAsRstring(curlDelete,&toReturn);
        if (res != CURLE_OK) {
            error = res;
            return "";
        }
        res = curl_easy_perform(curlDelete);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error " << res << " on curl_easy_perform", logTag);
            error = res;
            return "";
        }
        return toReturn;
    }

    SPL::rstring httpDelete(const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::int32 & error) {
       return httpDelete_(url, extraHeaders, username, password, error, NULL, NULL);
    }
    SPL::rstring httpDelete(const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::int32 & error, const SPL::int32 requestTimeout, const SPL::int32 connectionTimeout) {
        return httpDelete_(url, extraHeaders, username, password, error, &requestTimeout, &connectionTimeout);
    }


    SPL::rstring httpPost_(const SPL::rstring & data, const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::list<SPL::rstring> & headers,SPL::int32 & error, const SPL::int32 * requestTimeout, const SPL::int32 * connectionTimeout) {
        static __thread CURL* curlPost =NULL;
        error = 0;
        // curlPost is defined as a thread-local static variable.
        if (curlPost == NULL) {
            curlPost = curl_easy_init();
            if (curlPost == NULL) {
                SPLAPPTRC(L_ERROR, "Error on curl_easy_init", logTag);
                error = -1;
                return "";
            }
            addCurlHandle(curlPost);
        }

        headers.clear();
        CURLcode res = addCommonOpts(curlPost,url,extraHeaders,username, password, requestTimeout, connectionTimeout);
        if (res != CURLE_OK) {
            error = res;
            return "";
        }
        res = curl_easy_setopt(curlPost,CURLOPT_HEADERDATA,(void*)&headers);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR,"Error code " << res << " setting header data",logTag);
            error = res;
            return "";
        }
        res = curl_easy_setopt(curlPost,CURLOPT_HEADERFUNCTION,&addToHeaderList);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error code " << res << " setting header function", logTag);
            error = res;
            return "";
        }

        res = curl_easy_setopt(curlPost,CURLOPT_POSTFIELDS,data.data());
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error code " << res << " setting post data", logTag);
            error = res;
            return "";
        }
        curl_easy_setopt(curlPost,CURLOPT_POSTFIELDSIZE,data.length());
        if (res!= CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error code " << res << " setting post data size", logTag);
            error = res;
            return "";
        }

        // Now handle read, for error checking and exception handling
        SPL::rstring toReturn;
        res = readResultAsRstring(curlPost,&toReturn);
        if (res != CURLE_OK) {
            error = res;
            return "";
        }
        res = curl_easy_setopt(curlPost,CURLOPT_FOLLOWLOCATION,0);
        if (res != CURLE_OK) {
        	SPLAPPTRC(L_ERROR, "Error code " << res << " setting follow action = 0", logTag);
            error = res;
            return "";
        }

        SPLAPPTRC(L_DEBUG,"About to perform",logTag);
        // ALL DONE!  Do action.
        res = curl_easy_perform(curlPost);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error " << res << " on curl_easy_perform", logTag);
            error =res;
            return "";
        }
        error = 0; // All went well!
        return toReturn;

    }

    SPL::rstring httpPost(const SPL::rstring &  data, const  SPL::rstring &  url, const SPL::list<SPL::rstring> & extraHeaders,  const SPL::rstring &  username, const SPL::rstring & password, SPL::list<SPL::rstring>& headers, SPL::int32 & error) {
        return httpPost_(data, url, extraHeaders, username, password, headers, error, NULL, NULL);
    }
    SPL::rstring httpPost(const SPL::rstring &  data, const  SPL::rstring &  url, const SPL::list<SPL::rstring> & extraHeaders,  const SPL::rstring &  username, const SPL::rstring & password, SPL::list<SPL::rstring>& headers, SPL::int32 & error, const SPL::int32 requestTimeout, const SPL::int32 connectionTimeout) {
        return httpPost_(data, url, extraHeaders, username, password, headers, error, &requestTimeout, &connectionTimeout);
    }


    SPL::rstring httpPut_(const SPL::rstring & data, const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::list<SPL::rstring> & headers, SPL::int32 & error, const SPL::int32 * requestTimeout, const SPL::int32 * connectionTimeout) {
        static __thread CURL* curlPut = NULL;
        // curlPut is a thread-local static variable.
        if (curlPut == NULL) {
            curlPut = curl_easy_init();
            if (curlPut == NULL) {
                SPLAPPTRC(L_ERROR, "Error on curl_easy_init", logTag);
                error = -1;
                return "";
            }
            addCurlHandle(curlPut);
        }

        headers.clear();
        CURLcode res = addCommonOpts(curlPut,url,extraHeaders,username, password, requestTimeout, connectionTimeout);
        if (res != CURLE_OK) {
            error = res;
            return "";
        }
        res = curl_easy_setopt(curlPut,CURLOPT_HEADERDATA,(void*)&headers);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR,"Error code " << res << " setting header data",logTag);
            error = res;
            return "";
        }
        res = curl_easy_setopt(curlPut,CURLOPT_HEADERFUNCTION,&addToHeaderList);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error code " << res << " setting header function", logTag);
            error = res;
            return "";
        }
        //curl_easy_setopt(curlPut,CURLOPT_VERBOSE,1);
        // Set post vs put.
        res = curl_easy_setopt(curlPut,CURLOPT_UPLOAD,1);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error code " << res << " setting option CURLOPT_UPLOAD" ,logTag);
            error = res;
            return "";
        }

        RstringAndIndex readHelper;
        if (data.length() > 0 ) {
            readHelper.theData = &data;
            readHelper.numSent = 0;
            res = curl_easy_setopt(curlPut,CURLOPT_READFUNCTION,&readFromRstring);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR, "Error code " << res << "setting read function", logTag);
                error = res;
                return "";
            }   

            res = curl_easy_setopt(curlPut,CURLOPT_READDATA,&readHelper);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR,"Error code " << res << " setting read data", logTag);
                error = res;
                return "";
            }
        }
        res= curl_easy_setopt(curlPut,CURLOPT_FOLLOWLOCATION,0);
        if (res != CURLE_OK) {
            error = res;
            return "";
        }
        SPLAPPTRC(L_TRACE,"About to perform",logTag);
        // Now handle read, for error checking and exception handling
        SPL::rstring toReturn;
        res = readResultAsRstring(curlPut,&toReturn);
        if (res != CURLE_OK) {
            error = res;
            return "";
        }

        // ALL DONE!  Do action.
        res = curl_easy_perform(curlPut);
        SPLAPPTRC(L_TRACE,"About to perform",logTag);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error " << res << " on curl_easy_perform", logTag);
            error = res;
            return "";
        }
        error = 0; //all went well
        return toReturn;
    }

    SPL::rstring httpPut(const SPL::rstring &  data, const  SPL::rstring &  url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring &  username, const SPL::rstring & password, SPL::list<SPL::rstring>& headers, SPL::int32 & error) {
        return httpPut_(data, url, extraHeaders, username, password, headers, error, NULL, NULL);
    }
    SPL::rstring httpPut(const SPL::rstring &  data, const  SPL::rstring &  url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring &  username, const SPL::rstring & password, SPL::list<SPL::rstring>& headers, SPL::int32 & error, const SPL::int32 requestTimeout, const SPL::int32 connectionTimeout) {
        return httpPut_(data, url, extraHeaders, username, password, headers, error, &requestTimeout, &connectionTimeout);
    }


    // First pass as curl_get.
    SPL::rstring httpGet_(const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::list<SPL::rstring> * headers, SPL::int32& error, const SPL::int32 * requestTimeout, const SPL::int32 * connectionTimeout) {
        // Curl is more efficient if we let it reuse handles
        static __thread CURL* curlGet = NULL;
        if (curlGet == NULL) {
            curlGet = curl_easy_init();
            if (curlGet == NULL) {
                SPLAPPTRC(L_ERROR, "Error on curl_easy_init", logTag);
                error = -1;
                return "";
            }
            addCurlHandle(curlGet);
        }

        SPL::rstring toReturn;
        CURLcode res;
        // Let's make sure someone didn't forget to initialize before calling
        error = 0;
        res = addCommonOpts(curlGet,url,extraHeaders,username, password, requestTimeout, connectionTimeout);
        if (res != 0 ) {
            error = res;
            return toReturn;
        }
        res = readResultAsRstring(curlGet,&toReturn);
        if (res != CURLE_OK) {
            error = res;
            return toReturn;
        }
        res = curl_easy_setopt(curlGet,CURLOPT_FOLLOWLOCATION,1);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR,"Error " << res << " setting follow location",logTag);
            error = res;
            return toReturn;
        }
        //add header function if headers are requested from the interface
        if (headers) {
            CURLcode res = curl_easy_setopt(curlGet,CURLOPT_HEADERDATA,(void*)headers);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR,"Error code " << res << " setting header data",logTag);
                error = res;
                return "";
            }
            res = curl_easy_setopt(curlGet,CURLOPT_HEADERFUNCTION,&addToHeaderList);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR, "Error code " << res << " setting header function", logTag);
                error = res;
                return "";
            }
        } else {
            res = curl_easy_setopt(curlGet,CURLOPT_HEADERFUNCTION,NULL);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR, "Error code " << res << " setting header function", logTag);
                error = res;
                return "";
            }
            CURLcode res = curl_easy_setopt(curlGet,CURLOPT_HEADERDATA,NULL);
            if (res != CURLE_OK) {
                SPLAPPTRC(L_ERROR,"Error code " << res << " setting header data",logTag);
                error = res;
                return "";
            }
        }
        //perform operation
        res = curl_easy_perform(curlGet);
        if (res != CURLE_OK) {
            SPLAPPTRC(L_ERROR, "Error " << res << " on curl_easy_perform", logTag);
            error = res;
            return toReturn;
        }
        long responseCode = 0;
        curl_easy_getinfo(curlGet,CURLINFO_RESPONSE_CODE,&responseCode);
        if (responseCode != 200) {
            SPLAPPTRC(L_ERROR,"Unexpected response code " << responseCode,logTag);
            error = -1;
        }
        return toReturn;
    }

    SPL::rstring httpGet(const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::int32& error) {
        return httpGet_(url, extraHeaders, username, password, NULL, error, NULL, NULL);
    }
    SPL::rstring httpGet(const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::list<SPL::rstring> & headers, SPL::int32& error) {
        return httpGet_(url, extraHeaders, username, password, &headers, error, NULL, NULL);
    }
    SPL::rstring httpGet(const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::int32 & error, const SPL::int32 requestTimeout, const SPL::int32 connectionTimeout) {
        return httpGet_(url, extraHeaders, username, password, NULL, error, &requestTimeout, &connectionTimeout);
    }
    SPL::rstring httpGet(const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::list<SPL::rstring> & headers, SPL::int32 & error, const SPL::int32 requestTimeout, const SPL::int32 connectionTimeout) {
        return httpGet_(url, extraHeaders, username, password, &headers, error, &requestTimeout, &connectionTimeout);
    }

}

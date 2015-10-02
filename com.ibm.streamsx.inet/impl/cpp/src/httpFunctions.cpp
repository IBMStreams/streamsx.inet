#include "httpFunctions.h"
#include "SPL/Runtime/Common/RuntimeDebug.h"
#include <SPL/Runtime/Type/List.h>
#include <tr1/unordered_map>
#include <vector>
namespace com_ibm_streamsx_inet_http {


typedef std::tr1::unordered_map<SPL::list<SPL::rstring>,struct curl_slist*> HeaderCache;

std::vector<CURL*>* activeCurlPointers = NULL;
const char* logTag = "httpForStreams";

static __thread HeaderCache* headerCache = NULL;

__attribute__((constructor)) void initializeCurl() {
	curl_global_init(CURL_GLOBAL_ALL);
}

__attribute__((destructor)) void cleanupCurl() {

 
    while (activeCurlPointers != NULL && activeCurlPointers->size() > 0 ){
        curl_easy_cleanup(activeCurlPointers->back());
        activeCurlPointers->pop_back();
    }
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
        activeCurlPointers = new std::vector<CURL*>(3);
    }
    activeCurlPointers->push_back(handle);

}


// I'm not sure caching these is actually faster, but it does mean
// that hte calling functions don't have to worry about freeing the memory.

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

const char * nameForPost="fromTuple";
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

// Just put the header as-is into the list.
size_t addToHeaderList(char * buffer, size_t size, size_t nitems, void * userdata) {
	SPL::list<SPL::rstring> * theList = (SPL::list<SPL::rstring>*)userdata;
	SPL::rstring newString(buffer,size*nitems);
	theList->push_back(newString);
	return size*nitems;
}

CURLcode addCommonOpts(CURL * curl, const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password) {

    CURLcode res = curl_easy_setopt(curl,CURLOPT_URL,url.c_str());

    if (res != CURLE_OK) {
            	SPLAPPTRC(L_ERROR, "Error " << res << " setting URL", logTag);
            	return res;
     }

	if (extraHeaders.size() > 0) {
		struct curl_slist * extraHeadersSlist = getSList(extraHeaders);
	    curl_easy_setopt(curl,CURLOPT_HTTPHEADER,extraHeadersSlist);
	    if (res!= CURLE_OK) {
	    	return res;
	    }
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
    return CURLE_OK;
}

class RstringAndIndex {
    public:
    const SPL::rstring * theData;
    size_t numSent;
};

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
        SPLAPPTRC(L_ERROR,"Size is " << size*nitems << " blob size is " << toSend->length(), logTag);
    }
    SPLAPPTRC(L_DEBUG,"sent " << i << " bytes, numSent is " << theStruct->numSent,logTag);
    return i;
}

SPL::rstring httpPost(const SPL::rstring & data, const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::list<SPL::rstring> & headers,SPL::int32 & error) {
    static __thread CURL* curlPost =NULL;
    error = 0;
	// curlPost is defined as a thread-local static variable.

	if (curlPost == NULL) {
		curlPost = curl_easy_init();
        addCurlHandle(curlPost);
	}

    headers.clear();
    CURLcode res = addCommonOpts(curlPost,url,extraHeaders,username, password);
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
    	error = res;
    	return "";
    }
    curl_easy_setopt(curlPost,CURLOPT_POSTFIELDSIZE,data.length());
    if (res!= CURLE_OK) {
    	error = res;
    	return "";
    }



    // Now handle read, for error checking and exception handling
    SPL::rstring toReturn;
    res = curl_easy_setopt(curlPost,CURLOPT_WRITEDATA,&toReturn);
    if (res != CURLE_OK) {
    	SPLAPPTRC(L_ERROR, "Error " << res << "setting data pointer", logTag);
    	error =res;
    	return "";
    }
    res = curl_easy_setopt(curlPost,CURLOPT_WRITEFUNCTION,&(populate_rstring));
    if (res != CURLE_OK) {
    	SPLAPPTRC(L_ERROR, "Error " << res << " setting write function", logTag);
    	error = res;
    	return "";
    }

    curl_easy_setopt(curlPost,CURLOPT_FOLLOWLOCATION,0);
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



SPL::rstring httpPut(const SPL::rstring & data, const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::list<SPL::rstring> & headers, SPL::int32 & error) {
    static __thread CURL* curlPut = NULL;
	// curlPut is a thread-local static variable.

	if (curlPut == NULL) {
    	curlPut = curl_easy_init();
        addCurlHandle(curlPut);
    }
    headers.clear();
    addCommonOpts(curlPut,url,extraHeaders,username, password);
    CURLcode res = curl_easy_setopt(curlPut,CURLOPT_HEADERDATA,(void*)&headers);
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
    res = curl_easy_setopt(curlPut,CURLOPT_WRITEDATA,&toReturn);
    if (res != CURLE_OK) {
    	SPLAPPTRC(L_ERROR, "Error " << res << "setting data pointer", logTag);
    	error =res;
    	return "";
    }
    res = curl_easy_setopt(curlPut,CURLOPT_WRITEFUNCTION,&(populate_rstring));
    if (res != CURLE_OK) {
    	SPLAPPTRC(L_ERROR, "Error " << res << " setting write function", logTag);
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
    return toReturn;
}


// First pass as curl_get.
SPL::rstring httpGet(const SPL::rstring & url, const SPL::list<SPL::rstring> & extraHeaders, const SPL::rstring & username, const SPL::rstring & password, SPL::int32& error) {

    // Curl is more efficient if we let it reuse handles
    static __thread CURL* curlGet = NULL;
	if (curlGet == NULL) {
    	curlGet = curl_easy_init();
        addCurlHandle(curlGet);
    }
    SPL::rstring toReturn;
    CURLcode res;
    // Let's make sure someone didn't forget to initialize before calling
    error = 0;
    if (curlGet) {
    	res = addCommonOpts(curlGet,url,extraHeaders,username, password);
    	if (res != 0 ) {
    		error = res;
    		return toReturn;
    	}
        res = curl_easy_setopt(curlGet,CURLOPT_WRITEDATA,&toReturn);
        if (res != CURLE_OK) {
        	SPLAPPTRC(L_ERROR, "Error " << res << "setting data pointer", logTag);
        	error = res;
        	return toReturn;
        }
        res = curl_easy_setopt(curlGet,CURLOPT_WRITEFUNCTION,&(populate_rstring));
        if (res != CURLE_OK) {
        	SPLAPPTRC(L_ERROR, "Error " << res << " setting write function", logTag);
        	error = res;
        	return toReturn;
        }
        res = curl_easy_setopt(curlGet,CURLOPT_FOLLOWLOCATION,1);
        if (res != CURLE_OK) {
        	SPLAPPTRC(L_ERROR,"Error " << res << " setting follow location",logTag);
        	error = res;
        	return toReturn;
        }
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
    }
    return toReturn;
}

}

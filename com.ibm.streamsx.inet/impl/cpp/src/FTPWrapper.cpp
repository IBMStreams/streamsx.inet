/* Copyright (C) 2013-2014, International Business Machines Corporation */
/* All Rights Reserved */

#include "FTPWrapper.h"
#include <SPL/Runtime/Type/Timestamp.h>
#include <SPL/Runtime/Function/SPLFunctions.h>
#include <SPL/Runtime/Common/RuntimeException.h>
#include <cstdio>
#include <iostream>
#include <sys/stat.h>
#include <openssl/crypto.h>

namespace com { namespace ibm { namespace streamsx { namespace inet { namespace ftp {
using namespace SPL;

/***************************************************************
 *               static initializations                        *
 ***************************************************************/
bool FTPWrapper::asyncDnsSupport(false);
int FTPWrapper::wrapperCount(0);
pthread_mutex_t * FTPWrapper::ssllocks(NULL);
FTPWrapper::Initializer Init;

/**************************************************************
 *                  Wrapper Initializer                       *
 * Makes the global non thread save initialization of curl lib*
 **************************************************************/
FTPWrapper::Initializer::Initializer() {
	//ssl thread support

	CURLcode res = curl_global_init(CURL_GLOBAL_DEFAULT);
	if (CURLE_OK != res) {
		THROW(SPL::SPLRuntime, "FTPWrapper initialization error - global curl error");
	} else {
		SPLAPPTRC(L_INFO, "Work with Curl version:" << curl_version(), "FTPWrapper");
		std::cout << "FTPWrapper: Work with Curl version:" << curl_version() << std::endl;
	}
	curl_version_info_data * dat =  curl_version_info(CURLVERSION_NOW);
	if (dat->ssl_version) SPLAPPTRC(L_INFO, "curl_version_info ssl_version=" << dat->ssl_version, "FTPWrapper");
	std::cout << "FTPWrapper: curl_version_info ssl_version=" << dat->ssl_version << std::endl;
	if (dat->libssh_version) SPLAPPTRC(L_INFO, "libssh_version=" << dat->libssh_version, "FTPWrapper");
	std::cout << "FTPWrapper: libssh_version=" << dat->libssh_version << std::endl;
	//printf("protocols=%s\n", dat->protocols);
	if (dat->features & CURL_VERSION_ASYNCHDNS) asyncDnsSupport = true; else asyncDnsSupport = false;
	SPLAPPTRC(L_INFO, "Async DNS supported="<< asyncDnsSupport, "FTPWrapper");
	std::cout << "FTPWrapper: Async DNS supported="<< asyncDnsSupport << std::endl;

	//initialize ssl lock array
	void (*fp)(int, int, const char*, int) = CRYPTO_get_locking_callback();
	if (fp) SPLAPPTRC(L_ERROR, "There is already ssh lock call back function registered", "FTPWrapper");
	ssllocks = (pthread_mutex_t *)OPENSSL_malloc(CRYPTO_num_locks() * sizeof(pthread_mutex_t));
	for (int i=0; i < CRYPTO_num_locks(); i++) {
		pthread_mutex_init(&(ssllocks[i]),NULL);
	}
	//CRYPTO_set_id_callback(threadId);
	CRYPTO_set_locking_callback(lockCallback);
}

FTPWrapper::Initializer::~Initializer() {
	curl_global_cleanup();
	CRYPTO_set_locking_callback(NULL);
	//CRYPTO_set_id_callback(NULL);
	for (int i=0; i <CRYPTO_num_locks(); i++) {
		pthread_mutex_destroy(&(ssllocks[i]));
	}
	OPENSSL_free(ssllocks);
	ssllocks = NULL;
}

/**************************************************************
 *              ssh locking callbacks                         *
 **************************************************************/

void FTPWrapper::lockCallback(int mode, int ind, const char *file, int line) {
	if (mode & CRYPTO_LOCK) {
		pthread_mutex_lock(&(ssllocks[ind]));
	}
	else {
		pthread_mutex_unlock(&(ssllocks[ind]));
	}
}
/*unsigned long FTPWrapper::threadId() {
	unsigned long ret;
	ret=pthread_self();
	return ret;
}*/


/**************************************************************
 *                       FTPWrapper                           *
 **************************************************************/
//constructor
//Construction and Destruction must not be executed in a multithreading environment
FTPWrapper::FTPWrapper(
				CloseConnectionMode connectionCloseMode_,
				TransmissionProtocolLiteral protocol_,
				bool verbose_,
				CreateMissingDirs createMissingDirs_,
				SPL::rstring const & debugAspect_) :
	closeConnectionMode(connectionCloseMode_),
	protocol(protocol_),
	verbose(verbose_),
	createMissingDirs(createMissingDirs_),
	schema(),
	url(),
	host(),
	path(),
	usernameReceived(false),
	username(),
	passwordReceived(false),
	password(),
	urlChange(true),
	credentialChange(false),
	userpasswd(),

	connectionTimeout(0),
	transferTimeout(0),
	connectionTimeoutReceived(false),
	transferTimeoutReceived(false),

	curl(NULL),
	res(CURLE_OK),
	shutdown(false),

	noTransfers(0),
	noTransferFailures(0),
	error(),
	action(),
	debugAspect("FTPWrapper," + debugAspect_)
{
	SPLAPPTRC(L_DEBUG, "Construct FTPWrapper with params: CloseConnectionMode connectionCloseMode=" << connectionCloseMode_ <<
													" TransmissionProtocolLiteral=" << toString(protocol) <<
													" verbose=" << verbose_,
			debugAspect);

	wrapperCount++;
	SPLAPPTRC(L_DEBUG, "wrapperCount=" << wrapperCount, "FTPWrapper");
	//make the schema
	switch (protocol) {
	case ftp:
	case ftpSSLTry:
	case ftpSSLControl:
	case ftpSSLAll:
		schema = "ftp://";
		break;
	case ftps:
		schema = "ftps://";
		break;
	case sftp:
		schema = "sftp://";
		break;
	}
	//initialize the curl lib is done in static Init
}

//destructor
FTPWrapper::~FTPWrapper() {
	SPLAPPTRC(L_DEBUG, "Destruct FTPWrapper", debugAspect);
	deInitialize();
	wrapperCount--;
	//curl cleanup in static Init
}

//more set & get
void FTPWrapper::setHost(SPL::rstring const & val) {
	if (host != val) {
		host = val;
		urlChange = true;
	}
}
void FTPWrapper::setPath(SPL::rstring const & val) {
	SPL::rstring temp;
	if (val[0] != '/') temp = "/";
	temp.append(val);
	if (path != temp) {
		path = temp;
		urlChange = true;
	}
}
void FTPWrapper::setFilename(SPL::rstring const & val) {
	if (filename != val) {
		filename = val;
		urlChange = true;
	}
}
void FTPWrapper::setUsername(SPL::rstring const & val) {
	if ((! usernameReceived) ||  (username != val)) {
		username = val;
		credentialChange = true;
		usernameReceived = true;
	}
}
void FTPWrapper::setPassword(SPL::rstring const & val) {
	if ((! passwordReceived) ||  (password != val)) {
		password = val;
		credentialChange = true;
		passwordReceived = true;
	}
}

void FTPWrapper::setConnectionTimeout(uint32_t val) {
	if (val != connectionTimeout) {
		connectionTimeout = val;
		connectionTimeoutReceived = true;
	}
}

void FTPWrapper::setTransferTimeout(uint32_t val) {
	if (val != transferTimeout) {
		transferTimeout = val;
		transferTimeoutReceived = true;
	}
}

//get handle if null
//start with all common initialization actions
//from descendant initialize
void FTPWrapper::initialize() {
	if(!curl) {

		SPLAPPTRC(L_DEBUG, "FTPWrapper::initialize initialize curl lib", debugAspect);
		curl = curl_easy_init(); //first init action set result
		//std::cout << curl << std::endl;
		if (curl) {
			res = CURLE_OK;
			if (verbose) {
				action = "CURLOPT_VERBOSE";
				res = curl_easy_setopt(curl, CURLOPT_VERBOSE, 1L);
			}
			//ssl options
			if (protocol != ftp) {
				if (CURLE_OK == res) {
					action = "CURLOPT_SSL_VERIFYPEER";
					curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
				}
				if (CURLE_OK == res) {
					action = "CURLOPT_SSL_VERIFYHOST";
					curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
				}
			}
			if (CURLE_OK == res) {
				curl_ftpssl sslOption; 
				switch (protocol) {
				case ftpSSLTry:
					sslOption = CURLFTPSSL_TRY;
					break;
				case ftpSSLControl:
					sslOption = CURLFTPSSL_CONTROL;
					break;
				case ftpSSLAll:
					sslOption = CURLFTPSSL_ALL;
					break;
				default:
					sslOption = CURLFTPSSL_NONE;
				}
				if (sslOption != CURLFTPSSL_NONE) {
					action = "CURLOPT_FTP_SSL";
					curl_easy_setopt(curl, CURLOPT_FTP_SSL, sslOption);
				}
			}
			if (CURLE_OK == res) {
				long crmd = 0;
				if (createMissingDirs == create) crmd = 1;
				//if (createMissingDirs_ == retry) crmd = 2;
				action = "CURLOPT_FTP_CREATE_MISSING_DIRS";
				curl_easy_setopt(curl, CURLOPT_FTP_CREATE_MISSING_DIRS, crmd);
			}
		} else {
			THROW(SPL::SPLRuntime, "FTPWrapper initialization error - can not get handle");
		}
	}
}

//de-initialize the lib
void FTPWrapper::deInitialize() {
	if (curl) {
		SPLAPPTRC(L_DEBUG, "de-initialize curl", debugAspect);
		curl_easy_cleanup(curl);
		credentialChange = true;
		urlChange = true;
	}
	curl = NULL;
}

// Notify pending shutdown
void FTPWrapper::prepareToShutdown() {
	// This is an asynchronous call
	shutdown = true;
	SPLAPPTRC(L_INFO, "prepareToShutdown()", debugAspect);
}

// Notification window punct
void FTPWrapper::onPunct() {
	if (punct == closeConnectionMode) {
		deInitialize();
	}
}

//perform operation - must be called from descendant perform
//make common initializations
bool FTPWrapper::perform() {
	//res = CURLE_OK;
	if ((protocol == sftp) || (passwordReceived && usernameReceived)) {

		//write new options if hostPath or credential changed
		if (urlChange) {
			if (host.empty() || path.empty()) {
				THROW(SPL::SPLRuntime, "FTPWrapper empty host or path! filename=" << filename);
			}
			SPL::rstring hostPath_ = host + path;
			if (! filename.empty()) { //if there is a filepart to append
				if ('/' != hostPath_[hostPath_.size() - 1]) {
					hostPath_.append("/");
				}
				url = schema + hostPath_ + filename;
			} else {
				url = schema + hostPath_;
			}
			SPLAPPTRC(L_DEBUG, "urlChange url:" << url, debugAspect);
			action = "CURLOPT_URL";
			res = curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
			if (CURLE_OK == res) {
				urlChange = false;
			}
		}
		if (credentialChange && (CURLE_OK == res)) {
			action ="CURLOPT_USERPWD";
			userpasswd = username + ":" + password;
			SPLAPPTRC(L_DEBUG, "credentialChange : " << username << ":*****", debugAspect);
			res = curl_easy_setopt(curl, CURLOPT_USERPWD, userpasswd.c_str());
			if (CURLE_OK == res) {
				credentialChange = false;
			}
		}
		//CURLOPT_NOSIGNAL must be set if more than one ftp operator are fused in one pe an no dynamic dns support is available
		//otherwise we will see aborts
		if ((wrapperCount > 1) && (! asyncDnsSupport)) {
			if ((connectionTimeoutReceived || transferTimeoutReceived) && (CURLE_OK == res)) {
				action = "CURLOPT_NOSIGNAL";
				long nosig = 1;
				SPLAPPTRC(L_DEBUG, "set CURLOPT_NOSIGNAL = 1", debugAspect);
				res = curl_easy_setopt(curl, CURLOPT_NOSIGNAL, nosig);
			}
		}
		if (connectionTimeoutReceived && (CURLE_OK == res)) {
			action = "CURLOPT_CONNECTTIMEOUT";
			SPLAPPTRC(L_DEBUG, "connectionTimeout change : " << connectionTimeout, debugAspect);
			res = curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, connectionTimeout);
			if (CURLE_OK == res) {
				connectionTimeoutReceived = false;
			}
		}
		if (transferTimeoutReceived && (CURLE_OK == res)) {
			action = "CURLOPT_TIMEOUT";
			SPLAPPTRC(L_DEBUG, "transferTimeout change : " << transferTimeout, debugAspect);
			res = curl_easy_setopt(curl, CURLOPT_TIMEOUT, transferTimeout);
			if (CURLE_OK == res) {
				transferTimeoutReceived = false;
			}
		}
		if (CURLE_OK != res) {
			THROW(SPL::SPLRuntime, "FTPReaderWrapper initialization error action " << action << ", curl says: " << curl_easy_strerror(res));
		}

		//perform operation
		SPLAPPTRC(L_TRACE, "Perform FTP action url=" << url, debugAspect);
		noTransfers++;
		res = curl_easy_perform(curl);

		bool result;
		if (CURLE_OK == res) {
			SPLAPPTRC(L_DEBUG, "operation well performed", debugAspect);
			error = "";
			result = true;
		} else {
			noTransferFailures++;
			resultCodeForCOF = res;
			error = "libcurl perform operation failed! curl says: ";
			error+= curl_easy_strerror(res);
			result = false;
		}

		if (ever == closeConnectionMode) {
			deInitialize();
		}
		return result;
	} else {
		error = "No password received but tuple received";
		return false;
	}

}

/**************************************************************
 *                 FTPTransportWrapper                        *
 **************************************************************/
//constructor
FTPTransportWrapper::FTPTransportWrapper(
						CloseConnectionMode closeConnectionMode_,
						TransmissionProtocolLiteral protocol_,
						bool verbose_,
						CreateMissingDirs createMissingDirs_,
						SPL::rstring const & debugAspect_,
						bool useEPSV_,
						bool useEPRT_,
						/*bool usePRET_, */
						bool skipPASVIp_) :
	FTPWrapper::FTPWrapper(closeConnectionMode_, protocol_, verbose_, createMissingDirs_, debugAspect_),
	useEPSV(useEPSV_),
	useEPRT(useEPRT_),
	//usePRET;
	usePORT(),
	skipPASVIp(skipPASVIp_),
	noBytesTransferred(0),
	noBytesTransferredTemp(0),
	transferSpeed(0)
{
	SPLAPPTRC(L_DEBUG, "Construct FTPTransportWrapper with params: useEPSV=" <<  useEPSV_ <<
															" useEPRT=" <<  useEPRT_ <<
															" skipPASVIp=" << skipPASVIp_,
			debugAspect);
}

//initialize to be called from the descendant
void FTPTransportWrapper::initialize() {
	if (!curl) {	//initialize only once
		FTPWrapper::initialize();
		if (curl) {
			SPLAPPTRC(L_DEBUG, "FTPTransportWrapper::initialize initialize curl lib", debugAspect);
			//set ftp options
			long val;
			if (CURLE_OK == res) {
				val = useEPSV;
				action = "CURLOPT_FTP_USE_EPSV";
				curl_easy_setopt(curl, CURLOPT_FTP_USE_EPSV, val);
			}
			if (CURLE_OK == res) {
				val = useEPRT;
				action ="CURLOPT_FTP_USE_EPRT";
				curl_easy_setopt(curl, CURLOPT_FTP_USE_EPRT, val);
			}
			/*if (CURLE_OK == res) {
				val = <%=$usePRET%>;
				action = "CURLOPT_FTP_USE_PRET";
				curl_easy_setopt(curl, CURLOPT_FTP_USE_PRET, val);
			}*/
			if (CURLE_OK == res) {
				val = useEPSV;
				action = "CURLOPT_FTP_USE_EPSV";
				curl_easy_setopt(curl, CURLOPT_FTP_USE_EPSV, val);
			}
			if (CURLE_OK == res) {
				val = skipPASVIp;
				action = "CURLOPT_FTP_SKIP_PASV_IP";
				curl_easy_setopt(curl, CURLOPT_FTP_SKIP_PASV_IP, val);
			}
		}
	}
}

//init the transport part - to be called from the descendant
bool FTPTransportWrapper::perform() {
	transferSpeed = 0;
	if (! usePORT.empty()) {
		action ="CURLOPT_FTPPORT";
		SPLAPPTRC(L_DEBUG, "set  CURLOPT_FTPPORT: " << usePORT, debugAspect);
		curl_easy_setopt(curl, CURLOPT_FTPPORT, usePORT.c_str());
	}
	if (CURLE_OK == res) {
		noBytesTransferredTemp = 0;
		const SPL::timestamp startTime = SPL::Functions::Time::getTimestamp();

		bool result = FTPWrapper::perform();
		if (result) {
			noBytesTransferred += noBytesTransferredTemp;
			const SPL::timestamp endTime = SPL::Functions::Time::getTimestamp();
			SPL::float64 diff = SPL::Functions::Time::diffAsSecs(endTime, startTime);
			transferSpeed = noBytesTransferredTemp / diff;
		}
		return result;
	} else {
		THROW(SPL::SPLRuntime, "FTPReaderWrapper initialization error action " << action << ", curl says:" << curl_easy_strerror(res));
	}
}

/**************************************************************
 *                 FTPReaderWrapper                           *
 **************************************************************/
//constructor
FTPReaderWrapper::FTPReaderWrapper(
					CloseConnectionMode closeConnectionMode_,
					TransmissionProtocolLiteral protocol_,
					bool verbose_,
					CreateMissingDirs createMissingDirs_,
					SPL::rstring const & debugAspect_,
					bool useEPSV_,
					bool useEPRT_,
					/*bool usePRET_, */
					bool skipPASVIp_,
					void * op_,
					Callback cb_) :
	FTPTransportWrapper(closeConnectionMode_, protocol_, verbose_, createMissingDirs_, debugAspect_, useEPSV_, useEPRT_, /*bool usePRET_, */ skipPASVIp_),
	op(op_),
	operatorCallback(cb_)
{
	SPLAPPTRC(L_DEBUG, "Construct FTPReaderWrapper", debugAspect);
}

//the outer initialize to be called from perform throws in case of error
void FTPReaderWrapper::initialize() {
	res = CURLE_OK;
	if (! curl) {
		FTPTransportWrapper::initialize();
		if (curl) {
			SPLAPPTRC(L_DEBUG, "FTPReaderWrapper::initialize initialize curl lib", debugAspect);
			if (CURLE_OK == res) {
				action ="CURLOPT_WRITEFUNCTION";
				res = curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, callback);
			}
			if (CURLE_OK == res) {
				action = "CURLOPT_WRITEDATA";
				res = curl_easy_setopt(curl, CURLOPT_WRITEDATA, this);
			}
			if (CURLE_OK != res) {
				THROW(SPL::SPLRuntime, "FTPReaderWrapper initialization error action " << action << ", curl says:" << curl_easy_strerror(res));
			}
		}
	}
}

//the public perform
bool FTPReaderWrapper::perform() {
	initialize(); //sets res to ok
	return FTPTransportWrapper::perform();
}

//the write call back
size_t FTPReaderWrapper::writeCallback(void * buffer, size_t size, size_t count) {
	SPLAPPTRC(L_TRACE, "must write size=" << size << " count=" << count << " bytes", debugAspect);
	size_t result;
	if (shutdown) { //abort transfer in case of shutdown
		result = size * count;
		result--; //make sure the return differs
		SPLAPPTRC(L_ERROR, "Abort ftp read: url: " << url, debugAspect);
	} else {
		result = (*operatorCallback) (buffer, size, count, op);
		SPLAPPTRC(L_TRACE, "wrote=" << result << " bytes", debugAspect);
		addNoBytesTransferredTemp(result);
	}
	return result;
}

size_t FTPReaderWrapper::callback(void * buffer, size_t size, size_t count, void * stream) {
	FTPReaderWrapper * myself = static_cast<FTPReaderWrapper *>(stream);
	return myself->writeCallback(buffer, size, count);
}


/****************************************************************
 * the FTP libcurl wrapper for put file transport               *
 ****************************************************************/
//constructor
FTPPutFileWrapper::FTPPutFileWrapper(
					CloseConnectionMode closeConnectionMode_,
					TransmissionProtocolLiteral protocol_,
					bool verbose_,
					CreateMissingDirs createMissingDirs_,
					SPL::rstring const & debugAspect_,
					bool useEPSV_,
					bool useEPRT_,
					/*bool usePRET_, */
					bool skipPASVIp_) :
	FTPTransportWrapper(closeConnectionMode_, protocol_, verbose_, createMissingDirs_, debugAspect_, useEPSV_, useEPRT_, /*bool usePRET_, */ skipPASVIp_),
	localFilename(),
	renameTo(),
	fd(NULL),
	headerList(NULL),
	fsize(0)
{
	SPLAPPTRC(L_DEBUG, "Construct FTPPutFileWrapper", debugAspect);
}

//the outer initialize to be called from perform throws in case of error
void FTPPutFileWrapper::initialize() {
	res = CURLE_OK;
	if (! curl) {
		FTPTransportWrapper::initialize();
		if (curl) {
			SPLAPPTRC(L_DEBUG, "FTPPutFileWrapper::initialize initialize curl lib", debugAspect);
			if (CURLE_OK == res) {
				action ="CURLOPT_READFUNCTION";
				res = curl_easy_setopt(curl, CURLOPT_READFUNCTION, callback);
			}
			if (CURLE_OK == res) {
				action = "CURLOPT_READDATA";
				res = curl_easy_setopt(curl, CURLOPT_READDATA, this);
			}
			if (CURLE_OK == res) {
				action = "CURLOPT_UPLOAD";
				curl_easy_setopt(curl, CURLOPT_UPLOAD, 1L);
			}
			if (CURLE_OK != res) {
				THROW(SPL::SPLRuntime, "FTPReaderWrapper initialization error action " << action << ", curl says:" << curl_easy_strerror(res));
			}
		}
	}
}

//the public perform
//set the file size to curl - open the file
bool FTPPutFileWrapper::perform() {
	if (fd) { //the file should be always closed
		THROW(SPL::SPLRuntime, "FTPPutFileWrapper logic error fd not NULL");
	}
	bool result = false;
	fsize = 0;
	initialize(); //set res to ok

	//make the headers
	headerList = NULL;
	if (! renameTo.empty()) {
		if (sftp == protocol) {
			SPL::rstring path_ = path;
			if ('/' != path_[path_.size() - 1]) {
				path_.append("/");
			}
			SPL::rstring ftpCommand = "rename " + path_ + filename + " " + path_ + renameTo;
			SPLAPPTRC(L_DEBUG, "Apend header " << ftpCommand, debugAspect);
			headerList = curl_slist_append(headerList, ftpCommand.c_str());
		} else {
			SPL::rstring ftpCommand = "RNFR " + filename;
			SPLAPPTRC(L_DEBUG, "Apend header " << ftpCommand, debugAspect);
			headerList = curl_slist_append(headerList, ftpCommand.c_str());
			ftpCommand = "RNTO " + renameTo;
			SPLAPPTRC(L_DEBUG, "Apend header " << ftpCommand, debugAspect);
			headerList = curl_slist_append(headerList, ftpCommand.c_str());
		}
		res = curl_easy_setopt(curl, CURLOPT_POSTQUOTE, headerList);
		if (CURLE_OK != res) {
			THROW(SPL::SPLRuntime, "FTPPutFileWrapper initialization error action CURLOPT_POSTQUOTE, curl says: " << curl_easy_strerror(res));
		}
	}

	//open local file
	struct stat file_info;
	if ( ! stat(localFilename.c_str(), &file_info)) {
		fsize = (curl_off_t)file_info.st_size;
		fd = fopen(localFilename.c_str(), "rb");
		if (fd) {

			//filesize to curl
			curl_easy_setopt(curl, CURLOPT_INFILESIZE_LARGE, fsize);
			if (CURLE_OK != res) {
				THROW(SPL::SPLRuntime, "FTPPutFileWrapper initialization error action CURLOPT_INFILESIZE_LARGE, curl says: " << curl_easy_strerror(res));
			}
			//perform operation
			result = FTPTransportWrapper::perform();

			//close file
			fclose(fd);
			fd = NULL;
			if (result) {
				uint64_t x = fsize;
				if (x != noBytesTransferredTemp) {
					SPLAPPTRC(L_ERROR, "unequal size fsize=" << fsize << " noBytesTransferredTemp=" << noBytesTransferredTemp, debugAspect);
				}
			}
		} else {
			error = "can not open file: " + localFilename;
			SPLAPPTRC(L_ERROR, error, debugAspect);
		}
	} else {
		error = "can not open file: " + localFilename + " errno=" + strerror(errno);
		SPLAPPTRC(L_ERROR, error, debugAspect);
	}
	//clean up header
	if (headerList) {
		res = curl_easy_setopt(curl, CURLOPT_POSTQUOTE, NULL);
		if (CURLE_OK != res) {
			THROW(SPL::SPLRuntime, "FTPPutFileWrapper clear header error action CURLOPT_POSTQUOTE, curl says: " << curl_easy_strerror(res));
		}
		curl_slist_free_all(headerList);
	}
	return result;
}


//the read callback
size_t FTPPutFileWrapper::readCallback(void *ptr, size_t size, size_t nmemb) {
	SPLAPPTRC(L_TRACE, "can read size:" << size << " * nmemb:" << nmemb << "=" << (size * nmemb) << " bytes from file: " << localFilename, debugAspect);
	size_t retcode;
	if (shutdown) { //abort transfer in case of shutdown
		retcode = CURL_READFUNC_ABORT;
		SPLAPPTRC(L_ERROR, "Abort ftp Put: url: " << url << " localFilename:" << localFilename << " renameTo:" << renameTo, debugAspect);
	} else {
		retcode = fread(ptr, size, nmemb, fd);
		SPLAPPTRC(L_TRACE, "read " << retcode << " bytes from file: " << localFilename, debugAspect);
		addNoBytesTransferredTemp(retcode);
	}
	return retcode;
}

//the static call back
size_t FTPPutFileWrapper::callback(void *ptr, size_t size, size_t nmemb, void *stream) {
	FTPPutFileWrapper * myself = static_cast<FTPPutFileWrapper *>(stream);
	return myself->readCallback(ptr, size, nmemb);
}

/****************************************************************
 * the FTP libcurl wrapper for command execution                *
 ****************************************************************/
FTPCommandWrapper::FTPCommandWrapper(	CloseConnectionMode
										closeConnectionMode_,
										TransmissionProtocolLiteral protocol_,
										bool verbose_,
										CreateMissingDirs createMissingDirs_,
										SPL::rstring const & debugAspect_) :
	FTPWrapper(closeConnectionMode_, protocol_, verbose_, createMissingDirs_, debugAspect_),
	commandLiteral(none),
	arg1(),
	arg2(),
	result(),
	headerList(NULL)
{
	SPLAPPTRC(L_DEBUG, "Construct FTPCommandWrapper", debugAspect);
}

void FTPCommandWrapper::initialize() {
	res = CURLE_OK;
	if (! curl) {
		FTPWrapper::initialize();
		if (curl) {
			SPLAPPTRC(L_DEBUG, "FTPCommandWrapper::initialize initialize curl lib", debugAspect);
			if (CURLE_OK == res) {
				action ="CURLOPT_HEADERFUNCTION";
				res = curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, callback);
			}
			if (CURLE_OK == res) {
				action = "CURLOPT_WRITEHEADER";
				res = curl_easy_setopt(curl, CURLOPT_WRITEHEADER, this);
			}
			if (CURLE_OK == res) {
				action = "CURLOPT_NOBODY";
				res = curl_easy_setopt(curl, CURLOPT_NOBODY, 1L);
			}
			if (CURLE_OK != res) {
				THROW(SPL::SPLRuntime, "FTPCommandWrapper initialization error action " << action << ", curl says:" << curl_easy_strerror(res));
			}
		}
	}
}

//the public perform
bool FTPCommandWrapper::perform() {
	bool result = true;
	initialize(); //set res to ok

	//prepare the command header
	SPL::rstring c1;
	SPL::rstring c2;
	SPL::rstring path_;
	if (sftp == protocol) {
		path_ = path;
		if ('/' != path_[path_.size() - 1]) {
			path_.append("/");
		}
	}
	switch (commandLiteral) {
	case none :
		result = false;
		error = "FTPCommandWrapper no command received";
		break;
	case del :
	case rm :
		if (arg1.empty()) {
			error = "Command delete must have a non-empty filename / arg1";
			result = false;
		} else {
			if (sftp == protocol) {
				c1 = "rm " + path_ + arg1;
			} else {
				c1 = "DELE " + arg1;
			}
		}
		break;
	case rmdir :
		if (arg1.empty()) {
			error = "Command rmdir must have a non-empty filename / arg1";
			result = false;
		} else {
			if (sftp == protocol) {
				c1 = "rmdir " + path_ + arg1;
			} else {
				c1 = "RMD " + arg1;
			}
		}
		break;
	case mkdir :
		if (arg1.empty()) {
			error = "Command mkdir must have a non-empty filename / arg1";
			result =false;
		} else {
			if (sftp == protocol) {
				c1 = "mkdir " + path_ + arg1;
			} else {
				c1 = "MKD " + arg1;
			}
		}
		break;
	case rename :
		if ((arg1.empty()) || arg2.empty()) {
			error = "Command rename must have a non-empty arg1 and arg2";
			result = false;
		} else {
			if (sftp == protocol) {
				c1 = "rename " + path_ + arg1 + " " + path_ + arg2;
			} else {
				c1 = "RNFR " + arg1;
				c2 = "RNTO " + arg2;
			}
		}
		break;
	case modificationTime :
	case modtime :
		if (arg1.empty()) {
			error = "Command modificationTime must have a non-empty filename / arg1";
			result =false;
		} else {
			if (sftp == protocol) {
				error = "Command modificationTime is not valid for sftp";
				result = false;
			} else {
				c1 = "MDTM " + arg1;
			}
		}
		break;
	case pwd :
		if (sftp == protocol) {
			c1 = "pwd";
		} else {
			c1 = "PWD";
		}
	}

	//make the headers
	if (result) {
		headerList = NULL;
		headerList = curl_slist_append(headerList, c1.c_str());
		if (!c2.empty()) {
			headerList = curl_slist_append(headerList, c2.c_str());
		}
		res = curl_easy_setopt(curl, CURLOPT_POSTQUOTE, headerList);

		//perform
		if (CURLE_OK == res) {
			result = FTPWrapper::perform();
		} else {
			THROW(SPL::SPLRuntime, "FTPPutFileWrapper initialization error action CURLOPT_POSTQUOTE, curl says: " << curl_easy_strerror(res));
		}

		//clean up header
		res = curl_easy_setopt(curl, CURLOPT_POSTQUOTE, NULL);
		if (CURLE_OK != res) {
			THROW(SPL::SPLRuntime, "FTPPutFileWrapper clear header error action CURLOPT_POSTQUOTE, curl says: " << curl_easy_strerror(res));
		}
		if (headerList) {
			curl_slist_free_all(headerList);
		}
	}
	return result;
}

//the callback function for the curl lib
size_t FTPCommandWrapper::headerCallback(void * buffer, size_t size, size_t count) {
	SPLAPPTRC(L_TRACE, "header has=" << size << " count=" << count << " bytes", debugAspect);
	size_t l = size * count;
	result.clear(); //do not abort commands. Goodbye is in any case executed during shutdown
	char const * cp = static_cast<char const *>(buffer);
	result.append(cp, l);
	SPLAPPTRC(L_DEBUG, "ftp command returns:" << result, debugAspect);
	return l;
}

//the callback function for the curl lib
size_t FTPCommandWrapper::callback(void * buffer, size_t size, size_t count, void * data) {
	FTPCommandWrapper * myself = static_cast<FTPCommandWrapper *>(data);
	return myself->headerCallback(buffer, size, count);
}

char const * FTPCommandWrapper::commandLiteralString[] = {"none", "del", "rm", "rmdir" ,"mkdir", "rename", "modificationTime", "modtime", "pwd"};

void FTPCommandWrapper::setCommand(SPL::rstring const & val) {
	const int s = sizeof(commandLiteralString) / sizeof(char const *);
	int x = 0;
	while ((x < s) && (0 != val.compare(commandLiteralString[x]))) {
		x++;
	}
	if (x >= s) {
		THROW(SPL::SPLRuntime, "Wrong CommandLiteralString: " << val);
	}
	commandLiteral = static_cast<CommandLiteral>(x);
}

//transformations
char const * FTPWrapper::toString(TransmissionProtocolLiteral protocol) {
	return protocolString[protocol];
}
char const * FTPWrapper::toString(CloseConnectionMode mode) {
	return closeConnectionModeString[mode];
}

char const * FTPWrapper::protocolString[] = {"ftp", "ftpSSLAll", "ftpSSLControl", "ftpSSLTry", "ftps", "sftp"};
char const * FTPWrapper::closeConnectionModeString[] = {"never", "ever", "punct"};

}}}}} //namespace

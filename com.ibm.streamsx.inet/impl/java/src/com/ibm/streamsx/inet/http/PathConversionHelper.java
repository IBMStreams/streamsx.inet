package com.ibm.streamsx.inet.http;

import java.io.File;
import java.net.URI;

public class PathConversionHelper {

        // This method is trying to converting targetFilePath to a absolute path if it is relative path
        // by combining basePath with it.
        public static String convertToAbsPath(URI baseURI, String path) throws Exception {

                // if path is either null or empty, we return it
                // because nothing to convert
                if(path == null || path.isEmpty()) {
                        return path;
                }

                File absFile = new File(baseURI.resolve(path));

                return absFile.getCanonicalPath();

        }
}


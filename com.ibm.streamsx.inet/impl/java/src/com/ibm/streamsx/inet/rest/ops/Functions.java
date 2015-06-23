package com.ibm.streamsx.inet.rest.ops;

import org.eclipse.jetty.util.security.Password;

import com.ibm.streams.function.model.Function;

public class Functions {
    
    @Function(description="Obfuscate a password for an operator in this namespace."
            + "If the password is starts with `OBF:` then it is assumed to be "
            + "already obfuscated and input is returned unchanged. This allows"
            + "external tools to pass submission time values that are already "
            + "obfuscated. The Eclipse Jetty class `org.eclipse.jetty.util.security.Password` "
            + "is the underlying utility.")
    
    public static String obfuscate(String password) {
        if (password.startsWith(Password.__OBFUSCATE))
            return password;

        return Password.obfuscate(password);
    }
}

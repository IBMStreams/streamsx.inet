/**
 * 
 */
package com.ibm.streamsx.inet.test.httptestserver;

import java.util.Collections;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * @author joergboe
 *
 */
public class HTTPTestServer {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		
		int httpPort = 8097;
		int httpsPort = 1443;
		
		Server server = new Server();

		HttpConfiguration http_config = new HttpConfiguration();
		http_config.setSecureScheme("https");
		http_config.setSecurePort(httpsPort);
		http_config.setOutputBufferSize(32768);

		// HTTP connector
		ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(http_config));
		//http.setHost("localhost");
		http.setPort(httpPort);
		http.setIdleTimeout(30000);

		// ssl context
		SslContextFactory sslContextFactory = new SslContextFactory();
		//sslContextFactory.setKeyStorePath("/home/joergboe/git2/toolkit.streamsx.inet/tests/com.ibm.streams.inet.junit/resources/keystore");
		sslContextFactory.setKeyStorePath("etc/keystore");
		//sslContextFactory.setKeyStorePassword("");
		sslContextFactory.setKeyManagerPassword("test");
		//sslContextFactory.setKeyStorePath("/home/joergboe/jetty/jetty-distribution-9.4.12.v20180830/demo-base/etc/keystore");
		//sslContextFactory.setKeyStorePassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
		//sslContextFactory.setKeyManagerPassword("OBF:1u2u1wml1z7s1z7a1wnl1u2g");
		System.out.println(sslContextFactory.dump());

		// HTTPS Configuration
		// A new HttpConfiguration object is needed for the next connector and
		// you can pass the old one as an argument to effectively clone the
		// contents. On this HttpConfiguration object we add a
		// SecureRequestCustomizer which is how a new connector is able to
		// resolve the https connection before handing control over to the Jetty
		// Server.
		HttpConfiguration https_config = new HttpConfiguration(http_config);
		SecureRequestCustomizer src = new SecureRequestCustomizer();
		src.setStsMaxAge(2000);
		src.setStsIncludeSubDomains(true);
		https_config.addCustomizer(src);
		
		// HTTPS connector
		// We create a second ServerConnector, passing in the http configuration
		// we just made along with the previously created ssl context factory.
		// Next we set the port and a longer idle timeout.
		ServerConnector https = new ServerConnector(server,
				new SslConnectionFactory(sslContextFactory,HttpVersion.HTTP_1_1.asString()),
				new HttpConnectionFactory(https_config));
		https.setPort(httpsPort);
		https.setIdleTimeout(500000);
		
		server.setConnectors(new Connector[] { http, https });

		ServletHandler servletHandler = new ServletHandler();

		servletHandler.addServletWithMapping(HelloServlet.class, "/hello/*");
		servletHandler.addServletWithMapping(HeaderServlet.class, "/headers");
		servletHandler.addServletWithMapping(MethodServlet.class, "/get");
		servletHandler.addServletWithMapping(MethodServlet.class, "/delete");
		servletHandler.addServletWithMapping(MethodServlet.class, "/patch");
		servletHandler.addServletWithMapping(MethodServlet.class, "/post");
		servletHandler.addServletWithMapping(MethodServlet.class, "/put");
		servletHandler.addServletWithMapping(MethodServlet.class, "/head");
		servletHandler.addServletWithMapping(RedirectServlet.class, "/redirect/*");
		servletHandler.addServletWithMapping(StatusServlet.class, "/status/*");
		servletHandler.addServletWithMapping(RedirectToServlet.class, "/redirect-to");
		servletHandler.addServletWithMapping(AuthServlet.class, "/basic-auth/*");

		LoginService loginService = new HashLoginService("MyRealm", "realm.properties");
		server.addBean(loginService);
		
		ConstraintSecurityHandler security = new ConstraintSecurityHandler();
		server.setHandler(security);
		
		Constraint constraint = new Constraint();
		constraint.setName("auth");
		constraint.setAuthenticate(true);
		constraint.setRoles(new String[] { "user", "admin" });

		ConstraintMapping mapping = new ConstraintMapping();
		mapping.setPathSpec("/basic-auth/*");
		//mapping.setPathSpec("/hello/*");
		mapping.setConstraint(constraint);
		security.setConstraintMappings(Collections.singletonList(mapping));
		security.setAuthenticator(new BasicAuthenticator());
		security.setLoginService(loginService);
		security.setHandler(servletHandler);

		//RolloverFileOutputStream outputStream = new RolloverFileOutputStream("server.log", true,10);
		//DebugHandler debugHandler = new DebugHandler();
		//debugHandler.setOutputStream(outputStream);
		//debugHandler.setHandler(server.getHandler());
		//server.setHandler(debugHandler);
		
		server.start();
		server.dumpStdErr();
		server.join();

	}

}

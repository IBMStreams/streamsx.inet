/**
 * 
 */
package com.ibm.streamsx.inet.test.httptestserver;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

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
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.PathResource;
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
		boolean requestClientCertificate = false;
		if (args.length < 2) {
			System.out.println("HTTPTest server requires 2 or 3 arguments: httpPort httpsPort, [--clientCert]");
		} else {
			httpPort = Integer.parseInt(args[0]);
			httpsPort = Integer.parseInt(args[1]);
		}
		if (args.length > 2) {
			if (args[2].equals("--clientCert")) {
				requestClientCertificate = true;
			}
		}
		
		System.out.println("Start HTTP Test server listening on ports:");
		System.out.print(" - http  port: "); System.out.println(httpPort);
		System.out.print(" - https port: ");System.out.println(httpsPort);
		System.out.print(" - requestClientCertificate: "); System.out.println(requestClientCertificate);
		
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

		// ssl context1
		SslContextFactory sslContextFactory = new SslContextFactory();
		sslContextFactory.setKeyStorePath("etc/keystore.jks");
		//sslContextFactory.setKeyStorePassword("changeit"); //store password is not required
		sslContextFactory.setKeyManagerPassword("changeit"); //key password is required
		sslContextFactory.setCertAlias("mykey");
		if (requestClientCertificate) {
			sslContextFactory.setTrustStorePath("etc/cacert.jks");
			//sslContextFactory.setTrustStorePassword("changeit"); //store password is not required
			sslContextFactory.setNeedClientAuth(true);
		}
		
		sslContextFactory.setRenegotiationAllowed(false);
		sslContextFactory.setExcludeProtocols("SSLv3");
		sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.1");
		
		System.out.println("********************************");
		System.out.println(sslContextFactory.dump());
		System.out.println("********************************");
		String[] exlist = sslContextFactory.getExcludeCipherSuites();
		System.out.println("Excluded CipherSuites:");
		for (int x=0; x<exlist.length; x++) {
			System.out.println(exlist[x]);
		}
		String[] exlist2 = sslContextFactory.getExcludeProtocols();
		for (int x=0; x<exlist2.length; x++) {
			System.out.println(exlist2[x]);
		}
		System.out.println("Included CipherSuites:");
		String[] exlist3 = sslContextFactory.getIncludeCipherSuites();
		for (int x=0; x<exlist3.length; x++) {
			System.out.println(exlist3[x]);
		}
		System.out.println("********************************");
		
		//must manipulate the excluded cipher specs to avoid that all specs are disables with streams java ssl engine
		String[] specs = {"^.*_(MD5|SHA|SHA1)$","^TLS_RSA_.*$","^.*_NULL_.*$","^.*_anon_.*$"};
		//String[] specs = {};
		sslContextFactory.setExcludeCipherSuites(specs);
		System.out.println("********************************");
		System.out.println(sslContextFactory.dump());
		System.out.println("********************************");
		
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

		//gzip handler
		GzipHandler gzip = new GzipHandler();
		gzip.setIncludedMethods("GET","POST");
		gzip.setMinGzipSize(245);
		gzip.setIncludedMimeTypes("text/plain","text/css","text/html", "application/javascript");
		gzip.setIncludedPaths("/gzip/*");

		//Servlet context handler and context (static)
		Path webRootPath = new File("webapps/static-root/").toPath().toRealPath();
		ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/");
		context.setBaseResource(new PathResource(webRootPath));
		context.setWelcomeFiles(new String[] { "index.html" });
		//add servlets
		context.addServlet(HelloServlet.class, "/hello/*");
		context.addServlet(HelloServlet2.class, "/hello2/*");
		context.addServlet(HeaderServlet.class, "/headers");
		context.addServlet(MethodServlet.class, "/get");
		context.addServlet(MethodServlet.class, "/delete");
		context.addServlet(MethodServlet.class, "/patch");
		context.addServlet(MethodServlet.class, "/post");
		context.addServlet(MethodServlet.class, "/put");
		context.addServlet(MethodServlet.class, "/head");
		context.addServlet(RedirectServlet.class, "/redirect/*");
		context.addServlet(StatusServlet.class, "/status/*");
		context.addServlet(RedirectToServlet.class, "/redirect-to");
		context.addServlet(AuthServlet.class, "/basic-auth/*");
		context.addServlet(Oauth2Servlet.class, "/oauth2/*");
		context.addServlet(ResourceServlet.class, "/resource/*");
		context.addServlet(HelloServlet.class, "/gzip");
		context.addServlet(DelayServlet.class, "/delay/*");
		context.addServlet(DefaultServlet.class,"/"); // always last, always on "/"

		gzip.setHandler(context);
		
		LoginService loginService = new HashLoginService("MyRealm", "realm.properties");
		server.addBean(loginService);
		
		ConstraintSecurityHandler security = new ConstraintSecurityHandler();

		Constraint constraint = new Constraint();
		constraint.setName("auth");
		constraint.setAuthenticate(true);
		constraint.setRoles(new String[] { "user", "admin" });

		ConstraintMapping mapping1 = new ConstraintMapping();
		mapping1.setPathSpec("/basic-auth/*");
		mapping1.setConstraint(constraint);
		ConstraintMapping mapping2 = new ConstraintMapping();
		mapping2.setPathSpec("/oauth2/*");
		mapping2.setConstraint(constraint);
		ArrayList<ConstraintMapping> mappings = new ArrayList<ConstraintMapping>();
		mappings.add(mapping1);
		mappings.add(mapping2);
		security.setConstraintMappings(mappings);
		security.setAuthenticator(new BasicAuthenticator());
		security.setLoginService(loginService);

		security.setHandler(gzip);

		server.setHandler(security);

		server.start();
		server.dumpStdErr();
		server.join();
	}

}

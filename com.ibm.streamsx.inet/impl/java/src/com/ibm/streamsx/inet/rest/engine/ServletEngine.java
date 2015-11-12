/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014  
*/
package com.ibm.streamsx.inet.rest.engine;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.relation.MBeanServerNotificationFilter;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.StreamingData;
import com.ibm.streams.operator.management.OperatorManagement;
import com.ibm.streamsx.inet.http.PathConversionHelper;
import com.ibm.streamsx.inet.rest.ops.Functions;
import com.ibm.streamsx.inet.rest.ops.PostTuple;
import com.ibm.streamsx.inet.rest.servlets.ExposedPortsInfo;
import com.ibm.streamsx.inet.rest.servlets.PortInfo;
import com.ibm.streamsx.inet.rest.setup.ExposedPort;
import com.ibm.streamsx.inet.rest.setup.OperatorServletSetup;

/**
 * Eclipse Jetty Servlet engine that can be shared by multiple operators
 * within the same PE. Sharing is performed via JMX to
 * avoid class loading issues due to each operator having
 * its own classloader and hence its own version of the jetty
 * libraries.
 * Supports multiple servlet engines within the same PE,
 * one per defined port.
 */
public class ServletEngine implements ServletEngineMBean, MBeanRegistration {
	
    static Logger trace = Logger.getLogger(ServletEngine.class.getName());
	
    private static final Object syncMe = new Object();

	public static final String CONTEXT_RESOURCE_BASE_PARAM = "contextResourceBase";
    public static final String CONTEXT_PARAM = "context";
    
    public static final String SSL_CERT_ALIAS_PARAM = "certificateAlias";
    public static final String SSL_KEYSTORE_PARAM = "keyStore";
    public static final String SSL_KEYSTORE_PASSWORD_PARAM = "keyStorePassword";
    public static final String SSL_KEY_PASSWORD_PARAM = "keyPassword";
    
    public static final String SSL_TRUSTSTORE_PARAM = "trustStore";
    public static final String SSL_TRUSTSTORE_PASSWORD_PARAM = "trustStorePassword";

    public static ServletEngineMBean getServletEngine(OperatorContext context) throws Exception {
		
		int portNumber = 8080;
		if (context.getParameterNames().contains("port"))
			portNumber = Integer.valueOf(context.getParameterValues("port").get(0));

		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

		final ObjectName jetty = new ObjectName(
				"com.ibm.streamsx.inet.rest:type=jetty,port=" + portNumber);

        synchronized (syncMe) {
            if (!mbs.isRegistered(jetty)) {
                try {
                    mbs.registerMBean(new ServletEngine(jetty, context, portNumber),
                            jetty);
                } catch (InstanceAlreadyExistsException infe) {
                }
            }
        }
		
		return JMX.newMBeanProxy(ManagementFactory.getPlatformMBeanServer(), jetty, ServletEngineMBean.class);
	}

	protected final OperatorContext startingContext;
	protected final ThreadPoolExecutor tpe;

	private boolean started;
	private boolean stopped;
    
    private final Server server;
    private final ObjectName myObjectName;
    private boolean isSSL;
    // Jetty port.
    private int localPort;
    private final ContextHandlerCollection handlers;
    private final Map<String, ServletContextHandler> contexts = Collections.synchronizedMap(
            new HashMap<String, ServletContextHandler>());
    
    private final List<ExposedPort> exposedPorts = Collections.synchronizedList(new ArrayList<ExposedPort>());
   
    private ServletEngine(ObjectName myObjectName, OperatorContext context, int portNumber) throws Exception {
        this.myObjectName = myObjectName;
		this.startingContext = context;                
        tpe = newContextThreadPoolExecutor(context);
       
        server = new Server();
        handlers = new ContextHandlerCollection();
        
        if (context.getParameterNames().contains(SSL_CERT_ALIAS_PARAM))
            setHTTPSConnector(context, server, portNumber);
        else
            setHTTPConnector(context, server, portNumber);
        context.getMetrics().getCustomMetric("https").setValue(isSSL ? 1 : 0);
        
        server.setThreadPool(new ThreadPool() {

            @Override
            public boolean dispatch(Runnable runnable) {
                try {
                    tpe.execute(runnable);
                } catch (RejectedExecutionException e) {
                    return false;
                }
                return true; 
            }

            @Override
            public int getIdleThreads() {
                return tpe.getPoolSize()-tpe.getActiveCount();
            }

            @Override
            public int getThreads() {
                 return tpe.getPoolSize();
            }

            @Override
            public boolean isLowOnThreads() {
                return tpe.getActiveCount()>=tpe.getMaximumPoolSize();
            }

            @Override
            public void join() throws InterruptedException {
                while (true) {
                    Thread.sleep(600L*1000L);
                }
                
            }});
        
        ServletContextHandler portsIntro = new ServletContextHandler(server, "/ports", ServletContextHandler.SESSIONS);
        portsIntro.addServlet(new ServletHolder(
        		new ExposedPortsInfo(exposedPorts)), "/info");       
        addHandler(portsIntro);
        
       // String impl_lib_jar = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
       // File toolkitRoot = new File(impl_lib_jar).getParentFile().getParentFile().getParentFile();
        
        // making a abs path by combining toolkit directory with the opt/resources dir
        URI baseToolkitDir = context.getToolkitDirectory().toURI();
        //File toolkitResource = new File(toolkitRoot, "opt/resources");        
        addStaticContext(null, "streamsx.inet.resources", PathConversionHelper.convertToAbsPath(baseToolkitDir, "opt/resources"));
        
        String streamsInstall = System.getenv("STREAMS_INSTALL");
        if (streamsInstall != null) {
            File dojo = new File(streamsInstall, "ext/dojo");        
            addStaticContext(null, "streamsx.inet.dojo", dojo.getAbsolutePath());       	
        }
    }
    
    /**
     * Setup an HTTP connector.
     */
    private void setHTTPConnector(OperatorContext context, Server server, int portNumber) {
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(portNumber);
        connector.setMaxIdleTime(30000);
        server.addConnector(connector);
    }
    
    /**
     * Setup an HTTPS connector.
     */
    private void setHTTPSConnector(OperatorContext context, Server server, int portNumber) {
        SslContextFactory sslContextFactory = new SslContextFactory();
        
        String keyStorePath = context.getParameterValues(SSL_KEYSTORE_PARAM).get(0);
        File keyStorePathFile = new File(keyStorePath);
        if (!keyStorePathFile.isAbsolute())
            keyStorePathFile = new File(context.getPE().getApplicationDirectory(), keyStorePath);
        sslContextFactory.setKeyStorePath(keyStorePathFile.getAbsolutePath());
        
        String keyStorePassword = context.getParameterValues(SSL_KEYSTORE_PASSWORD_PARAM).get(0);
        sslContextFactory.setKeyStorePassword(Functions.obfuscate(keyStorePassword));
        
        String keyPassword;
        if (context.getParameterNames().contains(SSL_KEY_PASSWORD_PARAM))
            keyPassword = context.getParameterValues(SSL_KEY_PASSWORD_PARAM).get(0);
        else
            keyPassword = keyStorePassword;
   
        sslContextFactory.setKeyManagerPassword(Functions.obfuscate(keyPassword));
               
        sslContextFactory.setAllowRenegotiate(false);
        sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.1");
        sslContextFactory.setExcludeProtocols("SSLv3");
        
        if (context.getParameterNames().contains(SSL_TRUSTSTORE_PARAM)) {
            String trustStorePath = context.getParameterValues(SSL_TRUSTSTORE_PARAM).get(0);
            sslContextFactory.setNeedClientAuth(true);
            File trustStorePathFile = new File(trustStorePath);
            if (!trustStorePathFile.isAbsolute())
                trustStorePathFile = new File(context.getPE().getApplicationDirectory(), trustStorePath);
            
            sslContextFactory.setTrustStore(trustStorePath);
            
            String trustStorePassword = context.getParameterValues(SSL_TRUSTSTORE_PASSWORD_PARAM).get(0);
            sslContextFactory.setTrustStorePassword(Functions.obfuscate(trustStorePassword));
        }
        
        SslSelectChannelConnector connector = new SslSelectChannelConnector(sslContextFactory);
        
        connector.setPort(portNumber);
        connector.setMaxIdleTime(30000);
        server.addConnector(connector); 
        
        isSSL = true;
    }


        // Originally corePoolSize was set to a fixed: 32
        // Jetty, however, creates its starting threads based on the number of
        // available processors 2*(Runtime.getRuntime().availableProcessors()+3)/4    
        // On large hosts (ppc64 with 24 processors, this can exceed 32)
        // While many descriptions of the ThreadPoolExecuter make it seem that it will
        // just add threads, testing has shown that this did not occur.
        // Some literature states it will only add threads if the queue is full
        // If Jetty never starts, then the queue will never fill, thus
        // we need core threads to be set to at least as large as the number of threads
        // that Jetty will start
        // NOTE: This was based on examination of jetty 8.1.3 code
        //       If the toolkit moves to jetty 9+ this could change
	private ThreadPoolExecutor newContextThreadPoolExecutor(OperatorContext context) {
                int jettyStartupThreads = 2*(Runtime.getRuntime().availableProcessors()+3)/4;      
                trace.info("Creating ThreadPoolExecuter corePoolSize: 32+" + jettyStartupThreads);
		return  new ThreadPoolExecutor(
                32 + jettyStartupThreads, // corePoolSize,
                Math.max(256, 32 + jettyStartupThreads), // maximumPoolSize,
                60, //keepAliveTime,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), // workQueue,
                context.getThreadFactory());
	}
    
    private synchronized void addHandler(ServletContextHandler newHandler) {      
        handlers.addHandler(newHandler);
        handlers.mapContexts();
    }
    
    @Override
	public void start() throws Exception {

		synchronized (this) {
			if (started) {
				return;
			}
			started = true;
		}

		startWebServer();

	}
    @Override
	public void stop() throws Exception {
		synchronized (this) {
			if (stopped || !started) {
				return;
			}
			stopped = true;
			notifyAll();
		}
		stopWebServer();
	}
	
	/**
	 * Add a default servlet that allows an operator
	 * to pull static resources from a single location.
	 * Typically used with getThisFileDir(). 
	 * @throws Exception 
	 */
	private ServletContextHandler addOperatorStaticContext(OperatorContext context) throws Exception {
	    
	    if (!context.getParameterNames().contains(CONTEXT_PARAM))
	        return null;
	    if (!context.getParameterNames().contains(CONTEXT_RESOURCE_BASE_PARAM))
            return null;
	    
	    
	    String ctxName = context.getParameterValues(CONTEXT_PARAM).get(0);
	    String resourceBase = context.getParameterValues(CONTEXT_RESOURCE_BASE_PARAM).get(0);
	    
	    if ("".equals(ctxName))
	        throw new IllegalArgumentException("Parameter " + CONTEXT_PARAM + " cannot be empty");

	    if ("".equals(resourceBase))
            throw new IllegalArgumentException("Parameter " + CONTEXT_RESOURCE_BASE_PARAM + " cannot be empty");

	 // Convert resourceBase file path to absPath if it is relative, if relative, it should be relative to application directory.
        URI baseConfigURI = context.getPE().getApplicationDirectory().toURI();
        return addStaticContext(context, ctxName, PathConversionHelper.convertToAbsPath(baseConfigURI, resourceBase));
	}

    private ServletContextHandler addStaticContext(OperatorContext opContext, String ctxName, String resourceBase) throws Exception {
        
        if (contexts.containsKey(ctxName))
            return contexts.get(ctxName);
        
        ServletContextHandler cntx = new ServletContextHandler(server, "/" + ctxName,
                ServletContextHandler.SESSIONS);
        
        cntx.setWelcomeFiles(new String[] { "index.html" });
        cntx.setResourceBase(resourceBase);	
        
        ResourceHandler rh = new ResourceHandler();
        rh.setDirectoriesListed(true);
        cntx.setHandler(rh);

        addHandler(cntx);
        contexts.put(ctxName, cntx);
        
        trace.info("Static context: " + cntx.getContextPath() +
                " resource base: " + resourceBase);

        return cntx;
    }

    private void startWebServer() throws Exception {     

        ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setWelcomeFiles(new String[] { "index.html" });

       // File html = new File(startingContext.getPE().getDataDirectory()
       //         .getAbsolutePath(), "html");
        
        URI baseResourceURI = startingContext.getPE().getApplicationDirectory().toURI();
        
        resource_handler.setResourceBase(PathConversionHelper.convertToAbsPath(baseResourceURI, "opt/html"));
        // handlers.addHandler(resource_handler);
        
        HandlerList topLevelhandlers = new HandlerList();
        topLevelhandlers.setHandlers(new Handler[] { handlers, resource_handler, new DefaultHandler() });
        
        server.setHandler(topLevelhandlers);
        server.start();   
        Thread t = startingContext.getThreadFactory().newThread(new Runnable() {
            public void run() {

                try {
                    server.join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }
        });

        t.setDaemon(false);
        t.start();
        
        localPort = server.getConnectors()[0].getLocalPort();
        startingContext.getMetrics().getCustomMetric("serverPort").setValue(localPort);
    }
    
   
    private void stopWebServer() throws Exception {
        server.stop(); 
    }
        
    @Override
    public void registerOperator(
    		final String operatorClass,
    		final OperatorContext context, Object conduit)
            throws Exception {
    	
    	

    	trace.info("Register servlets for operator: " + context.getName());
    	
        final ServletContextHandler staticContext = addOperatorStaticContext(context);
        if (staticContext != null) {
        	staticContext.setAttribute("operator.context", context);
        	if (conduit != null)
        	    staticContext.setAttribute("operator.conduit", conduit);
        }

        
        // For a static context just use the name of the
        // base operator (without the composite nesting qualifiers)
        // as the lead in for resources exposed by this operator.
        // Otherwise use the full name of the operator so that it is unique.
        String leadIn = context.getName(); // .replace('.', '/');
        if (staticContext != null && leadIn.indexOf('.') != -1) {
            leadIn = leadIn.substring(leadIn.lastIndexOf('.') + 1);
        }
        
        // Standard ports context for URLs relative to ports.
        ServletContextHandler ports = null;
        if (context.getNumberOfStreamingInputs() != 0 ||
        		context.getNumberOfStreamingOutputs() != 0) {
        	
        	String portsContextPath = "/" + leadIn + "/ports";
        	if (staticContext != null)
        		portsContextPath = staticContext.getContextPath() + portsContextPath;
        	ports = new ServletContextHandler(server, portsContextPath,
                    ServletContextHandler.SESSIONS);
        	
        	ports.setAttribute("operator.context", context);
        	if (conduit != null)
        	     ports.setAttribute("operator.conduit", conduit);

        	trace.info("Ports context: " + ports.getContextPath());
        	
            if (context.getParameterNames().contains(PostTuple.MAX_CONTENT_SIZE_PARAM)) {
            	int maxContentSize = Integer.parseInt(context.getParameterValues(PostTuple.MAX_CONTENT_SIZE_PARAM).get(0)) * 1000;
            	if (maxContentSize > 0) {
            		trace.info("Maximum content size for context: " + ports.getContextPath() + " increased to " + maxContentSize);
            		ports.setMaxFormContentSize(maxContentSize);
            	}
            }
        }
        
        // Automatically add info servlet for all output and input ports
        for (StreamingData port : context.getStreamingOutputs()) {
            String path = "/output/" + port.getPortNumber() + "/info";
            ports.addServlet(new ServletHolder(new PortInfo(context, port)),  path);
            trace.info("Port information servlet URL : " + ports.getContextPath() + path);
        }
        for (StreamingData port : context.getStreamingInputs()) {
            String path = "/input/" + port.getPortNumber() + "/info";
            ports.addServlet(new ServletHolder(new PortInfo(context, port)),  path);
            trace.info("Port information servlet URL : " + ports.getContextPath() + path);
        }

        // Add servlets for the operator, driven by a Setup class that implements
        // OperatorServletSetup with a name derived from the operator class name.
        
        String setupClass = operatorClass.replace(".ops.", ".setup.").concat("Setup");
        OperatorServletSetup setup = 
        		Class.forName(setupClass).asSubclass(OperatorServletSetup.class).newInstance();
        
        List<ExposedPort> operatorPorts = setup.setup(context, staticContext, ports);
        if (operatorPorts != null)
        	exposedPorts.addAll(operatorPorts);
        
        if (ports != null)
        	addHandler(ports);
    }
   
    public static class OperatorWebAppContext extends WebAppContext {
        public OperatorWebAppContext() {
        }
    }

    @Override
    public void postDeregister() {
    }

    /**
     * On PE shutdown unregister this MBean, allows unit tests
     * to have multiple executions in the same JVM.
     */
    @Override
    public void postRegister(Boolean registrationDone) {
        
        MBeanServerNotificationFilter unregisterPe = new MBeanServerNotificationFilter();
        unregisterPe.disableAllTypes();
        unregisterPe.disableAllTypes();
        unregisterPe.enableObjectName(OperatorManagement.getPEName());
        unregisterPe.enableType(MBeanServerNotification.UNREGISTRATION_NOTIFICATION);
        
        try {
            ManagementFactory.getPlatformMBeanServer().addNotificationListener(
                    MBeanServerDelegate.DELEGATE_NAME, new NotificationListener() {
                        
                        @Override
                        public void handleNotification(Notification notification, Object handback) {
                            try {
                                ManagementFactory.getPlatformMBeanServer().unregisterMBean(myObjectName);
                            } catch (MBeanRegistrationException e) {
                                ;
                            } catch (InstanceNotFoundException e) {
                                ;
                            }
                        }
                    }, unregisterPe, null);
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void preDeregister() throws Exception {
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {
        return null;
    }
}

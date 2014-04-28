/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2014  
# US Government Users Restricted Rights - Use, duplication or
# disclosure restricted by GSA ADP Schedule Contract with
# IBM Corp.
*/
package com.ibm.streamsx.inet.rest.engine;

import java.io.File;
import java.lang.management.ManagementFactory;
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
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.StreamingData;
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
public class ServletEngine implements ServletEngineMBean {
	
    static Logger trace = Logger.getLogger(ServletEngine.class.getName());
	
    private static final Object syncMe = new Object();

	public static final String CONTEXT_RESOURCE_BASE_PARAM = "contextResourceBase";
    public static final String CONTEXT_PARAM = "context";

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
                    mbs.registerMBean(new ServletEngine(context, portNumber),
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
    private final ContextHandlerCollection handlers;
    private final Map<String, ServletContextHandler> contexts = Collections.synchronizedMap(
            new HashMap<String, ServletContextHandler>());
    
    private final List<ExposedPort> exposedPorts = Collections.synchronizedList(new ArrayList<ExposedPort>());
   
    private ServletEngine(OperatorContext context, int portNumber) throws Exception {
        
		this.startingContext = context;                
        tpe = newContextThreadPoolExecutor(context);
       
        server = new Server();
        handlers = new ContextHandlerCollection();

        SelectChannelConnector connector0 = new SelectChannelConnector();
        connector0.setPort(portNumber);
        connector0.setMaxIdleTime(30000);
        server.addConnector(connector0);
        
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
        
        String impl_lib_jar = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        File toolkitRoot = new File(impl_lib_jar).getParentFile().getParentFile().getParentFile();
        
        File toolkitResource = new File(toolkitRoot, "opt/resources");        
        addStaticContext(null, "streamsx.inet.resources", toolkitResource.getAbsolutePath());
        
        String streamsInstall = System.getenv("STREAMS_INSTALL");
        if (streamsInstall != null) {
            File dojo = new File(streamsInstall, "ext/dojo");        
            addStaticContext(null, "streamsx.inet.dojo", dojo.getAbsolutePath());       	
        }
    }
    
	private ThreadPoolExecutor newContextThreadPoolExecutor(OperatorContext context) {
		return  new ThreadPoolExecutor(
                32, // corePoolSize,
                256, // maximumPoolSize,
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

	    return addStaticContext(context, ctxName, resourceBase);
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

        File html = new File(startingContext.getPE().getDataDirectory()
                .getAbsolutePath(), "html");
        resource_handler.setResourceBase(html.getAbsolutePath());
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
}

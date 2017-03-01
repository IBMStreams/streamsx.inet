/*
 Licensed Materials - Property of IBM
 Copyright IBM Corp. 2014 
*/

/**
 * New functionality can be added without changing the existing code by
 * adding three items:
 * 1) A Java primitive operator
 * 2) One or more servlets that interact with the Java primitive operator
 * 3) A setup class for the Java primitive operator
 * 
 * The detailed steps are:
 * 
 * 1) Create a new Java primitive operator class that extends ServletOperator.
 *    Annotate it to define its operator model, including the required ports
 *    and parameters. This operator will then register itself with
 *    the ServletEngine through ServletEngineMBean. 
 *    
 *    Look at the sub-classes of ServletOperator for examples.
 *    
 *    The operator can pass data to its servlet through its conduit object
 *    which is made available to the servlets through the attribute named
 *    operator.conduit. This object must only reference classes that are
 *    from the Java platform or the Java operator API. This is because the
 *    servlet engine may be started by a different operator with a different classloader.
 *    See XMLView and AccessXMLAttribute for example use.
 *   
 * 2) Create the servlet(s), see all the servlets in com.ibm.streamsx.inet.rest.servlets for examples.
 *  
 * 3) Create a implementation of OperatorServletSetup that creates the servlet(s) for the operator.
 * The servlet engine uses the OperatorServletSetup based upon the operator's full class
 * name, replacing '.ops.' with '.setup.' and appending Setup, for example
 * 
 * operator class - com.ibm.streamsx.rest.ops.PostXML
 * setup class    - com.ibm.streamsx.rest.setup.PostXMLSetup
 * 
 * An new instance of the setup class is created, using its public no-arg constructor.
 * 
 * The setup class returns information that is used to report the URL paths and types
 * via the ports/info URL. Type is an arbitrary value, it should be unique for the
 * operator class. JavaScript applications can use the type to find paths of a specific type.
 *    
 * 
 */
@com.ibm.streams.operator.model.Namespace("com.ibm.streamsx.inet.rest")
package com.ibm.streamsx.inet.rest.ops;

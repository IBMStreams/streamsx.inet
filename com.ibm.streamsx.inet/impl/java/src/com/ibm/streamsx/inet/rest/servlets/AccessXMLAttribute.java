/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2014 
*/
package com.ibm.streamsx.inet.rest.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.streams.operator.StreamingData;
import com.ibm.streams.operator.types.XML;

/**
 * Returns an XML attribute as application/xml
 * 
 */
public class AccessXMLAttribute extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2612945204950914891L;

	private final StreamingData port;
	private Map<Integer, Object[]> portData;
	private long lastModified = System.currentTimeMillis();

	public AccessXMLAttribute(StreamingData port) {
		this.port = port;
	}
	@SuppressWarnings("unchecked")
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		portData = (Map<Integer, Object[]>) config.getServletContext().getAttribute("operator.conduit");
	}

	public long getLastModified() {
		return lastModified;
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/xml");
		ServletOutputStream out = response.getOutputStream();

		Object[] data = portData.get(port.getPortNumber());
		
		if (data == null) {
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
		} else {
			XML xdata = (XML) data[0];
			lastModified = (Long) data[1];
			
			if (xdata.isDefaultValue()) {
				response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			} else {
				response.setStatus(HttpServletResponse.SC_OK);

				InputStream datai = xdata.getInputStream();

				int r;
				while ((r = datai.read()) != -1) {
					out.write(r);
				}
				datai.close();
			}
		}

		out.flush();
		out.close();
	}
}

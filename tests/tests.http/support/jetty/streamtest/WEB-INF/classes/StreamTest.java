import javax.servlet.*;
import javax.servlet.http.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.nio.charset.*;

public class StreamTest extends HttpServlet
{


  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException
  {
      int max = 1000;
      String excep = req.getParameter("throw");
      if(excep != null && excep.equals("yes")) 
	  throw new ServletException("Throwing");
      String val = req.getParameter("dataCount") ;

      if(val != null)
	  max = Integer.parseInt(val);

      int i=0;
      while(i < max)
	  resp.getWriter().println("i="+(i++));
      resp.getWriter().close();
  }

  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException
  {
      OutputStream out=null;
      InputStream in=req.getInputStream();
      if(in==null) throw new IOException("input is null");
      try
	  {
	      System.out.println(req);
	      System.out.println(req.getCharacterEncoding());
	      System.out.println(Arrays.toString(req.getParameterMap().entrySet().toArray()));
	      resp.setContentType("text/plain");
	      
	      StringBuffer buf = new StringBuffer();
	      
	      BufferedReader br = new BufferedReader(new InputStreamReader(in));
	      String inputLine = null;
	      
	      while (((inputLine = br.readLine()) != null)) {
		  buf.append(inputLine);
	      }
	      out=resp.getOutputStream();
	      System.out.println(buf.toString());
	      out.write(buf.toString().getBytes(Charset.forName("UTF-8")));
	  }
      catch(IOException err)
	  {
	      //ignore
	  }
      finally
	  {
	      if(out!=null) out.flush();
	      if(out!=null) out.close();
	      in.close();
	  }
  }
		
}


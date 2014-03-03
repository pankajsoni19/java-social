package com.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

import com.google.common.io.Files;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class ServeStaticFiles implements HttpHandler {

	static Logger logger = Logger.getLogger(ServeStaticFiles.class);

	@Override
	public void handle(HttpExchange he) throws IOException {
		
		String requestMethod = he.getRequestMethod();
		
		byte buffer[] = new byte[512];
		//read request body
		BufferedInputStream bis = new BufferedInputStream(he.getRequestBody());
		int bytesRead = 0;
		while((bytesRead = bis.read(buffer)) != -1){}
		
		String arr[];
		
		/* Server paths:
		 * /js/ /css/ /img/ /public_docs/ /photos/
		 * 						
		*/
		if(! requestMethod.contentEquals("GET") ) {
			he.sendResponseHeaders(412, -1);
			he.close();
			return;
		}
		
			
		String path = he.getRequestURI().getPath();
		
		if(path.startsWith("/docs/") || path.startsWith("/email/")){
			path = URLDecoder.decode(path, "UTF-8");				
			String Cookie = Utility.getCookie("SID", he.getRequestHeaders());			
			if (Cookie==null || CookieData.getCookieData(Cookie) == null){
				Headers h = he.getResponseHeaders();	
				h.set("Refresh", "0; url=/login");		
				he.sendResponseHeaders(403, -1);
				he.close();
				return;
			}
		}		
			
		Headers h = he.getResponseHeaders();
		File f = null;			
		Calendar calendar = Calendar.getInstance();
		if(path.contains("/photos/")){				
			f = new File(Config.getImageFilePath(path));		
			calendar.add(Calendar.MINUTE, 2);
			h.set("Cache-Control","public, max-age=120");
		}else if (path.startsWith("/email/")){
			path = path.substring(path.indexOf("/", 1));
			f = new File(Config.getEmailAttachmentPath(path));	
			calendar.add(Calendar.YEAR, 1);				
			h.set("Cache-Control","public, max-age=31536000");	
		}else{
			f = new File(Config.getStaticFilePath(path));	
			calendar.add(Calendar.YEAR, 1);				
			h.set("Cache-Control","public, max-age=31536000");	
		}
			
		logger.debug("Sending static file by path" + f.getPath());
		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z");
		dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));		
		String expires = dateFormatGmt.format(calendar.getTime());
		h.set("Expires", expires);
		
		h.set("Connection","keep-alive");						
		h.set("Content-Type",Config.mimeData.get(Files.getFileExtension(path)));
		h.set("Content-Encoding", "gzip");
		he.sendResponseHeaders(200, 0);
		
		long toRead = f.length();			
		
		//BufferedOutputStream bufOs = new BufferedOutputStream(he.getResponseBody());
		GZIPOutputStream gos = new GZIPOutputStream(he.getResponseBody());
		BufferedInputStream bufread = new BufferedInputStream(new FileInputStream(f));
		
		buffer = new byte[2048];
		bytesRead = 0;
		while(toRead>0 && (bytesRead = bufread.read(buffer)) != -1){
			gos.write(buffer, 0, bytesRead);
			gos.flush();
			toRead = toRead - bytesRead; 
		}
		gos.finish();
		gos.close();
		bufread.close();
		he.close();
	}
	
	private String getRefreshURL(String path) throws UnknownHostException{
		String refreshurl = null;
		if (Config.isHTTPSsupported) {
			refreshurl = "0; url=https://"  + Config.getHost() + path;	
		} else {
			refreshurl = "0; url=http://"  + Config.getHost() + path;
		}
		return refreshurl;
	}
}
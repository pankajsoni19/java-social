package com.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Locale;

import org.apache.log4j.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class CatchAll implements HttpHandler {

	static Logger logger = Logger.getLogger(CatchAll.class);
	
	
	@Override
	public void handle(HttpExchange he) throws IOException {
		String cookie = Utility.getCookie("SID", he.getRequestHeaders());
		CookieData cd = CookieData.getCookieData(cookie);
		
		String requestMethod = he.getRequestMethod();
		String requestPath = he.getRequestURI().getPath();
		logger.info(Calendar.getInstance().getTime().toString() + " " + requestMethod + ": " 
					+ "requestPath: " + requestPath
					+ " cookie: " + cookie
					+ " validity: " + (cd !=null));
		
		
		switch (requestPath) {
		case "/list_documents":
				File f = new File("./public_docs/");
				String files[] = f.list();
				StringBuilder strbuild = new StringBuilder();
				String fileurl = "/public_docs/";
				strbuild.append("{").append("\"url\" : \"" + fileurl).append("\",");
				for(int i = 0; i< files.length; i ++){				
					strbuild.append("\"")
						.append(files[i])
						.append("\" : \"")
						.append(URLEncoder.encode(files[i], "UTF-8"))
						.append("\"");
					if(i<(files.length - 1))
						strbuild.append(",");
				}
				strbuild.append("}");
				String filesparsed = strbuild.toString();
				byte[] bdata = filesparsed.getBytes("UTF-8");
				he.sendResponseHeaders(200, bdata.length);
				OutputStream os = he.getResponseBody();
				os.write(bdata);
				os.flush();
				os.close();
				he.close();
			break;
		case "/logout":
			Config.removeCookie(cookie);
			sendRedirectHeaders("/login", he, Config.getDeleteCookie(cookie));			
			break;
		case "/list_contacts":
			if(cd !=null){
				try{
				String uname= cd.getUserName(cookie);
				String Contacts = CQLHandler.getContactsAndJsonify(uname);
				he.sendResponseHeaders(200, Contacts.getBytes("UTF-8").length);
				os = he.getResponseBody();
				os.write(Contacts.getBytes("UTF-8"));
				os.flush();
				os.close();
				}catch(Exception ex){
					ex.printStackTrace();
				}
			}else{
				he.sendResponseHeaders(403,  -1);
			}
			he.close();
			break;
		case "/add_contact":
			if(cd !=null){
				
				BufferedReader bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
				
				String[] dataRead = URLDecoder.decode(bufr.readLine(), "UTF-8").toLowerCase().split("&");
				bufr.close();
				String uname = cd.getUserName(cookie);
				StringBuilder strbuf = new StringBuilder();
				strbuf.append("UPDATE application_data.users SET contact_list['");
				for(int i=0; i< dataRead.length; i++){
					if(i<3){
						strbuf.append(dataRead[i].split("=")[1]);
						if(i <2){
							strbuf.append("&");
						}else{
							strbuf.append("'] = '");
						}
					}else{
						strbuf.append(dataRead[i].split("=")[1]);	
						if(i<(dataRead.length - 1)){
							strbuf.append("&");
						}else{
							strbuf.append("'")
							.append("  WHERE user_name='")
							.append("pankaj")
							.append("';");
						}
					}
				}				
				
				CQLHandler.runCommand(strbuf.toString());
				
				he.sendResponseHeaders(200, -1);				
			}else{
				he.sendResponseHeaders(403,  -1);
			}
			he.close();			
			break;
		case "/":
			if(cd != null){
				String redirPath = cd.getRedirectPath();
				sendRefreshHeaders(redirPath, he, null);
			}else{
				Utility.sendResponse("/index.htm", he);
			}
			break;
		case "/changepswd":
			if(cd != null){				
				Utility.sendResponse(cd.getRedirectPath() + ".htm", he);
			}else{
				Utility.sendResponse("/login.htm", he);
			}	
			break;
		case "/changepswd/token_verified":
			String query = he.getRequestURI().getQuery();			
			String[] tokens = query.split("&");
			String type = tokens[0].split("=")[1].trim();
			String uname = tokens[1].split("=")[1].trim();
			String token = tokens[2].split("=")[1].trim();
			if (type.contentEquals("resetpswd") && CQLHandler.resetPassword(uname,token)){
				he.sendResponseHeaders(200, -1);
			}else{
				he.sendResponseHeaders(412, -1);
			}				
			he.close();
			break;
		case "/changepswd/set_password":
			String qdata[] = he.getRequestURI().getQuery().split("&");
			uname = qdata[1].split("=")[1].trim();
			String password_recover_token = qdata[2].split("=")[1].trim();
			String pswd = qdata[3].split("=")[1].trim();			
			if (CQLHandler.setNewPassword(uname, pswd, password_recover_token)){
				he.sendResponseHeaders(202, -1);	
			}else{
				he.sendResponseHeaders(410, -1);
			}
			he.close();
			break;
		case "/favicon.ico":
			Utility.sendResponse("/img/favicon.ico", he);
			break;
		default:
			String lowercase = requestPath.toLowerCase(Locale.ENGLISH);
			if(lowercase.contentEquals(requestPath)){
				Utility.sendResponse("Internal_error.htm", he);
			}else{
				sendRedirectHeaders(lowercase, he, null);
			}
			break;
		}
	}
	
	private void sendRedirectHeaders(String redirecturl, HttpExchange he, String cookie) throws IOException{		
		Headers h = he.getResponseHeaders();
		if(cookie!=null)
			h.set("Set-Cookie", cookie);
		h.set("Location", redirecturl);		
		he.sendResponseHeaders(302, -1);
		he.close();
	}
	
	private void sendRefreshHeaders(String refreshurl, HttpExchange he, String cookie) throws IOException{		
		Headers h = he.getResponseHeaders();	
		if(cookie!=null)
			h.set("Set-Cookie", cookie);
		h.set("Refresh", "0; url=" + refreshurl);		
		he.sendResponseHeaders(200, -1);
		he.close();
	}
	
}

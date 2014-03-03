package com.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class LoginHandler implements HttpHandler {

	static Logger logger = Logger.getLogger(LoginHandler.class);
	
	@Override
	public void handle(HttpExchange he) {
		try{
		String Cookie = Utility.getCookie("SID", he.getRequestHeaders());
		CookieData cd = CookieData.getCookieData(Cookie);
		
		String requestMethod = he.getRequestMethod();
		
		logger.info(requestMethod + ": " 
					+ he.getRequestURI().getPath() 
					+ " cookie: " + Cookie
					+ " validity: " + (cd !=null));
		
		if(cd !=null)
		{			
			sendRefreshHeaders(cd.getRedirectPath(), he);
		} /*else if(Cookie != null && !cookieValidity)
		{
			//sendRefreshHeaders("/login", he,Config.getDeleteCookie());
			sendRefreshHeaders("/login", he);
		}*/ else{
			switch (requestMethod) {
			case "GET":
					processLoginData(he, Cookie);
				break;
			case "POST":
					processLoginPostData(he);
				break;
			default:
				break;
			}
		}
	}catch(Exception ex){
		ex.printStackTrace();
	}
	
	}
	
	private void processLoginPostData(HttpExchange he) throws IOException {
		String path = he.getRequestURI().getPath().toLowerCase();		
		switch (path) {
		case "/login":
			String[] readData = getPostData(he);
			String uname = readData[0].split("uname=")[1];
			String pwd = readData[1].split("passwd=")[1];
						
			String role = CQLHandler.checkLogon(uname, pwd);
			
			if (role !=null) {
				sendLoginAccepted(CookieData.getPath(role), he, getNewCookie(uname, role));
			} else {
				he.sendResponseHeaders(401, -1);
			}
			break;
		default:
			break;
		}		
	}
	
	private void processLoginData(HttpExchange he, String cookie) throws IOException {
		String path = he.getRequestURI().getPath().toLowerCase();		
		String[] tokens;
		switch (path) {
		case "/":
		case "/login":
			Utility.sendResponse("login.htm", he);			
			break;
		case "/login/init":
			String Query = he.getRequestURI().getQuery();
			if(Query != null && Query.length()> 1){
				tokens = Query.split("&");
				String type = tokens[0].split("=")[1].trim();
				String uname = tokens[1].split("=")[1].trim();
				String token = tokens[2].split("=")[1].trim();
				switch (type) {
					case "register":	
						if(CQLHandler.register_user_final(uname, token)){				
							he.sendResponseHeaders(201, -1);
						} else{
							he.sendResponseHeaders(408, -1);	
						}
						he.close();
						return;
					case "resetpswd":
						if(CQLHandler.resetPassword(uname,token)){
							he.sendResponseHeaders(226, -1);
						}else{
							he.sendResponseHeaders(410, -1);
						}
						he.close();
						return;
					default:
						break;
					}							
			}
			if(cookie !=null){
				Headers h = he.getResponseHeaders();
				h.set("Set-Cookie", Config.getDeleteCookie(cookie));			
				he.sendResponseHeaders(401, -1);
			}else{
				he.sendResponseHeaders(200, -1);
			}						
			he.close();	
			break;
		case "/login/resetpswd":
			String uname = he.getRequestURI().getQuery().split("=")[1].trim();
			Headers h = he.getResponseHeaders();
			h.set("Cache-Control", "no-cache");
			h.set("Connection", "keep-alive");					
			if (CQLHandler.canResetPassword(uname)) {
				he.sendResponseHeaders(200, -1);
			}else{
				he.sendResponseHeaders(412, -1);	
			}			
			he.close();
			break;
		case "/login/register":
			tokens = URLDecoder.decode(he.getRequestURI().getQuery(),"UTF-8").split("&");
			uname = tokens[0].split("=")[1].trim();
			String email_id = tokens[1].split("=")[1].trim();
			String pwd = tokens[2].split("=")[1].trim();
			String role = tokens[4].split("=")[1].trim();
			if (CQLHandler.register_user(uname, email_id, pwd, role)){
				he.sendResponseHeaders(200, -1);
			} else {
				he.sendResponseHeaders(409, -1);
			}
			he.close();
			break;
		case "/login/new_account":
			Utility.sendResponse("new_account.htm", he);
			break;
		default:
			Utility.sendResponse("Internal_error.htm", he);
			break;
		}
	}

	private String[] getPostData(HttpExchange he) throws IOException{
		BufferedReader bufr = new BufferedReader(new InputStreamReader(
				he.getRequestBody()));
		String[] readData = bufr.readLine().split("&");
			bufr.close();
			return readData;		 
	}
	
	
	private void sendLoginAccepted(String url, HttpExchange he,	String cookie) throws IOException {
		Headers h = he.getResponseHeaders();
		if(cookie!=null)
			h.set("Set-Cookie", cookie);
		byte[]data = url.getBytes();
		he.sendResponseHeaders(201, data.length);
		OutputStream os = he.getResponseBody(); 
		os.write(data);
		os.flush();
		os.close();
		he.close();
	}
	
	private void sendRefreshHeaders(String refreshurl, HttpExchange he) throws IOException{
		sendRefreshHeaders(refreshurl, he,null);
	}
	
	private void sendRefreshHeaders(String refreshurl, HttpExchange he, String cookie) throws IOException{		
		Headers h = he.getResponseHeaders();	
		if(cookie!=null)
			h.set("Set-Cookie", cookie);
		h.set("Refresh", "0; url=" + refreshurl);		
		he.sendResponseHeaders(200, -1);
		he.close();
	}
	
	private String getNewCookie(String uname, String role) {
		String uuid = UUID.randomUUID().toString();
		String cookie;
		try {
			cookie = Config.getNewCookie(uuid);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return null;
		}
		String Key = "SID="+ uuid;
		CookieData.addCookie(Key, role, uname);
		return cookie;
	}

}
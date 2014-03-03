package com.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class SecureCommHandler implements HttpHandler {

	static Logger logger = Logger.getLogger(SecureCommHandler.class);
	
	public void handle(HttpExchange he) throws IOException {
		String cookie = Utility.getCookie("SID", he.getRequestHeaders());
		CookieData cd = CookieData.getCookieData(cookie);
		
		String requestMethod = he.getRequestMethod();
		
		logger.info(requestMethod + ": " 
					+ he.getRequestURI().getPath() 
					+ " cookie: " + cookie
					+ " validity: " + (cd != null));
		
		if(cookie == null ){
			sendRefreshHeaders("/login", he);
		}else if(cookie !=null && cd == null){
			sendRefreshHeaders("/login", he);
		}else{
			switch (requestMethod) {
			case "GET":
				handleGETRequests(he, cookie, cd);			
				break;
			case "POST":
				handlePOSTRequests(he, cookie, cd);
				break;
			default:
				break;
			}			
		}
	}

	private void sendRefreshHeaders(String refreshurl, HttpExchange he) throws IOException{		
		Headers h = he.getResponseHeaders();	
		h.set("Refresh", "0; url=" + refreshurl);		
		he.sendResponseHeaders(403, -1);
		he.close();
	}

	private void handlePOSTRequests(HttpExchange he, String cookie, CookieData cd) throws IOException {
		String requestPath = he.getRequestURI().getPath();
		String user_name = cd.getUserName();
		BufferedReader bufr ;
		String[] data;
		StringBuilder builder;
		String guid;
		switch (requestPath) {
		case "/secure/change_security/sq":
			bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
			data = URLDecoder.decode(bufr.readLine(), "UTF-8").split("&");
			if(CQLHandler.updateSQQ(user_name, data[0].split("=")[1].trim()
					, data[1].split("=")[1].trim()
					, data[2].split("=")[1])){
				he.sendResponseHeaders(200, -1);				
			}else{
				he.sendResponseHeaders(412, -1);
			}
			he.close();
			break;
		case "/secure/edit_profile/update_photo":
			try{
				String fname = he.getRequestHeaders().get("X_FILENAME").get(0);
				String fsize = he.getRequestHeaders().get("Content-Length").get(0);
				String fpath = Utility.handleImageUpload(fname, fsize,he.getRequestBody(), user_name);
				
				if(fpath == null){
					he.sendResponseHeaders(412, -1);					
				}else{					
					byte[] path = fpath.getBytes("UTF-8");
					he.sendResponseHeaders(200, path.length);
					he.getResponseBody().write(path);
					he.getResponseBody().flush();					
				}
			}catch (Exception e) {
				e.printStackTrace();
				he.sendResponseHeaders(412, -1);
			}
			he.close();
			break;
		case "/secure/contacts/new":
			bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
			String contact_name = bufr.readLine().trim();
			bufr.close();
			if(CQLHandler.isUserExists(contact_name)){
				CQLHandler.addNewContact(user_name,contact_name);
				he.sendResponseHeaders(200, -1);
			}else{				
				he.sendResponseHeaders(412, -1);	
			}			
			he.close();
			break;
		case "/secure/contacts/delete":
			bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
			contact_name = bufr.readLine().trim();
			bufr.close();
			CQLHandler.deleteContact(user_name, contact_name);
			he.sendResponseHeaders(200, -1);			
			he.close();
			break;
		case "/secure/notification_settings/update":
			bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
			data = URLDecoder.decode(bufr.readLine(), "UTF-8").split("&");
			builder = new StringBuilder();
			builder.append("{");
			for(int i= 0; i< data.length; i++){				
				String[] toAppend = data[i].split("=");
				builder.append("'")
					.append(toAppend[0])
					.append("':")
					.append(toAppend[1]);
				if(i < (data.length - 1)){
					builder.append(",");
				}
			}
			builder.append("}");
			CQLHandler.setNotificationSettings(user_name, builder);
			he.sendResponseHeaders(200, -1);
			he.close();
			break;
		case "/secure/sendmail/mail":
			bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
			Headers h = he.getRequestHeaders();			
			guid = h.get("guid").get(0).trim();
			String mailTo = h.get("to").get(0).trim();
			String subject = h.get("subject").get(0).trim();
			boolean ap = Boolean.valueOf(h.get("ap").get(0).trim());
			String message = bufr.readLine();
			bufr.close();
			long key = MailServer.sendMail(user_name, guid, mailTo, subject, message,ap);
			if(key == MailServer.errorNotExists){
				he.sendResponseHeaders(412, -1);
			}else if(key == MailServer.errorMailBoxFull){
				he.sendResponseHeaders(409, -1);
			}
			else{
				sendReponseData(he,String.valueOf(key).getBytes("UTF-8"), 200);	
			}			
			he.close();
			break;
		case "/secure/sendmail/mail/attachment":
			try{
				String fileName = he.getRequestHeaders().get("X_FILENAME").get(0);
				long fileSize = Long.valueOf(he.getRequestHeaders().get("Content-Length").get(0));
				data = he.getRequestHeaders().get("X_CONT").get(0).split("&");
				guid = data[0].split("=")[1].trim();
				ap = Boolean.valueOf(data[1].split("=")[1].trim());
				int index = Integer.valueOf(data[2].split("=")[1].trim());
				long tag = Long.valueOf(he.getRequestHeaders().get("TAG").get(0));
				if(MailServer.processAttachment(user_name, guid, ap, he.getRequestBody(), fileName, fileSize,index, tag)){
					he.sendResponseHeaders(200, -1);
				}else{
					he.sendResponseHeaders(412, -1);
				}				
			}catch (Exception e) {								
				e.printStackTrace();
				he.sendResponseHeaders(412, -1);
			}
			he.close();
			break;
		case "/secure/edit_profile/update":
			bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
			data = URLDecoder.decode(bufr.readLine(), "UTF-8").split("&");
			builder = new StringBuilder();
			builder.append("{");
			for(int i = 0 ; i< data.length; i++ ){
				String keyvalue[] = data[i].split("=");
				if(keyvalue[0].contentEquals("caddr") 
						|| keyvalue[0].contentEquals("baddr") 
						|| keyvalue[0].contentEquals("note")){
					
					keyvalue[1] = keyvalue[1].replace("\r\n", "&");
				}
				builder.append("'")
					.append(keyvalue[0])
					.append("':'")
					.append(keyvalue[1])
					.append("'");
				if( i < (data.length - 1)){
					builder.append(",");
				}
			}
			builder.append("}");
			System.out.println(builder.toString());
			CQLHandler.updateProfileData(user_name, builder);			
			he.sendResponseHeaders(200, -1);			
			break;
		default:
			he.close();
			break;
		}
		
	}

	private void handleGETRequests(HttpExchange he, String cookie, CookieData cd) throws UnsupportedEncodingException, IOException {
		String requestPath = he.getRequestURI().getPath();
		requestPath = requestPath.substring(requestPath.indexOf('/',2));
		String user_name = cd.getUserName();
		
		byte[] data;
		switch (requestPath) {
		case "/view_profile":
			Utility.sendResponse(cd.getSecureFilePath(requestPath), he);
			break;
		case "/edit_profile":
			Utility.sendResponse(cd.getSecureFilePath(requestPath), he);
			break; 
		case "/view_profile/show" :
			sendReponseData(he, CQLHandler.getProfileDataAndJsonify(user_name), 200);
			he.close();
			break;
		case "/contacts/view":
			data = CQLHandler.viewContactsList(user_name);
			if(data == null){
				he.sendResponseHeaders(412, -1);
			}else{
				sendReponseData(he, data, 200);	
			}			
			he.close();
			break;
		case "/view_profile/limited/show" :
			String query = he.getRequestURI().getQuery();
			String uid = null;
			if(query != null && query.length() > 2){
				uid = query.split("=")[1].trim();
			}else{
				uid = user_name;
			}
			data = CQLHandler.getLimitedProfileDataAndJsonify(uid);
			if(data == null){
				he.sendResponseHeaders(412, -1);
			}else{
				sendReponseData(he, data, 200);	
			}			
			he.close();
			break;
		case "/change_security":
			Utility.sendResponse(cd.getSecureFilePath(requestPath), he);
			break;
		case "/notification_settings":
			Utility.sendResponse("/seller/notification_settings.htm", he);
			break;
		case "/notification_settings/show":
			byte[] toReturn = CQLHandler.getNotificationSettings(user_name);
			sendReponseData(he, toReturn, 200);
			break;
		case "/change_security/irp" :
			if(CQLHandler.canResetPassword(user_name)){
				he.sendResponseHeaders(200, -1);
			}else{
				he.sendResponseHeaders(412, -1);
			}
			he.close();
			break;
		case "/community":
			Utility.sendResponse(cd.getSecureFilePath(requestPath), he);
			break;
		case "/mail_box":
			String[] querydata = he.getRequestURI().getQuery().split("&");
			switch (querydata[0].split("=")[1].trim()) {
			case  "show":
				data = MailServer.fetchMails(user_name, querydata[1].split("=")[1].trim());
				if(data == null) {
					he.sendResponseHeaders(412, -1);
				}else{
					sendReponseData(he, data, 200);
				}
				break;
			case "delete":
				String folder = querydata[1].split("=")[1].trim();
				String guid = querydata[2].split("=")[1].trim();
				long ts = Long.valueOf(querydata[3].split("=")[1].trim());
				logger.debug(user_name + " " +  guid + " " +  folder+ " " + ts );
				MailServer.deleteMail(user_name, guid, folder,ts);
				he.sendResponseHeaders(200, -1);	
				break;
			case "archived":
				folder = querydata[1].split("=")[1].trim();
				guid = querydata[2].split("=")[1].trim();
				ts = Long.parseLong(querydata[3].split("=")[1].trim());
				MailServer.archiveMail(user_name, guid, folder,ts);
				he.sendResponseHeaders(200, -1);				
				break;
			case "view_conversation":
				guid = querydata[1].split("=")[1].trim();
				MailServer ms = new MailServer();
				data = ms.view_conversation(user_name, guid);
				if(data == null) {
					he.sendResponseHeaders(412, -1);
				}else{
					sendReponseData(he, data, 200);
				}				
				break;
			case "view_chat_log":
				guid = querydata[1].split("=")[1].trim();
				data = MailServer.view_ChatLogs(user_name, guid);
				if(data == null) {
					he.sendResponseHeaders(412, -1);
				}else{
					sendReponseData(he, data, 200);
				}
				break;
			case "toggle_read":
				guid = querydata[1].split("=")[1].trim();
				ts = Long.parseLong(querydata[2].split("=")[1].trim());
				MailServer.toggle_read(user_name, guid, ts);
				he.sendResponseHeaders(200, -1);
				break;
			default:
				break;
			}
			he.close();
			break;
		default:
			if( requestPath.endsWith(".jpg") || requestPath.endsWith(".jpeg") || requestPath.endsWith(".png")){
				Utility.sendImage(he);
			}else{
				sendRefreshHeaders(cd.getRedirectPath(), he);	
			}
			break;
		}		
	}
	
	private void sendReponseData(HttpExchange he, byte[] toReturn, int statusCode) throws IOException {		
		OutputStream os = he.getResponseBody();
		he.sendResponseHeaders(statusCode, toReturn.length);
		os.write(toReturn);
		os.flush();
		os.close();		
	}
	

}

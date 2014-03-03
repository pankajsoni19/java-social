package com.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

import com.google.common.io.Files;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

@SuppressWarnings("restriction")
public class Utility {	
	
	static Logger logger = Logger.getLogger(Utility.class);
	
	
	private static Headers getResponseHeaders(HttpExchange he) {
		Headers h = he.getResponseHeaders();
		h.set("Cache-Control", "no-cache");
		h.set("Connection", "keep-alive");
		h.set("Content-Type", "text/html");
		h.set("Content-Encoding", "gzip");
		return h;
	}
	
	public static void sendResponse(String fileName, HttpExchange he) {
		try {
			sendResponse(fileName, he, null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	
	public static String handleImageUpload(String fname, String fsize,
			InputStream requestBody, String user_name) throws IOException {
		return handleImageUpload(fname, fsize, requestBody, user_name, null);
	}
	
	public static String handleImageUpload(String filename, String fileSize, 
			InputStream inputStream, String username, String pname) throws IOException{
		System.out.println(filename + " " + fileSize + " " + username + " " + pname);
		int index = filename.lastIndexOf('.');
		String extension  = filename.substring(index);
		Long fSize = Long.valueOf(fileSize);
		if(fSize > Config.max_image_size){
			return null;
		}
		String imagePath = null;
		if(pname == null){
			imagePath = CQLHandler.getUserImage(username);
		} else {
			imagePath = CQLHandler.getSellerProductImage(username,pname);
		}
		String extensionInDb = null;
		// The data is not in db
		if(imagePath == null){
			StringBuilder strbuilder = new StringBuilder();
			strbuilder.append(UUID.randomUUID().toString()).append(UUID.randomUUID().toString());
			for(int i = 2; i< 42; i = i+6){
				strbuilder.insert(i, '/');	
			}
			strbuilder.append(extension);
			imagePath = strbuilder.toString();
		}else{
			index = imagePath.lastIndexOf('.');
			extensionInDb  = imagePath.substring(index);
		}
		
		BufferedInputStream bufis = new BufferedInputStream(inputStream);
		String filePath = Config.getImageFilePath(imagePath);		
		
		if(extensionInDb !=null){
			File f = new File(filePath);
			f.delete();
		}else{
			index = filePath.lastIndexOf('/');
			File f = new File(filePath.substring(0, index+1));
			f.mkdirs();
		}
		
		BufferedOutputStream bufos = new BufferedOutputStream(new FileOutputStream(filePath, false));
		byte buffer[] = new byte[2048];
		int read= 0;
		while((read = bufis.read(buffer))!=-1){
			bufos.write(buffer, 0, read);
			bufos.flush();
		}
		bufos.close();
		bufis.close();
		if(extensionInDb== null || (extensionInDb !=null && !extensionInDb.contentEquals(extension))){
			if(pname ==null){
				CQLHandler.updateUserPhoto(username, imagePath);
			}else{
				CQLHandler.updateProductPhoto(username, pname, imagePath);	
			}	
		}		
		return imagePath;
	}
	
	public static void sendResponse(String fileName, HttpExchange he,String cookie) throws IOException {
		File f = null;
		if(fileName.endsWith(".htm")){
			f = new File(Config.getWWW_Folder() + fileName);
		}else{
			f = new File(Config.getStaticFilePath(fileName));
		}
		logger.debug("Sending file by name " + Config.getWWW_Folder() + fileName );
        Headers h = getResponseHeaders(he);
        if(cookie!=null){
        	h.set("Set-Cookie", cookie);
        }
		
		he.sendResponseHeaders(200, 0);
		GZIPOutputStream gos = new GZIPOutputStream(he.getResponseBody());
		BufferedInputStream bufread = new BufferedInputStream(
				new FileInputStream(f));
		long toRead = f.length();
		byte buffer[] = new byte[2048];
		int dataread = 0;
		while (toRead > 0 && (dataread = bufread.read(buffer)) != -1) {			
			gos.write(buffer, 0, dataread);
			gos.flush();
			toRead = toRead - dataread;
		}
		bufread.close();
		gos.finish();
		gos.close();
		he.close();
	}
	
	public static String getRecievedMailText(String from, String subject){
		return 
				"You got a new mail from: " + from + "\r\n" + "\r\n"   
				+"\r\n" + "Subject: " + subject	+ "\r\n"
				+ "Signin to access your mail: " + Config.getHost() + "/login"
				+"\r\n\r\n";
	}
	public static String getPasswordRecoveryMailText(String url) {
		return 
				"Click the link to reset your password" + "\r\n" + "\r\n"   
				+ url
				+"\r\n" + "This link would expire in " + (Config.password_reset_link_expiry_time/(1*60*60*1000)) + " Hours."
				+ "\r\n"
				+ "If password reset request is not from you, then you can ignore this mail.";
	}
	
	public static String getRegistrationConfirmationMailText(String url) {
		return 
				"Click the link to confirm your account" + "\r\n" + "\r\n"   
				+ url
				+"\r\n" + "This link would expire in " + (Config.registration_reset_link_expiry_time/(1*60*60*1000)) + " Hours."
				+ "\r\n"
				+ "If registration reset request is not from you, then you can ignore this mail.";
	}

	public static void sendImage(HttpExchange he) throws IOException {
		String requestPath = he.getRequestURI().getPath();
		int index = requestPath.indexOf('/', 2);
		String imagePath = Config.getImageFilePath(requestPath.substring(index +1));
		Headers h = he.getResponseHeaders();
		h.set("Content-Encoding", "gzip");
		h.set("Connection","keep-alive");						
		h.set("Content-Type",Config.mimeData.get(Files.getFileExtension(imagePath)));
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.MINUTE, 2);
		h.set("Cache-Control","public, max-age=120");
		
		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z");
		dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));		
		String expires = dateFormatGmt.format(calendar.getTime());
		h.set("Expires", expires);
		
		he.sendResponseHeaders(200, 0);
		GZIPOutputStream gos = new GZIPOutputStream(he.getResponseBody());
		File f = new File(imagePath);
		BufferedInputStream bufread = new BufferedInputStream(new FileInputStream(f));
		
		byte[] buffer = new byte[2048];
		int bytesRead = 0;
		long toRead = f.length();	
		
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

	public static String getCookie(String startKey, Headers headers) {
		Iterator<Entry<String, List<String>>> I = headers.entrySet().iterator();
		while(I.hasNext()){
			Entry<String, List<String>> E = I.next();
			if(E.getKey().toLowerCase(Locale.ENGLISH).contentEquals("cookie")){
				String returned = getCookieFromSearchString(startKey, E.getValue().get(0));
				if(returned != null){
					return returned;
				}
			}
		}		
		return null;
	}

	public static String getCookieFromSearchString(String startKey, String wholeCookie) {
		if(wholeCookie.contains(";")){
			String data[] = wholeCookie.split(";");
			for(int i=0; i< data.length; i++){
				if(data[i].trim().startsWith(startKey)){
					return data[i].trim();
				}						
			}
		}else if(wholeCookie.startsWith(startKey)){
			return wholeCookie;
		}
		return null;
	}	

}
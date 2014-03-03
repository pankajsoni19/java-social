package com.server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.bidimap.TreeBidiMap;
import org.apache.log4j.Logger;
import org.java_websocket.WebSocket;
import org.yaml.snakeyaml.Yaml;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.BiMap;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public final class Config {

	// public static DelayQueue<ResetPasswordRequest> passwordResetReq = new
	// DelayQueue<ResetPasswordRequest>();
	// Additional config SendMailTLS..
	
	public static final String settings_file_path ="./conf/config.yml";
	public static final int stopDelay = 5; // in seconds
	//public static CQLHandler cqlHandler;
	public static String public_ip;
	public static int public_ip_port;
	public static String domain;
	public static boolean is_secured = false;
	public static String in_cert_store;
	public static char[] in_key_store_password;
	public static char[] in_cert_key_password;
	
	public static boolean client_is_secured = false;
	public static String client_in_cert_store;
	public static char[] client_in_key_store_password;
	public static char[] client_in_cert_key_password;
	
	private static String image_storage_dir ;
	private static String www_folder;
	private static String static_files;
	public static Long max_image_size = (long) (1024 * 512); // 512kb
/*	public static ArrayList<String> staticDirs = new ArrayList<String>();
*/	
	public static String cassuname = "pooja";
	public static String casspwd = "pooja";
	public static String casshost = "192.168.1.42";
	public static int cassport = 9042;
	
	public static int httpport = 80;
	public static boolean isHTTPSsupported = false ;
	public static int httpsport = 443;	
	public static int websocketserverport = 8443;
	public static String cert_store = "keystore.jks";
	public static char[] key_store_password = "rootpass".toCharArray();
	public static char[] cert_key_password = "rootpass".toCharArray();
	
	public static int maximum_backlog = 10;
	public static long cookie_expiry_time = 10 * 60 * 1000; //10 mins in ms.  //browser will take care of this;
	public static long password_reset_link_expiry_time = 2 * 60 * 60 * 1000; // in ms total 2 hrs. for email
	public static long registration_reset_link_expiry_time = 24 * 60 * 60 * 1000; // in ms total 1 day for new user registration
	public static int max_thread_pool_size = 128;
	
	protected static String email_username;
	protected static String email_pwd;
	protected static String email_from_addr;
	protected static String smtp_host;
	protected static int smtp_port;
	protected static long email_storage_expiry_time = 30 * 24 * 60 * 60; // in seconds 
	protected static String email_attachment_storage_dir;
	protected static long email_attachment_max_size = 1024 * 512; // in bytes
	public static final int smtp_sending_pool_size = 1;
	public static final int max_sending_pool_size = 1;
	public static long executor_keep_alive_time = 30;
	public static boolean isSSLSMTP = false;
	public static long mail_box_size;
	
	protected static boolean maintain_chat_history = false;	
	
	public static HashMap<String, String> mimeData = new HashMap<>();
	
	
	static Logger logger = Logger.getLogger(SendMailTLS.class);
	
	public static void init() throws IOException, MessagingException{  
		readSettings();
		load_mimetypes();
	}
	
	public static String getWWW_Folder(){
		return www_folder;
	}
	
	public static String getImageFilePath(String path){
		return image_storage_dir + path;
	}
	
	public static String getStaticFilePath(String path){
		return static_files + path;
	}
	
	public static void removeCookie(String cookie) {
		String uname= CookieData.getUserName(cookie);
		if(uname == null) return;
		ChatServer.doClose(uname);
		CookieData.invalidate(cookie);
	}
	
	private static void load_mimetypes() throws IOException {
		BufferedReader bufr = new BufferedReader(new InputStreamReader(new FileInputStream("./conf/mime_type.data")));
		String lr = null;
		while((lr = bufr.readLine())!=null){
			String mimes[] = lr.split(" ");
			mimeData.put(mimes[1].trim(), mimes[0].trim());
		}
		bufr.close();
	} 	
	
	private static void readSettings() throws IOException {
		Yaml yaml = new Yaml();
		Iterator I = ((Map) yaml.load(new FileInputStream(settings_file_path))).entrySet().iterator();
		 
		//BufferedReader bufr = new BufferedReader(new InputStreamReader(new FileInputStream(settings_file_path)));
		while(I.hasNext()){
			Entry E = (Entry) I.next();
			match((String) E.getKey(), E.getValue());			 
		}
	}
		
	private static void match(String key, Object Value) throws NumberFormatException, IOException{
			switch (key) {
				case "casshost":
					casshost = (String) Value;
					break;
				case "cassport":
					cassport = (int) Value;
					break;
				case "cassuname":
					cassuname = (String) Value;
					break;
				case "casspwd":
					casspwd = (String) Value;
					break;
				case "httpport":
					httpport = (int) Value;
					break;
				case "websocketserverport":
					websocketserverport = (int) Value;
					break;
				case "maximum_backlog":
					maximum_backlog = (int) Value;
					break;
				case "image_storage_dir":
					image_storage_dir = ((String)Value).concat("/");
					break;
				case "max_image_size":
					max_image_size = new Long((int)Value);	
					break;
				case "max_thread_pool_size":
					max_thread_pool_size = (int) Value;
					break;
				case "public_ip":
					public_ip = (String) Value;
					break;
				case "public_ip_port":
					public_ip_port = (int) Value;
					break;
				case "domain":
					domain = (String) Value;
					break;
					
				case "is_secured":
					is_secured = (boolean) Value;
					break;
				case "in_cert_store":
					in_cert_store = (String) Value;
					break;
				case "in_key_store_password":
					in_key_store_password = ((String)Value).toCharArray();
					break;
				case "in_cert_key_password":
					in_cert_key_password = ((String)Value).toCharArray();
					break;					
				case "client_is_secured":
					client_is_secured = (boolean) Value;
					break;
				case "client_in_cert_store":
					client_in_cert_store = (String) Value;
					break;
				case "client_in_key_store_password":
					client_in_key_store_password = ((String)Value).toCharArray();
					break;
				case "client_in_cert_key_password":
					client_in_cert_key_password = ((String)Value).toCharArray();
					break;					
				case "unique_identifier_for_this_server":
					S2s_communicator.setServerId((String)Value);
					break;
				case "https_server":
					isHTTPSsupported = (boolean) Value;
					break;
				case "httpsport":
					httpsport = (int) Value;
					break;
				case "cert_store":
					cert_store = (String) Value;
					break;
				case "key_store_password":
					key_store_password = ((String)Value).toCharArray();
					break;
				case "cert_key_password":
					cert_key_password = ((String)Value).toCharArray();
					break;
				case "cookie_expiry_time":
					cookie_expiry_time = new Long((int)Value);	
					break;
				case "password_reset_link_expiry_time":
					password_reset_link_expiry_time = new Long((int)Value);	
					break;
				case "registration_reset_link_expiry_time":
					registration_reset_link_expiry_time = new Long((int)Value);	
					break;
				case "email_username":
					email_username = (String) Value;
					break;
				case "email_pwd":
					email_pwd = (String) Value;
					break;
				case "email_from_addr":
					email_from_addr = (String) Value;
					break;
				case "smtp_host":
					smtp_host = (String) Value;
					break;
				case "smtp_port":
					smtp_port = (int) Value;
					break;
				case "email_attachment_storage_dir":
					email_attachment_storage_dir = Value + "/";
					break;
				case "www_folder":
					www_folder = Value + "/";
					break;
				case "email_storage_expiry_time": 
					email_storage_expiry_time = new Long((int)Value);
					break;
				case "email_attachment_max_size":
					email_attachment_max_size = new Long((int)Value); 
					break;
				case "maintain_chat_history":
					maintain_chat_history = (boolean) Value;
					break;
				case "static_files":
					static_files = (String) Value + "/";
					break;
				case "mail_box_size":
					mail_box_size = new Long((int) Value);
					break;
				case "isSSLSMTP":
					isSSLSMTP = (boolean) Value;
					break;
				default:
					break;
			}		
	}		
	
	public static String getHost() {
		return domain;
	}
	
	public static String getWebSocketHost() throws UnknownHostException{
		return getHost() + ":" + websocketserverport;
	}

	public static String getNewCookie(String uuid) throws UnknownHostException {
		Date date = new Date();
		date.setTime(date.getTime() + cookie_expiry_time);	//
		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z");
		dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));		
		String expires = dateFormatGmt.format(date);
		String secure = "";		
		if (isHTTPSsupported) secure = "Secure";
		String cookie = "SID="+ uuid + "; Expires=" + expires + "; Path=/; Domain="+ Config.getHost() + "; " + secure + " ; HttpOnly" ;
		return cookie;
	}
	
	public static StringBuilder getNewWSCookie(String uuid, StringBuilder strbuilder) throws UnknownHostException {
		Date date = new Date();
		date.setTime(date.getTime() + cookie_expiry_time);	//
		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z");
		dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));		
		String expires = dateFormatGmt.format(date);
		String secure = "";		
		if (isHTTPSsupported) {
			secure = "Secure ;";
		}
		strbuilder.append("WSSID=")
			.append(uuid)
			.append("; Expires=")
			.append(expires)
			.append("; Path=\"/secure/webchat/\"; Domain=")
			.append(Config.getHost()).append("; ").append(secure);
		return strbuilder;				
	}

	public static String getDeleteCookie(String cookie) throws UnknownHostException {
		Date date = new Date();
		date.setTime(date.getTime() - cookie_expiry_time);	//
		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z");
		dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));		
		String expires = dateFormatGmt.format(date);
		String secure = "";		
		if (isHTTPSsupported) secure = "Secure";
		String toReturn = "SID=deleted; Expires=" + expires  + "; Path=/; Domain="+ Config.getHost() + "; " + secure + " ; HttpOnly" ;		
		return toReturn;
	}

	public static void updateContextHandlers(HttpServer httpServer) {		
		httpServer.createContext("/login", new LoginHandler());
		httpServer.createContext("/js/", new ServeStaticFiles());
		httpServer.createContext("/css/", new ServeStaticFiles());
		httpServer.createContext("/img/", new ServeStaticFiles());
		httpServer.createContext("/docs/", new ServeStaticFiles());
		httpServer.createContext("/public_docs/", new ServeStaticFiles());
		httpServer.createContext("/photos/", new ServeStaticFiles());
		httpServer.createContext("/email/",new ServeStaticFiles());
		httpServer.createContext("/buyer/",new BuyerHandler());	
		httpServer.createContext("/seller/",new SellerHandler());
		httpServer.createContext("/secure/",new SecureCommHandler());		
		httpServer.createContext("/", new CatchAll());
	}

	public static String getEmailAttachmentPath(String path) {
		return email_attachment_storage_dir + path;		
	}

	public static String getWebSocketOrigin() throws UnknownHostException {
		if(isHTTPSsupported){
			return "https://" + Config.getHost();
		}
		return "http://" + Config.getHost();
	}

}

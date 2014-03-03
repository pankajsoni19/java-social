package com.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;

import org.apache.log4j.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
@SuppressWarnings("restriction")
public class SellerHandler implements HttpHandler {

	static Logger logger = Logger.getLogger(SellerHandler.class);
	
	@Override
	public void handle(HttpExchange he) throws IOException {
		String cookie = Utility.getCookie("SID", he.getRequestHeaders());
		System.out.println(cookie);
		CookieData cd = CookieData.getCookieData(cookie);
		
		String requestMethod = he.getRequestMethod();
		
		logger.info(requestMethod + ": " 
					+ he.getRequestURI().getPath() 
					+ " cookie: " + cookie
					+ " validity: " + (cd !=null));
		
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
	
	private String handlePOSTRequests(HttpExchange he, String cookie, CookieData cd) throws IOException {
		String requestPath = he.getRequestURI().getPath().toLowerCase();
		BufferedReader bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
		final String username = cd.getUserName(cookie);
		StringBuilder builder ;
		String pname = null;
		switch (requestPath) {
		case "/seller/modify_product/update_photo":
			try{
				String fname = he.getRequestHeaders().get("X_FILENAME").get(0);
				String fsize = he.getRequestHeaders().get("Content-Length").get(0);
				pname = he.getRequestHeaders().get("X_PNAME").get(0);
				String fpath = Utility.handleImageUpload(fname, fsize,he.getRequestBody(), username, pname);
				
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
		case "/seller/modify_product":
			
			pname = he.getRequestHeaders().get("X_PNAME").get(0);
			String[] data = URLDecoder.decode(bufr.readLine(),"UTF-8").split("&");
			builder = new StringBuilder();
			builder.append("UPDATE application_data.product_listing SET ");
			for(int i = 0; i< data.length; i++){
				String[] pdata  =data[i].split("=");
				
				builder.append(pdata[0].trim()).append("=");
				switch (pdata[0]) {
					case "cil":
					case "icoff":
						builder.append(pdata[1].trim());
						break;
					case "note":
						pdata[1] = pdata[1].replace("\r\n", "&");
					default:
						builder.append("'")
						.append(pdata[1].trim())
						.append("'");						
						break;
				};
				if(i < (data.length -1)){
					builder.append(",");			
				}
			}
			System.out.println(builder.toString());
			builder.append(" WHERE user_name='").append(username).append("' AND pname='").append(pname).append("';");
			CQLHandler.executeQuery(builder.toString());
			he.sendResponseHeaders(200, -1);
			he.close();
			break;
		case "/seller/add_product":			
			data = URLDecoder.decode(bufr.readLine(),"UTF-8").split("&");
			builder  = new StringBuilder();
			builder.append("INSERT INTO application_data.product_listing")
			.append("(user_name, pname, srid, cil, icoff, stype, price, note, pop)")
			.append("VALUES ('")
			.append(username)
			.append("',");
			for(int i = 0; i< data.length; i++){
				
				String[] pdata  =data[i].split("=");
				
				switch (pdata[0]) {
					case "cil":
					case "icoff":
						builder.append(pdata[1].trim());
						break;
					case "note":
						pdata[1] = pdata[1].replace("\r\n", "&");
					default:
						builder.append("'")
						.append(pdata[1].trim())
						.append("'");						
						break;
				};
				
				builder.append(",");
			}
			builder.append("'1.0');");
			CQLHandler.executeQuery(builder.toString());
			he.sendResponseHeaders(200, -1);
			he.close();
			break;
		default:
			break;
		}
		return null;
	}

	private String[] getData(HttpExchange he) throws IOException{
		BufferedReader bufr = new BufferedReader(new InputStreamReader(he.getRequestBody()));
		String data = URLDecoder.decode(bufr.readLine(),"UTF-8");
		logger.debug("data : " + data);
		System.out.println("Data: "  +data);
		bufr.close();
		return data.split("&");
	}
	
	private String handleGETRequests(HttpExchange he, String cookie, CookieData cd) throws IOException {
		String requestPath = he.getRequestURI().getPath().toLowerCase();
		byte[] data;
		switch (requestPath) {
		case "/seller/home":
			Utility.sendResponse("/seller/home.htm", he);
			break;
		case "/seller/add_product":
			Utility.sendResponse("/seller/add_product.htm", he);
			break;
		case "/seller/modify_product":
			Utility.sendResponse("/seller/modify_product.htm", he);
			break;
		case "/seller/view_product":
			String pname = he.getRequestURI().getQuery().split("=")[1].trim();
			String toReturn = CQLHandler.getSellerProduct(cd.getUserName(cookie), pname);
			if(toReturn == null) {				
				he.sendResponseHeaders(404, -1);
			}else{
				sendReponseData(he,toReturn.getBytes(), 200);
			}
			he.close();
			break;
		case "/seller/product/comments":
			String product_name = he.getRequestURI().getQuery().split("=")[1].trim();
			System.out.println("product_name" + product_name);
			data = CQLHandler.getProductCommentsAndJsonify(product_name);
			if(data == null){
				he.sendResponseHeaders(412, -1);
			}else{
				sendReponseData(he, data, 200);
			}
			he.close();
			break;
		case "/seller/overview":
			data = CQLHandler.getSellerProductsAndJsonify(cd.getUserName(cookie));			
			if(data == null){
				he.sendResponseHeaders(412, -1);
			}else{
				sendReponseData(he, data, 200);
			}
			he.close();
			break;
		case "/seller/modify_product/delete":
			String username= cd.getUserName(cookie);
			pname = he.getRequestURI().getQuery().split("=")[1].trim();
			try {
				CQLHandler.deleteSellerProduct(username,pname);
				he.sendResponseHeaders(200, -1);
			} catch (Exception e) {
				he.sendResponseHeaders(500, -1);
			}
			he.close();
			break;
		default:
			if( requestPath.endsWith(".jpg") || requestPath.endsWith(".jpeg") || requestPath.endsWith(".png")){
				Utility.sendImage(he);
			}else{
				sendRefreshHeaders("/seller/home", he);	
			}
			break;
		}
		
		return null;
	}

	private void sendReponseData(HttpExchange he, byte[] toReturn, int statusCode) throws IOException {		
		OutputStream os = he.getResponseBody();
		he.sendResponseHeaders(statusCode, toReturn.length);
		os.write(toReturn);
		os.flush();
		os.close();		
	}
	
	private void sendRefreshHeaders(String refreshurl, HttpExchange he) throws IOException{		
		Headers h = he.getResponseHeaders();	
		h.set("Refresh", "0; url=" + refreshurl);		
		he.sendResponseHeaders(403, -1);
		he.close();
	}		
}

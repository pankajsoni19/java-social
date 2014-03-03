package com.server;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.datastax.driver.core.Row;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class CookieData {

	private long creationTime;
	private String uname;
	private String role = null ; //by default
	
	private static Cache<String, CookieData> cookieStore = CacheBuilder.newBuilder()
						.expireAfterWrite(10000, TimeUnit.MILLISECONDS)
						.concurrencyLevel(4)
						.maximumSize(1024)
						.build();	
	
	public CookieData(String uname2, String role) {
		uname = uname2;
		this.role = role;
		creationTime = System.currentTimeMillis();
	}

	public boolean isValid() {
		if ((System.currentTimeMillis() - creationTime) < Config.cookie_expiry_time)
			return true;
		return false;
	}

	public String getUserName() {
		return uname;
	}

	public String getRedirectPath(){
		return getPath(role);
	}
	
	public static String getPath(String roleType) {
		switch (roleType) {
		case "buyer":
			return "/buyer/home";
		case "seller":
			return "/seller/home";
		case "admin":
			return "/admin/home";
		default:
			return "/buyer/home";
		}
	}

	public String getSecureFilePath(String requestPath) {
		StringBuilder builder = new StringBuilder();
		builder.append(role).append(requestPath).append(".htm");
		return builder.toString();
	}

	public static StringBuilder getNotificationSettings(String role, StringBuilder builder) {
		//TODO: add notifications for others.
		switch (role) {
		case "buyer":
			break;
		case "seller":
			builder.append("{ 'getmail' : true, 'productbought':true, 'inventorylow':true }");
			break;
		case "admin":
			break;
		default:
			break;
		}
		return builder;
	}

	public static void addCookie(String key, String role2, String uname2) {
		long ttl = Config.cookie_expiry_time /1000; //in seconds
		StringBuilder query = new StringBuilder();
		query.append("INSERT INTO application_data.cookie_data (cookie, role, user_name) VALUES ('")
			.append(key).append("','").append(role2).append("','").append(uname2).append("') USING TTL ")
			.append(ttl).append(" ;");
		CQLHandler.executeQuery(query.toString());		
	}

	public static CookieData getCookieData(String cookie) {
		if(cookie == null) return null;
		
		CookieData cd = cookieStore.getIfPresent(cookie);
		if(cd != null) return cd;	
		
		StringBuilder query = new StringBuilder();
		query.append("SELECT role, user_name FROM application_data.cookie_data WHERE cookie='").append(cookie).append("';");
		List<Row> rs = CQLHandler.executeQuery(query.toString());
		if(rs == null) return null;
		Row r = rs.get(0);
		cd = new CookieData(r.getString("user_name"), r.getString("role"));
		cookieStore.asMap().put(cookie, cd);
		return cd;		
	}

	public static String getUserName(String cookie) {
		return getCookieData(cookie).getUserName();
	}

	public static void invalidate(String cookie) {
		StringBuilder query = new StringBuilder();
		query.append("DELETE FROM application_data.cookie_data WHERE cookie='").append(cookie).append("';");
		CQLHandler.executeQuery(query.toString());		
	}
}

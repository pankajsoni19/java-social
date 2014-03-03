package com.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.log4j.Logger;
import java.util.Set;
import java.util.UUID;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.JsonParserSequence;
import org.yaml.snakeyaml.emitter.Emitable;

import com.codahale.metrics.Reservoir;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Delete;

public class CQLHandler {
	
	private static Cluster cluster;
	private static Session session;
		
	public static void start(){
		connect(Config.casshost);
	}
	
	static Logger logger = Logger.getLogger(CQLHandler.class);
	
	public static void connect(String node) {
	   cluster = Cluster.builder()
	         .addContactPoint(node)
	         .withCredentials(Config.cassuname, Config.casspwd)
	         .withPort(Config.cassport)
	         // .withSSL() // Uncomment if using client to node encryption
	         .build();
	   
	   Metadata metadata = cluster.getMetadata();
	   System.out.printf("Connected to cluster: %s\n", 
	         metadata.getClusterName());
	   for ( Host host : metadata.getAllHosts() ) {
	      System.out.printf("Datacenter: %s; Host: %s; Rack: %s\n",
	         host.getDatacenter(), host.getAddress(), host.getRack());
	   }
	   
	   session = cluster.connect();
	}
	
	public Session getSession() {
		return session;
	}
	
	public static void shutdown() {
		session.shutdown();
		cluster.shutdown();
	}
	
	
	public static boolean isUserExists(String uname) {
		ResultSet results = session.execute("SELECT user_name FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row == null) {
			return false;
		}
		return true;
	}
	
	public static boolean register_user(String uname, String email_id, String pwd, String role) throws UnknownHostException {
		ResultSet results = session.execute("SELECT user_name FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row != null) {
			return false;
		}
		String Token = UUID.randomUUID().toString();
		
		StringBuilder builder = new StringBuilder();
		builder.append("INSERT INTO application_data.users ")
		.append("(user_name, password, profile_data, register_token, role, notification_settings)")
		.append("VALUES ('").append(uname)
		.append("','").append(pwd).append("',")
		.append("{ 'email_id':'").append(email_id).append("'},'")
		.append(Token).append("-").append(System.currentTimeMillis())
		.append("','").append(role).append("',");
		builder = CookieData.getNotificationSettings(role, builder);
		builder.append(");");	
		session.execute(builder.toString());	
		String mailerLink = Config.getHost() + "/login?type=register&uname="+ uname +"&register_token=" + Token;
		SendMailTLS smt = new SendMailTLS(email_id, "Registration Confirmation mail", Utility.getRegistrationConfirmationMailText(mailerLink));
		MailServer.addMailToSend(smt);
		return true;
	}
	
	public static boolean register_user_final(String uname, String register_token){
		ResultSet results = session.execute("SELECT register_token FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if(row == null) return false;
		String RegToken = row.getString("register_token");
		if(RegToken != null) {
			if(RegToken.startsWith(register_token) && 
					( Config.registration_reset_link_expiry_time > (System.currentTimeMillis() - new Long(RegToken.split("-")[5].trim())))){
				session.execute("UPDATE application_data.users SET register_token = 'registered' WHERE user_name='" + uname +"';");
				return true;	
			} else if (RegToken.contentEquals("registered")) {				
				return true;
			}   			
		}		
		return false;
	}

	public static boolean canResetPassword(String uname) throws UnknownHostException {
		ResultSet results = session.execute("SELECT profile_data FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row == null) return false;
		Map<String, String> profile_data = row.getMap("profile_data", String.class, String.class);
		String mailID = profile_data.get("email_id");
		if(mailID == null) return false;
		
		String Token = UUID.randomUUID().toString();
		String SessionToken = Token + "-" + System.currentTimeMillis();
		session.execute("UPDATE application_data.users SET pswd_recover_token = '" + SessionToken +"' WHERE user_name='" + uname +"';");
		StringBuilder builder = new StringBuilder();
		builder.append(Config.getHost())
			.append("/changepswd")
			.append("?type=resetpswd&uname=")
			.append(uname)
			.append("&pswd_recover_token=")
			.append(Token);
		String mailerLink = builder.toString(); 
		SendMailTLS smt = new SendMailTLS(mailID, "Password recovery confirmation", Utility.getPasswordRecoveryMailText(mailerLink));
		MailServer.addMailToSend(smt);
		return true;
	}

	public static boolean resetPassword(String uname, String token) {
		ResultSet results = session.execute("SELECT pswd_recover_token FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row == null) return false;
		String SessToken = row.getString("pswd_recover_token");		
		if(SessToken != null 
				&& SessToken.startsWith(token) 
				&& ( Config.password_reset_link_expiry_time > (System.currentTimeMillis() - new Long(SessToken.split("-")[5].trim())))){
			return true;
		}		
		return false;
	}
	

	public static void setNewPassword(String uname, String passwd) {
		session.execute("UPDATE application_data.users SET password = '" + passwd +"' WHERE user_name='" + uname +"';");
	}
	
	public static boolean setNewPassword(String uname, String pswd, String token) {
		ResultSet results = session.execute("SELECT pswd_recover_token FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row == null) return false;
		String SessToken = row.getString("pswd_recover_token");
		if(SessToken != null && SessToken.startsWith(token)){
			session.execute("UPDATE application_data.users SET pswd_recover_token = 'null' , password = '" + pswd +"' WHERE user_name='" + uname +"';");
			return true;
		}		
		return false;
	}

	public static String checkLogon(String uname, String pwd) {
		ResultSet results = session.execute("SELECT register_token, role, password FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if( row == null) return null;
		String readPwd = row.getString("password");
		String register_token = row.getString("register_token");
		String role = row.getString("role");
		if (readPwd != null && readPwd.equals(pwd) && register_token != null && register_token.contains("registered")){
			return role;
		}			
		return null;
	}

	public static byte[] getLimitedProfileDataAndJsonify(String uname) throws UnsupportedEncodingException{
		ResultSet results = session.execute("SELECT profile_data FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if(row == null) return null;
		Iterator<Entry<String, String>> data = row.getMap("profile_data", String.class, String.class).entrySet().iterator();
		StringBuilder builder = new StringBuilder();
		builder.append("{");
		while(data.hasNext()){
			Entry<String, String> E = data.next();
			switch (E.getKey()) {
			case "fname":
			case "lname":
			case "note":
			case "image":
			case "email_id":
				builder.append("\"")
				.append(E.getKey())
				.append("\":\"")
				.append(E.getValue())
				.append("\"");
				if(data.hasNext()){
					builder.append(",");
				 }
				break;
			default:
				break;
			}
		 }	 
		builder.deleteCharAt(builder.length() -1).append("}");
		return builder.toString().getBytes("UTF-8");
	}
	
	public static byte[] getProfileDataAndJsonify(String uname) throws UnsupportedEncodingException{
		
		ResultSet results = session.execute("SELECT profile_data FROM application_data.users WHERE user_name='" + uname +"';");
		Row row = results.one();
		if(row == null) return null;
		Iterator<Entry<String, String>> data = row.getMap("profile_data", String.class, String.class).entrySet().iterator();
		StringBuilder builder = new StringBuilder();
		builder.append("{");
		while(data.hasNext()){
			Entry<String, String> E = data.next();
			builder.append("\"")
				.append(E.getKey())
				.append("\":\"")
				.append(E.getValue())
				.append("\"");
			if(data.hasNext()){
				builder.append(",");
			 }
		 }	 
		builder.append("}");
		return builder.toString().getBytes("UTF-8");		
	}
	
	public static void updateProfileData(String uname, StringBuilder builder) {
		builder.insert(0, "UPDATE application_data.users SET profile_data = profile_data + ")
			.append(" WHERE user_name='")
			.append(uname)
			.append("';");
		session.execute(builder.toString());
	}

	public static void runCommand(String Command) {
			session.execute(Command);		
	}

	public static String getContactsAndJsonify(String uname) {
		ResultSet results = session.execute("Select contact_list from application_data.users where user_name='" + uname+"';");
		Row row = results.one();
		if(row == null) return null;
		Iterator<Entry<String, String>> I = row.getMap("contact_list", String.class, String.class).entrySet().iterator();
		StringBuilder strbuild = new StringBuilder();
		strbuild.append("{");
		int index = 1;
		while(I.hasNext()){
			Entry<String, String> K = I.next();
			String data[] = K.getKey().split("&");
			String data2[] = K.getValue().split("&");
			strbuild
			.append("\"")
			.append(index)
			.append("\":{\"Name\":\"")
			.append(data[0])
			.append(" ")
			.append(data[1])
			.append("\",\"Birthdate\":\"")
			.append(data[2])
			.append("\",\"Gender\":\"")
			.append(data2[0])
			.append("\",\"Address\":\"")
			.append(data2[1])
			.append("\",\"Tel No\":\"")
			.append(data2[2])
			.append("\",\"Email\":\"")
			.append(data2[3])
			.append("\",\"Notes\":\"")
			.append(data2[4]).append("\"}");
			if(I.hasNext()){
				index ++;
				strbuild.append(",");
			}
		}
				
		strbuild.append("}");
		
		return strbuild.toString();	
	}
	
	public static List<Row> executeQuery(String query){
		logger.debug(query);
		ResultSet res = session.execute(query);
		List<Row> rows = res.all();
		if(rows == null || rows.isEmpty()){
			return null;
		}
		return rows;
	}

	public static String getSellerProduct(String username, String pname) {
		StringBuilder builder = new StringBuilder();
		builder.append("SELECT cil, icoff, image, note, srid, price, stype FROM application_data.product_listing where user_name='").append(username);
		builder.append("' AND pname='").append(pname).append("';");
		ResultSet res = session.execute(builder.toString());
		Row row = res.one();
		if(row == null) return null;
		builder = new StringBuilder();
		builder.append("{\"cil\":\"").append(row.getInt("cil"))
		.append("\",\"image\":\"").append(row.getString("image"))
		.append("\",\"icoff\":\"").append(row.getInt("icoff"))
		.append("\",\"note\":\"").append(row.getString("note"))
		.append("\",\"srid\":\"").append(row.getString("srid"))
		.append("\",\"price\":\"").append(row.getString("price"))
		.append("\",\"stype\":\"").append(row.getString("stype"))
		.append("\"}");
		return builder.toString();		
	}

	public static void updateUserPhoto(String username, String imagePath){
		StringBuilder strbuilder = new StringBuilder();
		strbuilder.append("UPDATE application_data.users SET profile_data['image'] = '")
			.append(imagePath)
			.append("' where user_name='")
			.append(username)
			.append("';");
		session.execute(strbuilder.toString());
	}
	
	public static void updateProductPhoto(String username, String pname, String fpath) {
		StringBuilder strbuilder = new StringBuilder();
		strbuilder.append("UPDATE application_data.product_listing SET image ='").append(fpath)
		.append("' WHERE user_name='").append(username).append("' AND pname='").append(pname).append("';");
		session.execute(strbuilder.toString());		
	}

	public static String getSellerProductImage(String username, String pname) {
		StringBuilder builder = new StringBuilder();
		builder.append("SELECT image FROM application_data.product_listing where user_name='").append(username);
		builder.append("' AND pname='").append(pname).append("';");
		ResultSet res = session.execute(builder.toString());
		Row row = res.one();
		if(row == null) return null;
		return row.getString("image");		
	}

	public static String getUserImage(String username) {
		ResultSet res = session.execute("SELECT profile_data FROM application_data.users where user_name='" + username + "';");
		Map<String, String> m = res.one().getMap("profile_data",String.class, String.class);
		return m.get("image");
	}	
	
	public static void deleteSellerProduct(String username, String pname) {
		StringBuilder builder = new StringBuilder();
		builder.append("DELETE FROM application_data.product_listing WHERE user_name='").append(username)
		.append("' AND pname='").append(pname).append("';");
		session.execute(builder.toString());
		builder = new StringBuilder();
		builder.append("DELETE FROM application_data.product_comments WHERE pname='").append(pname).append("';");
		session.execute(builder.toString());
	}

	public static byte[] getSellerProductsAndJsonify(String userName) throws UnsupportedEncodingException {		
		StringBuilder builder = new StringBuilder();
		builder.append("SELECT pname, pop, cil, price, srid, stype FROM application_data.product_listing where user_name='")
		.append(userName).append("';");
		ResultSet res = session.execute(builder.toString());
		 List<Row> rows = res.all();
		 if(rows.size() == 0) return null;
		 builder = new StringBuilder();
		 builder.append("{");
		 for(int i =0; i<rows.size(); i++){
			 Row row = rows.get(i);
			 builder.append("\"").append(i+1).append("\" : {")
			 .append("\"pname\" : \"").append(row.getString("pname"))
			 .append("\",\"pop\" :\"").append(row.getString("pop"))
			 .append("\",\"cil\" :\"").append(row.getInt("cil"))
			 .append("\",\"price\" :\"").append(row.getString("price"))
			 .append("\",\"srid\" :\"").append(row.getString("srid"))
			 .append("\",\"stype\" :\"").append(row.getString("stype"))
			 .append("\"}");
			 if(i < rows.size() - 1){
				 builder.append(",");
			 }
		 }
		 builder.append("}");
		 //System.out.println(builder.toString());
		return builder.toString().getBytes("UTF-8");		
	}

	public static byte[] getProductCommentsAndJsonify(String product_name) throws UnsupportedEncodingException {
		StringBuilder builder = new StringBuilder();
		builder.append("SELECT buyer_id, comment, comment_date, rating FROM application_data.product_comments where pname='")
		.append(product_name).append("';");
		ResultSet res = session.execute(builder.toString());
		 List<Row> rows = res.all();
		 if(rows.size() == 0) return null;
		 builder = new StringBuilder();
		 builder.append("{");
		 for(int i =0; i<rows.size(); i++){
			 Row row = rows.get(i);
			 builder.append("\"").append(i+1).append("\" : {")
			 .append("\"buyer_id\" : \"").append(row.getString("buyer_id"))
			 .append("\",\"comment\" :\"").append(row.getString("comment"))
			 .append("\",\"comment_date\" :\"").append(row.getDate("comment_date").toString())
			 .append("\",\"rating\" :\"").append(row.getString("rating"))
			 .append("\"}");
			 if(i < rows.size() - 1){
				 builder.append(",");
			 }
		 }
		 builder.append("}");
		// System.out.println(builder.toString());
		return builder.toString().getBytes("UTF-8");
	}

	public static boolean updateSQQ(String uname, String sqq, String sqa, String sqp) {
		ResultSet res = session.execute("SELECT password FROM application_data.users WHERE user_name='" + uname + "';");
		String pass = res.one().getString("password");
		if(pass.contentEquals(sqp)){
			StringBuilder builder = new StringBuilder();
			builder.append("UPDATE application_data.users SET sqq='")
			.append(sqq)
			.append("', sqa='")
			.append(sqa)
			.append("' WHERE user_name='")
			.append(uname)
			.append("';");
			session.execute(builder.toString());
			System.out.println(builder.toString());
			return true;
		}
		return false;
	}
	 
	public static long addNewSentMail(String user_name,String guid,String mailTo,String subject, String message, boolean ap) {
		long key = System.currentTimeMillis();
		StringBuilder builder = new StringBuilder();
		builder.append("INSERT INTO application_data.mailbox(")
		.append("user_name, folder, guid, msgtime, mail_to,  subject, message, has_ats");
		  builder.append(") VALUES ('")
		  .append(user_name)
		  .append("','sent','")
		  .append(guid)
		  .append("',")
		  .append(key)
		  .append(",'")
		  .append(mailTo)
		  .append("','")
		  .append(subject.replace("'", "''"))
		  .append("','")
		  .append(message.replace("'", "''"))		  
		  .append("',")
		  .append(ap).append(") USING TTL ")
		  .append(Config.email_storage_expiry_time)
		  .append(";");
		  logger.debug(builder.toString());
		  session.execute(builder.toString());		
		  return key;
	}

	public static byte[] getNotificationSettings(String user_name) throws UnsupportedEncodingException {
		ResultSet res = session.execute("SELECT notification_settings FROM application_data.users WHERE user_name='" + user_name +"';");
		Row row = res.one();
		Iterator<Entry<String, Boolean>> I = row.getMap("notification_settings", String.class, Boolean.class).entrySet().iterator();
		StringBuilder builder = new StringBuilder();
		builder.append("{");
		while(I.hasNext()){
			Entry<String, Boolean> E = I.next();
			builder.append("\"").append(E.getKey()).append("\":\"")
			.append(E.getValue()).append("\"");
			if(I.hasNext()){
				builder.append(",");
			}
		}
		builder.append("}");
		return builder.toString().getBytes("UTF-8");
	}

	public static void setNotificationSettings(String user_name, StringBuilder builder) {
		builder.insert(0,"UPDATE application_data.users SET notification_settings = ")
		.append(" WHERE user_name='")
		.append(user_name)
		.append("';");
		System.out.println(builder.toString());
		session.execute(builder.toString());	
		
	}

	public static void addNewContact(String user_name, String contact_name) {
		StringBuilder builder = new StringBuilder();
		builder.append("UPDATE application_data.users SET contacts = contacts + { '")
			.append(contact_name)
			.append("' } WHERE user_name = '")
			.append(user_name)
			.append("';");
		session.execute(builder.toString());		
	}
	
	public static void deleteContact(String user_name, String contact_name) {
		StringBuilder builder = new StringBuilder();
		builder.append("UPDATE application_data.users SET contacts = contacts - { '")
			.append(contact_name)
			.append("' } WHERE user_name = '")
			.append(user_name)
			.append("';");
		session.execute(builder.toString());		
	}
	
	public static byte[] viewContactsList(String user_name) throws UnsupportedEncodingException {
		StringBuilder builder = new StringBuilder();
		builder.append("Select contacts FROM application_data.users WHERE user_name = '")
			.append(user_name)
			.append("';");
		ResultSet res = session.execute(builder.toString());
		Row row = res.one();
		if(row == null) return null;
		builder = new StringBuilder();
		builder.append("{");
		Iterator<String> I = row.getSet("contacts", String.class).iterator();
		int index = 0;
		while(I.hasNext()){
			builder.append("\"").append(index)
				.append("\":\"")
				.append(I.next())
				.append("\"");
			if(I.hasNext()){
				builder.append(",");
			}
			index++;
		}		
		builder.append("}");
		return builder.toString().getBytes("UTF-8");
	}
		
}

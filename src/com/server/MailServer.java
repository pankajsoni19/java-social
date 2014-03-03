package com.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

import javax.mail.MessagingException;
import javax.print.attribute.standard.Sides;

import org.apache.log4j.Logger;

import com.datastax.driver.core.Row;

public class MailServer {

	public static final long errorNotExists = -1;

	public static final long errorMailBoxFull = 0;
	
	static Logger logger = Logger.getLogger(MailServer.class);
	
	public static ThreadPoolExecutor executor;
	
	public static void addMailToSend(SendMailTLS smt) {
		executor.execute(smt);		
	}
	
	public static void stop() throws MessagingException{
		executor.shutdown();
		if(executor.getQueue().isEmpty()){
			SendMailTLS.finialize();
		}
	}
	
	public static void start(){
		
		executor = new ThreadPoolExecutor(Config.smtp_sending_pool_size, 
				Config.max_sending_pool_size, 
				Config.executor_keep_alive_time, 
				TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()
				, new RejectedExecutionHandler() {
		
		@Override
		public void rejectedExecution(Runnable smt, ThreadPoolExecutor arg1) {
			SendMailTLS tls = (SendMailTLS) smt;
			logger.info("Mail could not be sent to : " + tls.getUsername()  + " for : " + tls.getSubject());
			
		}
	});
		executor.prestartCoreThread();
		//TODO: Uncomment when mail transport is to be used
		//SendMailTLS.getTransport();
	}
	
	public static long sendMail(String user_name, String guid, String mailTo,
			String subject, String message, boolean ap) {		
		StringTokenizer st = new StringTokenizer(mailTo, ",;");
		while(st.hasMoreTokens()){
			if(!CQLHandler.isUserExists(st.nextToken())){
				return errorNotExists;			
			}
		}
		if(!spaceInMailBox(user_name)){
			return errorMailBoxFull;
		}
		
		long key = CQLHandler.addNewSentMail(user_name, guid, mailTo, subject, message,ap);
		if(!ap) {
			pushMailToInbox(user_name, guid,key);
		}		
		return key;
	}

	private static boolean spaceInMailBox(String uid) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT mailbox_size FROM application_data.users WHERE user_name='").append(uid).append("';");
		Long mSize = CQLHandler.executeQuery(query.toString()).get(0).getLong("mailbox_size");
		if(mSize == null || mSize < Config.mail_box_size){
			return true;
		}
		return false;
	}
	
	private static void updateMailBoxSize(String uid, long size){		
		StringBuilder query = new StringBuilder();
		query.append("SELECT mailbox_size FROM application_data.users WHERE user_name='").append(uid).append("';");
		Long mSize = CQLHandler.executeQuery(query.toString()).get(0).getLong("mailbox_size");
		logger.debug("Size in db:" + mSize);
		if(mSize != null){
			size = size + mSize;
		}
		query = new StringBuilder();
		query.append("UPDATE application_data.users SET  mailbox_size=").append(size)
		.append(" WHERE user_name='").append(uid).append("';");
		logger.debug("Writing size:" + query.toString());
		CQLHandler.executeQuery(query.toString());
	}

	public static void pushMailToInbox(String user_name, String guid, long key){
		StringBuilder query = new StringBuilder();
		query.append("SELECT mail_to, subject, message, has_ats, ats FROM application_data.mailbox WHERE user_name='")
			.append(user_name)
			.append("' AND folder='sent' AND guid='").append(guid)
			.append("' AND msgtime=").append(key).append(";");
		logger.debug("reading " + query.toString());
		
		Row r = CQLHandler.executeQuery(query.toString()).get(0);
		String message = r.getString("message").replace("'", "''");
		String subject = r.getString("subject").replace("'", "''");
		long mailSize = message.length() + subject.length();
		
		ArrayList<String> ats_file_store_name = new ArrayList<String>();
		
		query = new StringBuilder();
		query.append("INSERT INTO application_data.mailbox ")
			.append("( user_name, folder, guid, msgtime, has_ats, ats, mail_from, message, subject, read) VALUES ('")
			.append("','inbox','")
			.append(guid).append("',").append(key + 1).append(",").append(r.getBool("has_ats"));
			Map<String, String> s = r.getMap("ats", String.class,String.class);
			if(s != null && s.size() >0){
				Iterator<Entry<String, String>> I = s.entrySet().iterator();
				query.append(",{");
				while(I.hasNext()){
					Entry<String, String> E = I.next();
					query.append("'").append(E.getKey()).append("':'").append(E.getValue()).append("'");
					ats_file_store_name.add(E.getValue());
					File f = new File(Config.getEmailAttachmentPath(E.getValue()));
					mailSize = mailSize + f.length();
					if(I.hasNext()){
						query.append(",");
					}
				}
				query.append("},'");
			}else{
				query.append(",{},'");
			}
			
		query.append(user_name).append("','").append(message).append("','")
			.append(subject).append("',false);");
		
		String mailData = query.toString();
		
		logger.debug("mail box size" + mailSize);
		//update senders mailbox size
		updateMailBoxSize(user_name, mailSize);
		
		StringTokenizer st = new StringTokenizer(r.getString("mail_to"),",;");
		// attachment copies.. 1 in sent..+ x in recievers inbox
		int copies = st.countTokens() + 1;
		
		for(String file_store_name: ats_file_store_name){
			query = new StringBuilder("INSERT INTO application_data.mailbox_holder (filepath , copies) VALUES ('");
			query.append(file_store_name).append("',").append(copies).append(");");
			CQLHandler.executeQuery(query.toString());			
		}
		
		ats_file_store_name.clear();		
		
		while(st.hasMoreTokens()){
			String reciever = st.nextToken();
			query = new StringBuilder(mailData);
			query.insert(131, reciever);
			CQLHandler.executeQuery(query.toString());
			// update recievers mailbox size
			updateMailBoxSize(reciever, mailSize);
			// do mail notification
			query = new StringBuilder("SELECT profile_data, notification_settings FROM application_data.users WHERE user_name='");
			query.append(reciever).append("';");
			Row row = CQLHandler.executeQuery(query.toString()).get(0);
			boolean doSend = row.getMap("notification_settings", String.class, Boolean.class).get("getmail");
			if(doSend){
				String mailId = row.getMap("profile_data", String.class, String.class).get("email_id");
				query = new StringBuilder();
				SendMailTLS sendMailTls = new SendMailTLS(mailId, "You got a new mail", Utility.getRecievedMailText(user_name, subject));
				addMailToSend(sendMailTls);
			}			
		}		
	}
	
	public static boolean processAttachment(String user_name, String guid,
			boolean ap, InputStream requestBody, String filename, long fileSize, int fileIndex, long tag) {
			if(Config.email_attachment_max_size < fileSize){
				return false;
			}
			int index = filename.lastIndexOf('.');
			String extension  = filename.substring(index);
			StringBuilder strbuilder = new StringBuilder();
			strbuilder.append(UUID.randomUUID().toString()).append(UUID.randomUUID().toString());
			for(int i = 2; i< 42; i = i+6){
				strbuilder.insert(i, '/');	
			}
			strbuilder.append(extension);
			String fileStoreName = strbuilder.toString();
			String fileStorePath = Config.getEmailAttachmentPath(fileStoreName);
			
			index = fileStorePath.lastIndexOf('/');
			File f = new File(fileStorePath.substring(0, index+1));
			f.mkdirs();
			
			try {
				BufferedOutputStream bufos = new BufferedOutputStream(new FileOutputStream(fileStorePath, false));
				BufferedInputStream bufis = new BufferedInputStream(requestBody);
				byte buffer[] = new byte[2048];
				int read= 0;
				while((read = bufis.read(buffer))!=-1){
					bufos.write(buffer, 0, read);
					bufos.flush();
				}
				bufos.close();
				bufis.close();				
				
				strbuilder = new StringBuilder();
				strbuilder.append("UPDATE application_data.mailbox SET ats = ats + {'")
					.append(filename).append("':'").append(fileStoreName)
					.append("'} WHERE user_name='")
					.append(user_name)
					.append("' AND folder = 'sent' AND guid='")
					.append(guid)
					.append("' AND msgtime=")
					.append(tag)
					.append(";");
				CQLHandler.executeQuery(strbuilder.toString());				
				if(!ap){					
					pushMailToInbox(user_name, guid, tag);
				}
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			return true;
	}
	
	public static byte[] view_ChatLogs(String user_name, String guid) throws UnsupportedEncodingException {
		StringBuilder query = new StringBuilder();
		query.append("SELECT message, read, msgtime, mail_from, mail_to FROM application_data.mailbox WHERE folder='chat' and user_name='")
		.append(user_name)
		.append("' AND guid='").append(guid)
		.append("';");
		List<Row> res = CQLHandler.executeQuery(query.toString()); 
		if(res == null) return null;
		Iterator<Row> I = res.iterator();
		query = new StringBuilder();
		query.append("{");
		while(I.hasNext()){
			Row row = I.next();
			long msgtime = row.getLong("msgtime");
			boolean read = row.getBool("read");
			query.append("\"").append(msgtime).append("\":{");
				String mail_from = row.getString("mail_from");
				if(mail_from !=null){
					query.append("\"mail_from\":\"").append(mail_from);
				}
				String mail_to = row.getString("mail_to");
				if(mail_to!=null){
					query.append("\"mail_to\":\"").append(mail_to);
				}				
				query.append("\",\"message\":\"").append(row.getString("message").replace("\"", "\\\""))
				.append("\"}");
			if(I.hasNext()){
				query.append(",");
			}
			if(!read){
				StringBuilder query2 = new StringBuilder();
				query2.append("UPDATE application_data.mailbox SET read = true WHERE folder='chat' and user_name='")
				.append(user_name)
				.append("' AND guid='").append(guid)
				.append("' AND msgtime=").append(msgtime).append(";");
				CQLHandler.executeQuery(query2.toString());				
			}
		}
		query.append("}");
		logger.debug(query);
		return query.toString().getBytes("UTF-8");
	}
	
	public static byte[] fetchMails(String user_name, String mtype) throws UnsupportedEncodingException {
		StringBuilder query = new StringBuilder();
		HashMap<String, Mail> mailMap = new HashMap<String, Mail>();
		int index =0;
		switch (mtype) {
		case "chat":			
			query.append("SELECT read, guid, msgtime, mail_from, mail_to FROM application_data.mailbox WHERE folder='chat' and user_name='")
				.append(user_name)
				.append("';");
			List<Row> r = CQLHandler.executeQuery(query.toString());
			if(r ==null){
				return null;
			}else{				
				Iterator<Row> I = r.iterator();			
				while(I.hasNext()){
					Row row = I.next();
					String guid = row.getString("guid");
					long msgtime = row.getLong("msgtime");					
					Mail mpre = mailMap.get(guid);
					System.out.println(row.getBool("read"));
					if(mpre == null || (msgtime > mpre.getMsgtime())){
						Mail m = new Mail(row.getBool("read"), msgtime , row.getString("mail_from"), row.getString("mail_to"));
						mailMap.put(guid, m);
					}				
				}
				query = new StringBuilder();
				query.append("{");
				Iterator<Entry<String, Mail>> it = mailMap.entrySet().iterator();
				while(it.hasNext()){
					Entry<String, Mail> E = it.next();
					Mail m = E.getValue();
					query.append("\"").append(E.getKey()).append("\":{\"read\":\"").append(m.isRead())
						.append("\",\"msgtime\":\"").append(m.getMsgtime())
						.append("\",\"mail_from\":\"").append(m.getMail_from())
						.append("\",\"mail_to\":\"").append(m.getMail_to())
						.append("\"}");
					index++;
				}
				query.append("}");
				return query.toString().getBytes("UTF-8");
			}
		case "archived":
			query.append("SELECT guid, msgtime, mail_from, mail_to, has_ats, subject FROM application_data.mailbox WHERE folder='archived' and user_name='")
			.append(user_name)
			.append("';");
			r = CQLHandler.executeQuery(query.toString());
			if(r ==null){
				return null;
			}else{
				Iterator<Row> I = r.iterator();
				query = new StringBuilder();
				query.append("{");
				
				while(I.hasNext()){
					Row row = I.next();
					query.append("\"").append(index).append("\": {")
					.append("\"guid\":\"").append(row.getString("guid"));
					String s1 = row.getString("mail_to");
					if(s1 != null && s1.length() >0){
						query.append("\",\"mail_to\":\"").append(s1);
					}						
					s1 = row.getString("mail_from");
					if(s1 != null && s1.length() >0){
						query.append("\",\"mail_from\":\"").append(s1);
					}
					
					query.append("\",\"subject\":\"").append(row.getString("subject").replace("\"", "\\\""))
						.append("\",\"msgtime\":\"").append(row.getLong("msgtime"))
						.append("\",\"has_attachments\":\"").append(row.getBool("has_ats")).append("\"}");
					if(I.hasNext()){
						query.append(",");
					}
					index++;
				}
				
				query.append("}");
				System.out.println(query.toString());
				return query.toString().getBytes("UTF-8");
			}
			
		case "sent":
			query.append("SELECT guid, msgtime, mail_to, has_ats, subject FROM application_data.mailbox WHERE folder='sent' and user_name='")
			.append(user_name)
			.append("';");
			r = CQLHandler.executeQuery(query.toString());
			if(r ==null){
				return null;
			}else{
				Iterator<Row> I = r.iterator();
				query = new StringBuilder();
				query.append("{");
				
				while(I.hasNext()){
					Row row = I.next();
					query.append("\"").append(index).append("\": {")
					.append("\"guid\":\"").append(row.getString("guid"))					
					.append("\",\"mail_to\":\"").append(row.getString("mail_to"))
					.append("\",\"subject\":\"").append(row.getString("subject").replace("\"", "\\\""))
					.append("\",\"msgtime\":\"").append(row.getLong("msgtime"))
					.append("\",\"has_attachments\":\"").append(row.getBool("has_ats")).append("\"}");
					if(I.hasNext()){
						query.append(",");
					}
					index++;
				}
				
				query.append("}");
				System.out.println(query.toString());
				return query.toString().getBytes("UTF-8");
			}
		case "inbox":
			query.append("SELECT guid, read, msgtime, mail_from, has_ats, subject FROM application_data.mailbox WHERE folder='inbox' and user_name='")
			.append(user_name)
			.append("';");
			r = CQLHandler.executeQuery(query.toString());
			if(r ==null){
				return null;
			}else{
				Iterator<Row> I = r.iterator();
				query = new StringBuilder();
				query.append("{");
				
				while(I.hasNext()){
					Row row = I.next();
					query.append("\"").append(index).append("\": {")
					.append("\"guid\":\"").append(row.getString("guid"))
					.append("\",\"read\":\"").append(row.getBool("read"))
					.append("\",\"mail_from\":\"").append(row.getString("mail_from"))
					.append("\",\"subject\":\"").append(row.getString("subject").replace("\"", "\\\""))
					.append("\",\"msgtime\":\"").append(row.getLong("msgtime"))
					.append("\",\"has_attachments\":\"").append(row.getBool("has_ats")).append("\"}");
					if(I.hasNext()){
						query.append(",");
					}
					index++;
				}
				
				query.append("}");
				System.out.println(query.toString());
				return query.toString().getBytes("UTF-8");
			}
		default:			
			break;
		}
		return null;
	}

	public static void deleteMail(String user_name, String guid, String folder, long ts) {
		logger.debug(folder.contains("chat"));
		if(folder.contains("chat")){
			StringBuilder builder = new StringBuilder();
			builder.append("DELETE FROM application_data.mailbox where user_name='")
				.append(user_name)
				.append("' AND folder='").append(folder)
				.append("' AND guid='").append(guid)
				.append("' AND msgtime=").append(ts)
				.append(";");
				
			CQLHandler.executeQuery(builder.toString());	
			
			
		}else{
		
			
			StringBuilder builder = new StringBuilder();
			builder.append("SELECT subject, message, ats FROM application_data.mailbox where user_name='")
				.append(user_name)
					.append("' AND folder='").append(folder)
					.append("' AND guid='").append(guid)
					.append("' AND msgtime=").append(ts)
					.append(";");
			
			Row row = CQLHandler.executeQuery(builder.toString()).get(0);
			long mailSize = row.getString("subject").length() + row.getString("message").length();
			Map<String, String> map = row.getMap("ats", String.class, String.class);		
			if(map != null && map.size()>0){
				Iterator<Entry<String, String>> I = map.entrySet().iterator();
				while(I.hasNext()){	
					Entry<String, String> E = I.next();				
					File f = new File(Config.getEmailAttachmentPath(E.getValue()));
					mailSize = mailSize + f.length();		
					builder = new StringBuilder("SELECT copies FROM application_data.mailbox_holder WHERE filepath='");
					builder.append(E.getValue()).append("';");
					int copies = CQLHandler.executeQuery(builder.toString()).get(0).getInt("copies") - 1;
					if( copies == 0){
						f.delete();
						builder = new StringBuilder("DELETE FROM application_data.mailbox_holder WHERE filepath='");
						builder.append(E.getValue()).append("';");
						CQLHandler.executeQuery(builder.toString());
					}else{
						//UPDATE application_data.users SET notification_settings = 
						builder = new StringBuilder("UPDATE application_data.mailbox_holder SET copies=");
						builder.append(copies)
							.append(" WHERE filepath='")
							.append(E.getValue()).append("';");
						CQLHandler.executeQuery(builder.toString());
					}
				}			
			}
			mailSize = mailSize * -1;
			updateMailBoxSize(user_name, mailSize);
			
			builder = new StringBuilder();
			builder.append("DELETE FROM application_data.mailbox where user_name='")
				.append(user_name)
				.append("' AND folder='").append(folder)
				.append("' AND guid='").append(guid)
				.append("' AND msgtime=").append(ts)
				.append(";");
				
			CQLHandler.executeQuery(builder.toString());	
		}
	}

	public static void archiveMail(String user_name, String guid, String folder, long ts) {
		// read mail
		StringBuilder builder = new StringBuilder();
		builder.append("SELECT * FROM application_data.mailbox WHERE user_name='")
			.append(user_name)
			.append("' AND folder='").append(folder)
			.append("' AND guid='").append(guid)
			.append("'AND msgtime=").append(ts).append(";");		
		Row r = CQLHandler.executeQuery(builder.toString()).get(0);
		// move mail
		builder = new StringBuilder();
		builder.append("INSERT INTO application_data.mailbox")
		.append("( user_name, folder, guid, msgtime, has_ats, ats, mail_from, mail_to, message, subject, read) VALUES ('")
		.append(user_name)
		.append("','archived','")
		.append(guid).append("',").append(r.getLong("msgtime")).append(",").append(r.getBool("has_ats"));
		Map<String, String> s = r.getMap("ats", String.class,String.class);
		if(s != null && s.size() >0){
			Iterator<Entry<String, String>> I = s.entrySet().iterator();
			builder.append(",{");
			while(I.hasNext()){
				Entry<String, String> E = I.next();
				builder.append("'").append(E.getKey()).append("':'").append(E.getValue()).append("'");
				if(I.hasNext()){
					builder.append(",");
				}
			}
			builder.append("},'");
		}else{
			builder.append(",{},'");
		}
		
		builder.append(r.getString("mail_from"))
			.append("','").append(r.getString("mail_to"))
			.append("','").append(r.getString("message").replace("'","''"))
			.append("','").append(r.getString("subject").replace("'", "''")).append("',true);");
	logger.debug("archiving mail:  " + builder.toString());	
		//return; 
		CQLHandler.executeQuery(builder.toString());		
		//delete mail from old folder
		builder = new StringBuilder();
		builder.append("DELETE FROM application_data.mailbox where user_name='")
			.append(user_name)
			.append("' AND folder='").append(folder)
			.append("' AND guid='").append(guid)
			.append("'AND msgtime=").append(ts).append(";");
		System.out.println(builder.toString());	
		CQLHandler.executeQuery(builder.toString());
	}

	public class MailData{
		private boolean read;
		private String mail_from;
		private String mail_to;
		private String message;
		private String subject;
		private Map<String, String> ats;
		private String folder;

		public MailData(boolean read, String mail_to, String mail_from, String message,
				String subject, Map<String, String> ats, String folder) {
			this.read = read;
			this.mail_to = mail_to;
			this.mail_from = mail_from;
			this.message = message;
			this.subject = subject;
			this.ats = ats;
			this.folder = folder;
			
		}	
	}
	
	public byte[] view_conversation(String user_name, String guid) throws UnsupportedEncodingException {
		TreeMap<Long, MailData> treemap = new TreeMap<>();
		StringBuilder query = new StringBuilder();
		query.append("SELECT read, mail_from, message ,subject, msgtime, ats FROM application_data.mailbox  WHERE user_name='")
			.append(user_name)
			.append("' AND folder='inbox' AND guid='").append(guid).append("';");
		System.out.println(query.toString());
		 List<Row> rows = CQLHandler.executeQuery(query.toString());
		 if(rows != null && rows.size() > 0){
			 Iterator<Row> I = rows.iterator();
				
				while(I.hasNext()){
					Row r = I.next();
					treemap.put(r.getLong("msgtime"),
									new MailData(r.getBool("read"),
										null, r.getString("mail_from"),
										r.getString("message"),
										r.getString("subject"),
										r.getMap("ats", String.class, String.class), "inbox"));
				}	 
		 }
		 
		
		query = new StringBuilder();
		query.append("SELECT mail_to, message ,subject, msgtime, ats FROM application_data.mailbox  WHERE user_name='")
			.append(user_name)
			.append("' AND folder='sent' AND guid='").append(guid).append("';");
		System.out.println(query.toString());
		rows = CQLHandler.executeQuery(query.toString());
		 if(rows != null && rows.size() > 0){
			 Iterator<Row> I = rows.iterator();
			 while(I.hasNext()){
					Row r = I.next();
					treemap.put(r.getLong("msgtime"),
									new MailData(false,
										r.getString("mail_to"),null,
										r.getString("message"),
										r.getString("subject"),
										r.getMap("ats", String.class, String.class),"sent"));
			}
		 }		
		
		query = new StringBuilder();
		query.append("SELECT mail_from, mail_to, message ,subject, msgtime, ats FROM application_data.mailbox  WHERE user_name='")
			.append(user_name)
			.append("' AND folder='archived' AND guid='").append(guid).append("';");
		System.out.println(query.toString());
		rows = CQLHandler.executeQuery(query.toString());
		 if(rows != null && rows.size() > 0){
			 Iterator<Row> I = rows.iterator();
			 while(I.hasNext()){
					Row r = I.next();
					treemap.put(r.getLong("msgtime"),
									new MailData(false,
										r.getString("mail_to"),
										r.getString("mail_from"),
										r.getString("message"),
										r.getString("subject"),
										r.getMap("ats", String.class, String.class),"archived"));
			 }		 
		 }
		
		NavigableMap<Long, MailData> map = treemap.descendingMap();
		query = new StringBuilder();
		query.append("{");		
		Iterator<Entry<Long, MailData>> Ik = map.entrySet().iterator();
		while(Ik.hasNext()){
			Entry<Long, MailData> entry = Ik.next();
			MailData d = entry.getValue();
			query.append("\"").append(entry.getKey())
			.append("\": {\"folder\":\"").append(d.folder);
			
			if(d.mail_to != null){
				query.append("\",\"mail_to\":\"").append(d.mail_to);
			}
			if(d.mail_from !=null){
				query.append("\",\"mail_from\":\"").append(d.mail_from);
			}
			query.append("\",\"subject\":\"").append(d.subject.replace("\"", "\\\""))
			.append("\", \"message\" :\"").append(d.message.replace("\"", "\\\""));
			query.append("\"");
			if(d.ats != null && d.ats.size() >0 ){
				query.append(",\"ats\": {");
				Iterator<Entry<String, String>> Iik = d.ats.entrySet().iterator();
				while(Iik.hasNext()){
					Entry<String, String> en = Iik.next();
					query.append("\"").append(en.getKey())
						.append("\":\"").append(en.getValue()).append("\"");
					
					if(Iik.hasNext()){
						query.append(",");	
					}
				}
				query.append("}");
			}
			query.append("}");
			if(Ik.hasNext()){
				query.append(",");	
			}
		}
		query.append("}");
		
		return query.toString().getBytes("UTF-8");
	}

	public static void toggle_read(String user_name, String guid, long ts) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT read FROM application_data.mailbox WHERE user_name='")
			.append(user_name).append("' AND folder='inbox' AND guid='").append(guid)
			.append("' AND msgtime=").append(ts).append(";");
		logger.debug(query.toString());
		boolean b = CQLHandler.executeQuery(query.toString()).get(0).getBool("read");
		
		query = new StringBuilder();
		query.append("UPDATE application_data.mailbox SET read=")
			.append(!b).append(" WHERE user_name='").append(user_name)
			.append("' AND folder='inbox' AND guid='").append(guid)
			.append("' AND msgtime=").append(ts).append(";");
		logger.debug(query.toString());
		CQLHandler.executeQuery(query.toString());		
	}

	public static void toggle_read(String user_name, String guid) {
		// TODO Auto-generated method stub
		
	}
}

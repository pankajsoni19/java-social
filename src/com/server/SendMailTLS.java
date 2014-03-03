package com.server;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;

import com.sun.mail.smtp.SMTPTransport;

 
public class SendMailTLS implements Runnable{
	
	private String mailTo;
	private String subject;
	private String Data;
	private static Session session;
	private static Transport t = null;
	static Logger logger = Logger.getLogger(SendMailTLS.class);
	
	public static Transport getTransport() throws MessagingException{
		if(t == null || !t.isConnected()){
			Properties props = new Properties();
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.host", Config.smtp_host);
			props.put("mail.smtp.port", Config.smtp_port);
			if(Config.isSSLSMTP){
				props.put("mail.smtp.socketFactory.port", String.valueOf(Config.smtp_port));
				props.put("mail.smtp.socketFactory.class",
					"javax.net.ssl.SSLSocketFactory");
			}
			session = Session.getInstance(props,
			  new javax.mail.Authenticator() {
				protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
					return new javax.mail.PasswordAuthentication(Config.email_username, Config.email_pwd);
				}
			  });
			t = session.getTransport("smtp");			
			t.connect(Config.smtp_host, Config.email_username, Config.email_pwd);
			if(t.isConnected()){
				logger.info("Connected to mail server at: " + Config.smtp_host + ":" + Config.smtp_port);
			}
		}
		return t;
	}
	
	public static void finialize() throws MessagingException{
		if(t!=null && t.isConnected()){
			t.close();
		}
		logger.info("Shutting down mail transport.");
	}
	public SendMailTLS(String mailID, String subj,
			String msgBody) {
		mailTo = mailID;
		subject = subj;
		Data = msgBody;
	}

	@Override
	public void run() {		
		try {
			sendmessage(mailTo, subject, Data);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
	
	public boolean sendmessage(String mailTo, String Subject, String Data) throws UnknownHostException {
		try {
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(Config.email_from_addr));
			message.setRecipients(Message.RecipientType.TO,InternetAddress.parse(mailTo));
			message.setSubject(Subject);
			message.setText(Data); 
			Transport tx = getTransport();			
			tx.sendMessage(message, message.getRecipients(Message.RecipientType.TO));
					
			return true;
		} catch (MessagingException e) {
			e.printStackTrace();
			return false;
		} 
	}

	public String getSubject() {
		return subject;
	}
	
	public String getUsername() {
		return mailTo;
	}
}
package com.server;

public class Mail {

	private boolean read;
	private long msgtime;
	private String mail_from;
	private String mail_to;
	
	public Mail(boolean read, long msgtime, String mail_from, String mail_to){
		this.read = read;
		this.msgtime = msgtime;
		this.mail_from = mail_from;
		this.mail_to = mail_to;		
	}

	public boolean isRead() {
		return read;
	}

	public long getMsgtime() {
		return msgtime;
	}

	public String getMail_from() {
		return mail_from;
	}

	public String getMail_to() {
		return mail_to;
	}
}

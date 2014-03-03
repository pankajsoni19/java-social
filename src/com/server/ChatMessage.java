package com.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ChatMessage {

	public final static int msgError = 0;
	public final static int msgNormal = 1;
	
	private String msgFrom;
	private String msgTo;
	private String msg;
	private String guid;
	private String remote_server_id = null;
	private int msgType;
	
	public ChatMessage(String msgFrom){
		this.msgFrom = msgFrom;
		this.msgType = msgError;
	}
	
	public ChatMessage(String msgFrom, String msgTo, String msg, String guid) {
		this.msgFrom = msgFrom;
		this.msgTo = msgTo;
		this.msg = msg;
		this.guid= guid;
		this.msgType = msgNormal;
	}

	public ChatMessage(String msgFrom, String msgTo, String msg, String guid, String remote_server_id) {
		this.msgFrom = msgFrom;
		this.msgTo = msgTo;
		this.msg = msg;
		this.guid= guid;		
		this.remote_server_id = remote_server_id;
		this.msgType = msgNormal;
	}

	public static void writeData(ChatMessage data, DataOutputStream dos) throws IOException {
		if(data.msgType == ChatMessage.msgNormal){
			dos.writeInt(data.msgType);
			dos.writeUTF(data.msgFrom);
			dos.writeUTF(data.msgTo);
			dos.writeUTF(data.msg);
			dos.writeUTF(data.guid);
			dos.writeUTF(S2s_communicator.getServer_id());		
			dos.flush();
		}else if(data.msgType == ChatMessage.msgError){
			dos.writeInt(data.msgType);
			dos.writeUTF(data.msgFrom);	
			dos.flush();
		}
	}

	public static ChatMessage makeMessage(DataInputStream dis) throws IOException {	
		
		int type = dis.readInt();
		if(type == ChatMessage.msgNormal){
			return new ChatMessage(dis.readUTF(), dis.readUTF(), dis.readUTF(), dis.readUTF(), dis.readUTF());	
		}else if(type == ChatMessage.msgError){
			return new ChatMessage(dis.readUTF());
		}
		
		return null;
	}
	
	public String getMsgFrom() {
		return msgFrom;
	}

	public String getMsgTo() {
		return msgTo;
	}

	public String getMsg() {
		return msg;
	}

	public String getGuid() {
		return guid;
	}

	public String getRemote_server_id() {
		return remote_server_id;
	}
	
	public int getMsgType(){
		return msgType;
	}

}

package com.server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.collections4.bidimap.TreeBidiMap;
import org.apache.log4j.Logger;
import org.apache.thrift.transport.TFileTransport.chunkState;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeImpl1Server;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.DefaultWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.server.WebSocketServer.WebSocketServerFactory;

import com.datastax.driver.core.Row;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/*
 * 
 * A simple WebSocketServer implementation. Keeps track of a "chatroom".
 */

/*
 * Keystore with certificate created like so (in JKS format):
 *
 *keytool -genkey -validity 3650 -keystore "keystore.jks" -storepass "storepassword" -keypass "keypassword" -alias "default" -dname "CN=127.0.0.1, OU=MyOrgUnit, O=MyOrg, L=MyCity, S=MyRegion, C=MyCountry"
 */

public class ChatServer {

	static Logger logger = Logger.getLogger(ChatServer.class);
	
	private WebSocketServer webSocketServer ;
	private static HashMap<String, ChatData> chatMap = new HashMap<String, ChatServer.ChatData>();
	private static final String SystemMsg = "System";
	
	public void stop() throws IOException, InterruptedException {
		if(Config.isHTTPSsupported){
			
		}else{
			webSocketServer.stop();
		}		
	}
	
	public void start() {
		webSocketServer.start();
	}
	
	public ChatServer() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, UnrecoverableKeyException, KeyManagementException {
		
		plainServer();
		
		if(Config.isHTTPSsupported){
			
			KeyStore ks = KeyStore.getInstance("JKS");
	        ks.load(new FileInputStream(Config.cert_store), Config.key_store_password);
	        KeyManagerFactory kmf = KeyManagerFactory.getInstance( "SunX509" );
			kmf.init( ks, Config.cert_key_password );
			TrustManagerFactory tmf = TrustManagerFactory.getInstance( "SunX509" );
			tmf.init( ks );
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
			SSLSessionContext sslSessionContext = sslContext.getServerSessionContext();
			sslSessionContext.setSessionTimeout(0);
            sslSessionContext.setSessionCacheSize(0);
            SSLParameters sslParams = sslContext.getDefaultSSLParameters();
            sslParams.setNeedClientAuth(false);
            webSocketServer.setWebSocketFactory(new DefaultSSLWebSocketServerFactory( sslContext ) );
			
		}
		
	}

	private void plainServer() {

		webSocketServer = new WebSocketServer(new InetSocketAddress(Config.websocketserverport)) {
			@Override
			public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
					WebSocket conn, Draft draft, ClientHandshake request)
					throws InvalidDataException {
				try {
					if(!validateRequest(request ,conn)){
						throw new InvalidDataException(403,"Forbidden");
					}					
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}			
				
				return super.onWebsocketHandshakeReceivedAsServer(conn, draft, request); 
			}
			
			private boolean validateRequest(ClientHandshake request, WebSocket conn) throws UnknownHostException {
				if(!request.getFieldValue("Host").contentEquals(Config.getWebSocketHost())){
					return false;
				}
				if(!request.getFieldValue("Origin").contentEquals(Config.getWebSocketOrigin())){
					return false;
				}				
				String cookie = Utility.getCookieFromSearchString("SID", request.getFieldValue("Cookie"));
				CookieData cd = CookieData.getCookieData(cookie);
				if(cd == null){
					return false;
				}
				
				ChatData chatdata_old = chatMap.get(cd.getUserName());				
				
				if(chatdata_old != null && chatdata_old.conn_cd.isOpen()){
					chatdata_old.conn_cd.send("rebind");
					chatdata_old.conn_cd.close();
				}
				
				ChatData chatData = new ChatData(conn);
				chatMap.put(cd.getUserName(), chatData);
				conn.setPrivateData(cd.getUserName());
				StringBuilder query = new StringBuilder("INSERT INTO application_data.routing_data (user_name, server_id) VALUES ('");
				query.append(cd.getUserName()).append("','").append(S2s_communicator.getServer_id()).append("');");
				CQLHandler.executeQuery(query.toString());
				return true;
			}

			@Override
			public void onOpen( WebSocket conn, ClientHandshake handshake ) {
				logger.debug( conn.getPrivateData() + " from: " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + " entered the room!" );			
				try {
					StringBuilder strbuilder = new StringBuilder();
					strbuilder = Config.getNewWSCookie(UUID.randomUUID().toString(),strbuilder);					
					if(Config.maintain_chat_history){
						strbuilder.append("&System&Chat msgs will be recorded.");
					}else{
						strbuilder.append("&System&Chat msgs will not be recorded.");
					}
					conn.send(strbuilder.toString());
				} catch (NotYetConnectedException e) {
					e.printStackTrace();
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
			}
			
			private void deRegister(String user_name){
				StringBuilder query = new StringBuilder("DELETE FROM application_data.routing_data WHERE user_name='");
				query.append(user_name).append("';");
				CQLHandler.executeQuery(query.toString());
			}

			@Override
			public void onClose( WebSocket conn, int code, String reason, boolean remote ) {
				String user_name = (String) conn.getPrivateData();				
				logger.debug( user_name + " has left the room!" );
				chatMap.remove(user_name);
				deRegister(user_name);				
			}

			@Override
			public void onMessage( WebSocket conn, String recieved_msg_local ) {				
				int index = Integer.valueOf(recieved_msg_local.substring(0, 2));
				String msgTo = recieved_msg_local.substring(2, 2 + index);
				index = index+2;
				int ulen2 = Integer.valueOf(recieved_msg_local.substring(index, index + 4));
				index = index + 4;
				String msg = recieved_msg_local.substring(index, index + ulen2);
				String msgFrom = (String)conn.getPrivateData();
				ChatData cdMsgFrom = chatMap.get(msgFrom);
				logger.debug("from: " + msgFrom + " to: " + msgTo + " msg: " + msg);
				
				String guid = cdMsgFrom.chat_id.get(msgTo);
				if(guid == null){
					guid = UUID.randomUUID().toString();
					cdMsgFrom.chat_id.put(msgTo, guid);
				}
				
				StringBuilder query;
				if(Config.maintain_chat_history){
					long key = System.currentTimeMillis();
					query = new StringBuilder();
					query.append("INSERT INTO application_data.mailbox ")
						.append("(user_name, read, folder, guid, msgtime, mail_to,message) VALUES ('")
						.append(msgFrom)
						.append("','true','chat','").append(guid)
						.append("',").append(key).append(",'").append(msgTo).append("','")
						.append(msg.replace("'", "''")).append("');");						
					CQLHandler.executeQuery(query.toString());
				}
				
				ChatData cdMsgTo = chatMap.get(msgTo);
				
				if(cdMsgTo != null && cdMsgTo.conn_cd.isOpen()){
					sendChatMsg(cdMsgTo.conn_cd, msgFrom, "msg", msg);
					storeRecieverMessage(msgTo, msgFrom, msg, guid);
				}else if(CQLHandler.isUserExists(msgTo)){		
					//send remote data
					ChatMessage remote_msg_to_send = new ChatMessage(msgFrom, msgTo, msg, guid);
					
					query = new StringBuilder("SELECT server_id FROM application_data.routing_data WHERE user_name='")
													.append(conn.getPrivateData()).append("';");
					List<Row> rows = CQLHandler.executeQuery(query.toString());
					if(rows == null){
						// User is offline..
						if(Config.maintain_chat_history){
							sendChatMsg(conn, SystemMsg, "msg", "User will recieve messages when they come online.");							
						}else{
							sendChatMsg(conn, SystemMsg, "msg", "User is currently offline.");
						}
						
						storeRecieverMessage(msgTo, msgFrom, msg, guid);
					}else{
						// User may be online..
						String remote_sid = rows.get(0).getString("server_id");
						LinkedBlockingQueue<ChatMessage> queue = S2s_communicator.getQueue(remote_sid);
						// try sending..
						if(queue !=null){
							if(!queue.offer(remote_msg_to_send)){
								// couldn't send..send error message..
								if(Config.maintain_chat_history){
									sendChatMsg(conn, SystemMsg, "msg", "Internal Server Error, user will recieve msg when they come online.");							
								}else{
									sendChatMsg(conn, SystemMsg, "msg", "Internal Server Error.");
								}
								storeRecieverMessage(msgTo, msgFrom, msg, guid);									
							}	
						}else{
							//connection is lost.. try reconnect..
							query = new StringBuilder("SELECT p, server_ip from application_data.s2s WHERE server_id='")
											.append(remote_sid).append("';");
							rows = CQLHandler.executeQuery(query.toString());
							if(rows == null){
								sendChatMsg(conn, SystemMsg, "msg", "Internal Server Error.");
								storeRecieverMessage(msgTo, msgFrom, msg, guid);
							}else{
								Row row = rows.get(0);								
								new S2S_sender(row.getString("server_ip"), row.getInt("p"), remote_sid).start();
								//get new queue and enqueue..
								queue = S2s_communicator.getQueue(remote_sid);
								if(!queue.offer(remote_msg_to_send)){
									// couldn't send..send error message..
									if(Config.maintain_chat_history){
										sendChatMsg(conn, SystemMsg, "msg", "Internal Server Error, user will recieve msg when they come online.");							
									}else{
										sendChatMsg(conn, SystemMsg, "msg", "Internal Server Error.");
									}
									storeRecieverMessage(msgTo, msgFrom, msg, guid);									
								}
							}							
						}												
					}
					
				}else{
					sendChatMsg(conn, SystemMsg, "msg", "User name is invalid.");
				}
			}

			public void onFragment( WebSocket conn, Framedata fragment ) {
				System.out.println( "received fragment: " + fragment );
			}

			@Override
			public void onError( WebSocket conn, Exception ex ) {
				ex.printStackTrace();
				if( conn != null ) {
					conn.close();
					String user_name = (String) conn.getPrivateData(); 
					chatMap.remove(user_name);
					deRegister(user_name);
					logger.error("Error on connection: " + conn.getRemoteSocketAddress().getHostName() + " for: " + ex.getMessage());
				}
			}
		};		
	}

	public class ChatData {		
		private WebSocket conn_cd;
		private HashMap<String, String> chat_id;
		
		public ChatData(WebSocket conn) {
			conn_cd = conn;	
			chat_id = new HashMap<String, String>();
		}		
	}

	public static void doClose(String uname) {		
		ChatData cd = chatMap.remove(uname);
		if(cd != null){
			cd.conn_cd.close();	
		}		
	}
	
	public static void sendChatMsg(WebSocket conn, String msgFrom, String msgType, String msg) {
		logger.debug("websocket: " + conn +" msgFrom: "+ msgFrom + " msgType: "+ msgType + " msg: " +msg);
		StringBuilder builder = new StringBuilder();
		builder.append(String.format("%02d", msgFrom.length()))
			.append(msgFrom)
			.append(msgType)
			.append(String.format("%04d", msg.length()))
			.append(msg);					
		conn.send(builder.toString());
	}
	
	public static void storeRecieverMessage(String user_name, String msgFrom, String msg, String guid){
		if(Config.maintain_chat_history){
			long key = System.currentTimeMillis();
			StringBuilder query = new StringBuilder();
			query.append("INSERT INTO application_data.mailbox ")
				.append("(user_name, read, folder, guid, msgtime, mail_from, message) VALUES ('")
				.append(user_name)
				.append("',false ,'chat','").append(guid)
				.append("',").append(key).append(",'").append(msgFrom).append("','")
				.append(msg.replace("'", "''")).append("');");						
			CQLHandler.executeQuery(query.toString());
		}		
	}

	public static void processRecievedData(ChatMessage cm) {
		if(cm == null) return;
		if(cm.getMsgType() == ChatMessage.msgNormal){
			ChatData cd = chatMap.get(cm.getMsgTo());	
			if(cd == null || !cd.conn_cd.isOpen()){
				LinkedBlockingQueue<ChatMessage> queue = S2s_communicator.getQueue(cm.getRemote_server_id());
				ChatMessage cm_error = new ChatMessage(cm.getMsgFrom());
				if(queue != null){
					queue.offer(cm_error);					
				}else{
				//try reconnecting..	
					StringBuilder query = new StringBuilder("SELECT p, server_ip from application_data.s2s WHERE server_id='")
										.append(cm.getRemote_server_id()).append("';");
					List<Row> rows = CQLHandler.executeQuery(query.toString());
					if(rows != null){
						Row row = rows.get(0);								
						new S2S_sender(row.getString("server_ip"), row.getInt("p"), cm.getRemote_server_id()).start();
						//get new queue and enqueue..
						queue = S2s_communicator.getQueue(cm.getRemote_server_id());
						if(queue != null){
							queue.offer(cm_error);					
						}						
					}
				}
			}else{			
				sendChatMsg(cd.conn_cd, cm.getMsgFrom(), "msg", cm.getMsg());
			}
			storeRecieverMessage(cm.getMsgTo(), cm.getMsgFrom(), cm.getMsg(), cm.getGuid());
			if(cd != null){
				cd.chat_id.put(cm.getMsgFrom(), cm.getGuid());			
			}	
		}else if(cm.getMsgType() == ChatMessage.msgError){
			ChatData cd = chatMap.get(cm.getMsgFrom());
			if(cd != null && cd.conn_cd.isOpen()){
				if(Config.maintain_chat_history){
					sendChatMsg(cd.conn_cd, SystemMsg, "msg", "User is offline and will recieve messages later.");
				}else{
					sendChatMsg(cd.conn_cd, SystemMsg, "msg", "User is offline.");
				}
			}
		}
	}	
}
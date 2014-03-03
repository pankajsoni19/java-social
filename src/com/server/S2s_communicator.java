package com.server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.apache.log4j.Logger;

import com.datastax.driver.core.Row;

public class S2s_communicator extends Thread{
	
	static Logger logger = Logger.getLogger(S2s_communicator.class);
	
	private static String server_id;
	private static ConcurrentHashMap<String, LinkedBlockingQueue<ChatMessage>> s2s_remote_map;

	public static LinkedBlockingQueue<ChatMessage> remove(Object key) {
		return s2s_remote_map.remove(key);
	}
	
	public static void setServerId(String id){
		server_id = id;
	}

	public static LinkedBlockingQueue<ChatMessage> put(String arg0,
			LinkedBlockingQueue<ChatMessage> arg1) {
		return s2s_remote_map.put(arg0, arg1);
	}
	
	public S2s_communicator() {
		server_id = UUID.randomUUID().toString();
		init();
		s2s_remote_map = new ConcurrentHashMap<String, LinkedBlockingQueue<ChatMessage>>();
	}
	
	public static LinkedBlockingQueue<ChatMessage> getQueue(String server_id) {
		return s2s_remote_map.get(server_id);		
	}
	
	public static String getServer_id(){
		return server_id;
	}
	
	public void stop_s2s(){		
		this.interrupt();
		StringBuilder query = new StringBuilder();
		query.append("DELETE FROM application_data.s2s WHERE server_id='").append(server_id).append("';");
		CQLHandler.executeQuery(query.toString());		
	}
	
	@Override
	public void run() {
		super.run();
		if(Config.is_secured){		
			SSLServerSocket s = null;
			try {
				SSLContext sslContext = SSLContext.getInstance("TLS");
		        KeyStore ks = KeyStore.getInstance("JKS");
		        ks.load(new FileInputStream(Config.in_cert_store), Config.in_key_store_password);
		        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		        kmf.init(ks, Config.in_cert_key_password);
		        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		        tmf.init(ks);
		        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
		        
		        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
		        
		         s = (SSLServerSocket) ssf.createServerSocket();
		         s.setReuseAddress(true);
		         if(Config.client_is_secured){
		        	 s.setNeedClientAuth(true);
		        	 s.setWantClientAuth(true);
		         }else{
		        	 s.setNeedClientAuth(false);
		        	 s.setWantClientAuth(false);
		         }		         
		         s.bind(new InetSocketAddress(Config.public_ip_port));
			}  catch (Exception e) {				
				logger.error("Cannot start s2s connections. server exiting now...");
				e.printStackTrace();
				System.exit(1);			
			}	
			
	         while(!this.isInterrupted()){	        	 
				try {
					SSLSocket sock = (SSLSocket) s.accept();
					new Reciever(sock).start();
				} catch (IOException e) {
					e.printStackTrace();
					try {
						s.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}	 
				 
	         }
	         
		}else{
			ServerSocket serverSocket = null;
			try {
				serverSocket = new ServerSocket();
				serverSocket.setReuseAddress(true);
				serverSocket.bind(new InetSocketAddress(Config.public_ip_port));
				while(!this.isInterrupted()){
					Socket sock = serverSocket.accept();
					new Reciever(sock).start();
				}							
			} catch (IOException e) {
				e.printStackTrace();
				try {
					serverSocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}		
		}	
	}
	
	public class Reciever extends Thread{
		
		private Socket recieverSocket;
		
		public Reciever(Socket sock) {
			recieverSocket = sock;
		}

		@Override
		public void run() {
			super.run();
			try {
				DataInputStream dis = new DataInputStream(recieverSocket.getInputStream());
				while(!this.isInterrupted()){
					ChatServer.processRecievedData(ChatMessage.makeMessage(dis));
				}
			} catch (IOException e) {
				e.printStackTrace();
				try {
					recieverSocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}			
		}
	}
	
	private void init() {
		StringBuilder query = new StringBuilder();
		query.append("SELECT * from application_data.s2s;");
		List<Row> rows = CQLHandler.executeQuery(query.toString());
		if(rows != null){
			Iterator<Row> I = rows.iterator();
			while(I.hasNext()){
				Row r = I.next();
				String sip = r.getString("server_ip");
				if(sip.contains(Config.public_ip)){
					server_id = r.getString("server_id");
				}else{
					new S2S_sender(sip, r.getInt("p"), r.getString("server_id")).start();					
				}
			}			
		}
		
		query = new StringBuilder();
		query.append("INSERT INTO application_data.s2s (server_id, server_ip, p) VALUES ('")
			.append(server_id).append("','")
			.append(Config.public_ip).append("',")
			.append(Config.public_ip_port).append(")");
		CQLHandler.executeQuery(query.toString());		
	}
}

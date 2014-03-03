package com.server;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.log4j.Logger;

public class S2S_sender extends Thread{

	static Logger logger = Logger.getLogger(S2S_sender.class);
	
	private Socket sender_sock;
	private String sid;
	private LinkedBlockingQueue<ChatMessage> processing_queue;
	
	public S2S_sender(String sip, int port, String sid) {
		try {
			connectToRemoteServer(sip, port, sid);
		} catch (Exception e) {
			logger.error("Client keystore error.. server exiting now...");
			e.printStackTrace();
			System.exit(2);
		}
		this.sid = sid;
		processing_queue = new LinkedBlockingQueue<ChatMessage>();
		S2s_communicator.put(sid, processing_queue);
	}

	private void connectToRemoteServer(String sip, int port, String sid) throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, KeyManagementException, CertificateException {
		logger.info("S2S_Connecting to: " +  sid +  " at:" + sip +":" + port);
		if(Config.is_secured){		
			
			if(Config.client_is_secured){
				SSLContext sslContext = SSLContext.getInstance("TLS");
		        KeyStore ks = KeyStore.getInstance("JKS");
		        ks.load(new FileInputStream(Config.client_in_cert_store), Config.client_in_key_store_password);
		        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		        kmf.init(ks, Config.client_in_cert_key_password);
		        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		        tmf.init(ks);
		        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		        
		        SSLSocketFactory ssf = sslContext.getSocketFactory();
		        SSLSocket sock = (SSLSocket) ssf.createSocket();
		         sock.setReuseAddress(true);
		         sock.setNeedClientAuth(true);
		         sock.setWantClientAuth(true);
		         sock.connect(new InetSocketAddress(sip, port));
		         sock.startHandshake();
		         sender_sock = sock;
				
			}else{
				SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
				 SSLSocket c = (SSLSocket) f.createSocket();
				 c.setReuseAddress(true);
				 c.setKeepAlive(true);
				 c.connect(new InetSocketAddress(sip, port));
				 c.startHandshake();	
				 sender_sock = c;
			}
			 
		}else{
			sender_sock = new Socket();		
			try {
				sender_sock.setKeepAlive(true);
				sender_sock.setReuseAddress(true);
				sender_sock.connect(new InetSocketAddress(sip, port));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}		
	}
	
	@Override
	public void run() {
		super.run();
		 DataOutputStream dos = null;
		try {
			dos = new DataOutputStream(sender_sock.getOutputStream());
			while(!this.isInterrupted()){
				try {
					ChatMessage data = processing_queue.take();
					ChatMessage.writeData(data, dos);					
				} catch (InterruptedException e) {
					e.printStackTrace();
				}				 
			 }
		} catch (IOException e1) {
			e1.printStackTrace();
			S2s_communicator.remove(sid);
			try {
				sender_sock.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}			
	}		
}

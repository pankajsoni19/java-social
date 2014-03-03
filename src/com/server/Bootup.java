package com.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;

import org.apache.log4j.Logger;

public class Bootup {

	private ServerShutdown hook;
	private HTTPListerner httpListener;
	private HTTPSListener httpsListener;
	private ChatServer chatServer;	
	static Logger logger = Logger.getLogger(Bootup.class);
	private S2s_communicator s2s;
	
	public Bootup() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, KeyManagementException, UnrecoverableKeyException {
		hook = new ServerShutdown();		
		httpListener = new HTTPListerner();
		httpListener.start();
		chatServer = new ChatServer(); 
		chatServer.start();
		
		logger.info("Starting http server on port: " + Config.httpport);
		
		if(Config.isHTTPSsupported){
			httpsListener = new HTTPSListener();
			httpsListener.start();
			logger.info("Starting https server on port: " + Config.httpsport);
		}
		
		Runtime.getRuntime().addShutdownHook(hook);
		CQLHandler.start();
		s2s = new S2s_communicator();
		s2s.start();
	}
	
	public static void main(String[] args) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, KeyManagementException, UnrecoverableKeyException {
		//BasicConfigurator.configure();
		logger.info("Starting Server ....");
		try {
			Config.init();
			MailServer.start();
		} catch (IOException e) {
			System.out.println("Exiting server.. Settings cannot be read.");
			e.printStackTrace();
			System.exit(0);
		} catch (MessagingException e) {
			logger.info("Exiting server.. OutGoing mail transport cannot be opened.");
			e.printStackTrace();
		}		
		
		new Bootup();
		
		if (Boolean.parseBoolean(System.getenv("RUNNING_IN_ECLIPSE")) == true) {
    		logger.info("You're using Eclipse; click in this console and	" +
    						"press ENTER to call System.exit() and run the shutdown routine.");
    		try {
    			System.in.read();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		System.exit(0);
    	}
	}
	
	private class ServerShutdown extends Thread {
		
		@Override
		public void run() {
			super.run();
			logger.info("Shutting down server. Please wait...");
			s2s.stop_s2s();
			CQLHandler.shutdown();
			try {
				MailServer.stop();
			} catch (MessagingException e1) {
				e1.printStackTrace();
			}
			httpListener.stopServer();
			try {
				chatServer.stop();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if(Config.isHTTPSsupported){
				httpListener.stopServer();
			}
		}
	}

}

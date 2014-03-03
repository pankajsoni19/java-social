package com.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public class HTTPListerner extends Thread {
	private HttpServer httpServer;
	
	public void stopServer() {
		httpServer.stop(Config.stopDelay);
	}
	
	@Override
	public void run() {
		super.run();
		try {
			if (Config.isHTTPSsupported){			
				try {
					ServerSocket serversock = new ServerSocket(Config.httpport,Config.maximum_backlog);
					serversock.setReuseAddress(true);
					while(true){
						Socket clientSock = serversock.accept();
						new Thread(new RedirectTraffic(clientSock)).start();				
					}
				} catch (IOException e) {
					e.printStackTrace();
				} 
			} else {
				//LinkedBlockingQueue<String[]> lbq = new LinkedBlockingQueue<>();
				//new LoginMaintainer(lbq).start();    		
				httpServer = HttpServer.create(new InetSocketAddress(Config.httpport), Config.maximum_backlog);
				ThreadPoolExecutor tpe = new ThreadPoolExecutor(4, Config.max_thread_pool_size, 1, 
															TimeUnit.SECONDS,
															new ArrayBlockingQueue<Runnable>(4),
															new ThreadPoolExecutor.AbortPolicy());
				tpe.allowCoreThreadTimeOut(true);
				httpServer.setExecutor(tpe);
				Config.updateContextHandlers(httpServer);
				httpServer.start();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}		 
	}
	
	private class RedirectTraffic implements Runnable{

		private Socket clientSock;
		
		public RedirectTraffic(Socket sock) {
			clientSock = sock;
		}

		@Override
		public void run() {
			String readLine = null;
			BufferedReader bufr;
			try {
				bufr = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
				String location = null;
				while(bufr!=null && (readLine = bufr.readLine())!=null && readLine.length()>0) {
					if(readLine.split(" ")[0].contentEquals("GET")){
					location = 	"Location: https://"+Config.getHost()+readLine.split(" ")[1] +"\r\n";
					}					
				};
				
				BufferedWriter bufwr = new BufferedWriter(new OutputStreamWriter(clientSock.getOutputStream()));
				if(bufwr!=null){
					bufwr.write("HTTP/1.1 301 Moved Permanently\r\n");
					bufwr.write("Cache-Control: private, no-cache, no-store, must-revalidate\r\n");
					bufwr.write("Expires: Sat, 01 Jan 2000 00:00:00 GMT\r\n");
					if(location!=null)
						bufwr.write(location);
					bufwr.write("Connection: close\r\n");
					bufwr.write("Content-Length: 0\r\n\r\n");				
					bufwr.flush();
					bufwr.close();
				}				
				bufr.close();
				clientSock.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
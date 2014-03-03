package com.server;


/*SSLServerSocketFactory sslserversocketfactory =
(SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
SSLServerSocket sslserversocket =
(SSLServerSocket) sslserversocketfactory.createServerSocket(9999);
SSLSocket sslsocket = (SSLSocket) sslserversocket.accept();

*  keytool -genkey -alias my_host -keystore school.jks
*  firstand last name question, answer with domain name
*
*/


import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

@SuppressWarnings("restriction")
public class HTTPSListener extends Thread {

    SSLContext sslContext;
    HttpsServer httpsServer;
    
    public HTTPSListener() throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
    	init();
	}
    
    public void stopServer() {
		httpsServer.stop(Config.stopDelay);
	}
    
    protected void init() throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, KeyManagementException, UnrecoverableKeyException {
        sslContext = SSLContext.getInstance("TLS");
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(Config.cert_store), Config.key_store_password);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, Config.cert_key_password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());        
    }
    

	@Override
    public void run() {
    	super.run();
    	try {    		
	        HttpsConfigurator httpsConfigurator = new HttpsConfigurator(sslContext) {

				@Override
	            public void configure(HttpsParameters httpsParameters) {
	                SSLContext sslContext = getSSLContext();
	                SSLSessionContext sslSessionContext = sslContext.getServerSessionContext();
	                sslSessionContext.setSessionTimeout(0);
	                sslSessionContext.setSessionCacheSize(0);
	                SSLParameters defaultSSLParameters = sslContext.getDefaultSSLParameters();
	                defaultSSLParameters.setNeedClientAuth(false);
	                httpsParameters.setSSLParameters(defaultSSLParameters);
	            }
	        };        

			httpsServer = HttpsServer.create(new InetSocketAddress(Config.httpsport), Config.maximum_backlog);
	        httpsServer.setHttpsConfigurator(httpsConfigurator);
	        ThreadPoolExecutor tpe = new ThreadPoolExecutor(4, 512, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(4),new ThreadPoolExecutor.AbortPolicy());
	        tpe.allowCoreThreadTimeOut(true);
	        httpsServer.setExecutor(tpe);
	        Config.updateContextHandlers(httpsServer);
	        httpsServer.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
    }
}
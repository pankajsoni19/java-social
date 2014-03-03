package com.server;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class BuyerHandler implements HttpHandler {

	static Logger logger = Logger.getLogger(BuyerHandler.class);
	
	
	@Override
	public void handle(HttpExchange he) throws IOException {
		he.close();
	}
}
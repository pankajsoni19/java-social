package com.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.xml.crypto.Data;

import com.datastax.driver.core.utils.UUIDs;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.InetAddresses;

public class Test extends Thread{
	
	@Override
	public void run() {
		super.run();
	
		
		
		
	}
public static void main(String[] args) throws UnsupportedEncodingException, NoSuchAlgorithmException, UnknownHostException {	
	String s = null;
	
	System.out.println("asd" + s.length());
	
	
	
	
}


//TODO: Admin page: Single seller, multiple seller, view user, chat, send_mail
//TODO: move cookies: to cassandra
//TODO: Products types and sub-types
//TODO: delete attachments
//TODO: chat saving..
//TODO: same guid show only latest one.
//TODO: expiring cache map for cookie data
}

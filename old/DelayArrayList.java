package com.server;

import java.util.ArrayList;

public class DelayArrayList extends Thread{

		private ArrayList<DelayedStrings> ds;
		
		public DelayArrayList() {
			ds = new ArrayList();			
		}
		
		@Override
		public void run() {
			super.run();
			while(true){
				synchronized (this) {
					try {
						sleep(1000*60);				//run each minute
					} catch (InterruptedException e) {
						e.printStackTrace();
						break;
					}					
				}
				synchronized (ds) {					
					for(int i=0;i<ds.size();i++){
						if(ds.get(i).expiryTime < System.currentTimeMillis()){
							ds.remove(i);
						}
					}
				}
			}
		}
		
		public void removeData(int i){
			synchronized (ds) {
				ds.remove(i);
			}
		}
		
		public String getData(String resetID) {
			synchronized (ds) {
				for(int i=0;i<ds.size();i++){
					if(ds.get(i).resetid.contentEquals(resetID))
						return i+":"+ds.get(i).uname;
				}	
			}
			return null;
		}
		
		public boolean hasData(final String resetID){
			synchronized (ds) {
				for(int i=0;i<ds.size();i++){
					if(ds.get(i).resetid.contentEquals(resetID))
						return true;
				}				
			}			
			return false;
		}
		
		public void addNewData(String resetID,String uname) {			
			synchronized (ds) {
				for(int i=0;i<ds.size();i++){					
					if(ds.get(i).uname.contentEquals(uname))
						ds.remove(i);
				}
				DelayedStrings dsadd = new DelayedStrings(uname, resetID,(System.currentTimeMillis() 
															+ Config.resetLinkExpiryTime));
				ds.add(dsadd);
			}
		}
		
		private class DelayedStrings{
			public DelayedStrings(String uname2, String resetID2, long l) {
				uname =uname2;
				resetid = resetID2;
				expiryTime = l;
			}
			private String resetid;
			private long expiryTime;
			private String uname;					
		}
}

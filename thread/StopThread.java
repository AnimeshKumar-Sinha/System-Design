package Thread;

import java.util.concurrent.TimeUnit;

//Synchronization
// How long this program will run;

/***
 * 	Synchronization : if you need only inter thread comunication , and not the mutal 
 * Exclusion, the volatile modifer would be acceptable form of synchronization.
 * */

public class StopThread {
	
	private static volatile boolean stopRequested;
	
	public static void main(String args[]) throws InterruptedException {
		
		Thread backgroundThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				int i =0;
				while(!stopRequested) {
				  i++;	
				}
			}
		});
		
		backgroundThread.start();
			TimeUnit.SECONDS.sleep(1);
		stopRequested = true;	
	}
}

package Thread;

public class ConcurrencyRocks {
	
	public static synchronized void main(String args[]) {
		
		Thread t = new Thread() {
			public void run() {
				bar();
			}
		};
		t.run();
		System.out.println("foo ");
	}
	
	static synchronized void bar() {
		
		System.out.print("bar ");
	}

}

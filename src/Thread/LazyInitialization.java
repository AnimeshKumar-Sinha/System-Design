package Thread;

public class LazyInitialization {

	private static boolean initialized = false;

	private static Thread t = new Thread(new Runnable() {

		public void run() {
			initialized = true;
		}
	});

	static {
		t.start();
	}

	public static void main(String args[]) {
		System.out.print(initialized);

		try {
			t.join();
		} catch (InterruptedException ex) {
			throw new AssertionError();
		}
	}
}

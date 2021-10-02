package algo.recursion;

import java.util.ArrayList;
import java.util.List;

public class Arrange {

	public static List<List<Integer>> result = new ArrayList<>();
	static int  temp = 0;
	public static void main(String[] args) {
		
		int[] val = {1,2,3};
		List<Integer> l2=new ArrayList<>();
		arrange(val,0,val.length,l2);
		System.out.print("");	
	}
	
	
	public static void arrange(int[] val, int p, int len, List<Integer> l2) {
		
		if (p == len) {
			
			return;
		}
		
		for (int i = p ; i<len;i++) {
			
		    System.out.println(i +" <-i  IN p -> "+ p );
			l2.add(val[i]);
			List<Integer> l1=new ArrayList<>();
			swap(val,i,p);
			System.out.println(i +" <-i  IN p -> "+ p );
			arrange(val,p+1,len,l1);
			
			System.out.println(i +" <-i  OUT p -> "+ p );
			swap(val,p,i);
			
		}
	
		for(int j=0; j<p;j++) {
			
			l2.add(j,val[j]);
			
		}
		System.out.println(result.toString() );
		
		   result.add(l2);
	}
		
	
	public static void swap(int[] val,int first, int second) {
		int temp = val[first];
		val[first] = val[second];
		val[second] = temp;
	}
		

}

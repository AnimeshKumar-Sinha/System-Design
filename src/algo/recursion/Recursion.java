package algo.recursion;

import java.util.ArrayList;
import java.util.List;

public class Recursion {
	
	List computelist = new ArrayList();
	int tempPointer = 0;
	boolean flag = true;
	int tem = 0;
	int arrTemp [];
	int tempx=0;
	
	int tem1 = 0;
	int arrTemp1[];
	int tempx1=0;
	public void permuterec(int arr[]) {
		
		int len = arr.length -1;
		
		
		if(flag) {
			add(arr);	
			flag = false;
		}
		
		if(tem == len) {
			tempx=0;
			return;
		}
		//[1, 2, 3, 4, |, 2, 1, 3, 4, |, 2, 3, 1, 4, |, 2, 3, 4, 1, |, 3, 2, 4, 1, |, 3, 4, 2, 1, |, 4, 3, 2, 1, |]
		//[1, 2, 3, 4, |, 2, 1, 3, 4, |, 2, 3, 1, 4, |, 2, 3, 4, 1, |, 3, 2, 4, 1, |, 3, 4, 2, 1, |, 4, 3, 2, 1, |, 3, 4, 2, 1, |]
		
		for(int i = 0; i < len - tem; i++) {	
			arrTemp = swap(arr,i, i+1);
			add(arrTemp);
			tempx++;    
		}
		tem++;
		
		permuterec(arrTemp);
		
		for(int j = 0; j < len - tempx1; j++) {	
			arrTemp = swap(arr,j, j+1);
			add(arrTemp);
			tempx1++;    
		}
		tem1++;

	}
	
	private void add(int arr2[]) {
		    for(int n : arr2)
			this.computelist.add(n);
		    
		    this.computelist.add("|");
	}
	
	
	private int[] swap(int []arr, int x, int y) {
		
		int temp = arr[x];
		arr[x] = arr[y];
		arr[y] = temp;
		
		return arr;
	}
	
	private void printList(List ll) {
		
		System.out.print("[");
		for(int i=0; i < ll.size();i++) {
			System.out.print(ll.get(i));
		}
		System.out.print("]");
	}
	
	public void m1() {
		printList(computelist);
	}
	
	public static void main(String args[]) {
		
		Recursion recObj = new Recursion();
		String arr1="1,2,3,4";
		recObj.permute(arr1,0,arr1.length());
		
		
	}
	
	
	private void permute(String str, int mm, int nn) 
    {   int l , r;
        l = mm; r =nn - 1;
          
        if (l == r) 
            System.out.println(str); 
        else { 
            for (int i = l; i <= r; i++) { 
                str = swap(str, l, i); 
                permute(str, l + 1, r); 
                str = swap(str, l, i); 
            } 
        } 
    } 
  
	
	public String swap(String a, int i, int j) 
    { 
        char temp; 
        char[] charArray = a.toCharArray(); 
        temp = charArray[i]; 
        charArray[i] = charArray[j]; 
        charArray[j] = temp; 
       
        return String.valueOf(charArray); 
	
    }
}

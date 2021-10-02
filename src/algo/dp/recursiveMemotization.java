package algo.dp;

public class recursiveMemotization {
	
	
	
	public static void main (String args[]) {
		
		int arr1[] = {-2,1,-3,4,-1,2,1,-5,4};
		printArray(arr1);
		maxCountSum(arr1);
		
	}
	
	
	private static void maxCountSum(int arr3[]) {
		
		int n = arr3.length, maxSum = arr3[0];
		
		for(int i = 1; i < n ; ++i) {
			if(arr3[i-1] > 0) {
				arr3 [i] += arr3[i - 1];
				printArray (arr3);
			}
			maxSum = Math.max(arr3[i] , maxSum);
		}
		System.out.print("maxSum = " + maxSum);
}
	
	private static void printArray(int arr2[]){
		System.out.print("{");
		for(int i = 0; i < arr2.length; i++) {
			System.out.print(arr2[i]);
			System.out.print(",");
		}
		System.out.print("};");
		System.out.println();
	}
	
}

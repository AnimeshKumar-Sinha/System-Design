package algo.bt;

import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

public class PramutationBT {
	

		  public void backtrack(int n,
		                        ArrayList<Integer> nums,
		                        List<List<Integer>> output,
		                        int first) {
		    // if all integers are used up
			  
			System.out.println(n +" <-n  OUT first-> "+ first);
			  
		    if (first == n)
		       output.add(new ArrayList<Integer>(nums));
		    for (int i = first; i < n; i++) {
		      // place i-th integer first 
		      // in the current permutation
		      System.out.println(i +" <-i  IN first-> "+ first);	
		      Collections.swap(nums, first, i);
		      // use next integers to complete the permutations
		      backtrack(n, nums, output, first + 1);
		      // backtrack
		      System.out.println(i +" <-i = BACKTRACK =  first-> "+ first);
		      Collections.swap(nums, first, i);
		    }
		  }

		  public List<List<Integer>> permute(int[] nums) {
		    // init output list
			  
		    List<List<Integer>> output = new LinkedList();

		    // convert nums into list since the output is a list of lists
		    ArrayList<Integer> nums_lst = new ArrayList<Integer>();
		    for (int num : nums)
		      nums_lst.add(num);

		    int n = nums.length;
		    System.out.println(n +" <-n-> "+ 0 );
		    backtrack(n, nums_lst, output, 0);
		    
		    return output;
		  }
		}


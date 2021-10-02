package tree;
	
	class BTNode {
		
		int key;
		BTNode left , right;
		
		public BTNode() {}
		
		public BTNode(int n){
			
			this.key = n;
			this.left = null;
			this.right = null;
		}
	
	}
	
    public class TreeTraversal {
		
		BTNode root;

	    public TreeTraversal (){
		root = null;
	    }
	
	
	//Inorder BT traversal;
	        
	public void inOderTraversal(BTNode node) {
		
		
	   if(node == null) {
			return;
		}
	   
	        //System.out.println("Preorder: "+ node.key);
		
			inOderTraversal(node.left);
			
			//System.out.println("Inorder:  "+ node.key);
			
			inOderTraversal(node.right);
			
			System.out.println("Post-Order: "+ node.key);
			
			
			
	}
	    
	public void inOderTraversal() {
		inOderTraversal(root); 
	}
	
	public static void main(String args[]) {
			
	 TreeTraversal tree = new TreeTraversal();
	   
	   tree.root = new BTNode(1);  
	   
	   tree.root.left = new BTNode(2);
	   tree.root.right = new BTNode(3);
	   
	   tree.root.left.left = new BTNode(4);
	   tree.root.left.right = new BTNode(5);  
	   
	   tree.inOderTraversal();
	   
	   //35416  //34561
	  }
	
}






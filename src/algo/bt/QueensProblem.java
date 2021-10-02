package algo.bt;


public class QueensProblem {
	
	private int noOfQueens;
	private int chessBoard[][];
	
	public QueensProblem (int noOfQueens) {
		this.noOfQueens = noOfQueens;
		this.chessBoard = new int[noOfQueens][noOfQueens];
	}

	private void solve() {
		if(setQueens(0)) {
			printQuens();
		}else {
			System.out.println("There is no valid solution");
		}		
	}


	private boolean setQueens(int colIndex) {
		
		if(colIndex == noOfQueens)
			return true;
		
		for(int rowIndex = 0;rowIndex < noOfQueens; rowIndex++) {
		   
			if(isPlacedValid(colIndex, rowIndex)) {
				
			   chessBoard[colIndex][rowIndex] = 1;	   
				
			
			   if(setQueens(colIndex+1))
			   return true;
			
			
		       chessBoard[colIndex][rowIndex] = 0;
		      
			}
		}
		
		return false;
	}
        

	private boolean isPlacedValid(int colIndex, int rowIndex) {
		
	 
		for(int i=colIndex, j = rowIndex; i >=0 && j >=0; i--,j--)
			if(chessBoard[i][j] == 1)
				return false;
		
		for(int i=colIndex, j = rowIndex; j < noOfQueens && i >=0; i--,j++)
			if(chessBoard[i][j] == 1)
				return false;
		
		for(int i = colIndex; i >=0; i--)
			if(chessBoard[rowIndex][i] == 1)
				return false;
		
		
		
		return true;
	}

	private void printQuens() {
		
		for(int i=0; i < noOfQueens; i ++) {
			for(int j=0; j < noOfQueens; j++) {
				System.out.print(chessBoard[i][j]+","); 
			}
			System.out.println();
		}
		
	}
	
	
    public static void main(String[] args) {
		
		QueensProblem problem = new QueensProblem(4);
		problem.solve();
		
		
	}
	

}

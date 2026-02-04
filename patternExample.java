
public class patternExample{
    public static void main(String[] args) {
        int rows = 5;

        for (int i = 1; i <= rows; i++) {
            for (int j = 1; j <= i; j++) {
                if(j<=i){
                    System.out.print(j);//for numbers otherwise use *
                }
                
            }
            System.out.println();
        }
    }
}
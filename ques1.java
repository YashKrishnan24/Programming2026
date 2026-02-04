public class ques1 {
    public static void main(String[] args) {
        int n=12345;
        int copy=n;
        int pow=1;
        while(copy!=0){
            copy=copy/10;
            pow=pow*10;
        }pow=pow/10;
        System.out.println("Power:" + pow);
        while(n!=0){
            System.out.println(n/pow);
            n=n%pow;
            pow=pow/10;
        }
        }
    }
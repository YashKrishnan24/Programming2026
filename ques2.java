public class ques2 {
    public static void main(String[] args) {
        int n=12345;
        int r=2;
        //get left part and right part
        int pow=(int)Math.pow(10,r);
        int left=n/pow;
        int right=n%pow;
        //System.out.println(right + "" + left);
        //count
        int copy=n;
        int count=0;
        while(copy!=0){
            copy=copy/10;
            count++;
        }
        int n2=right*(int)Math.pow(10,count - r) + left;
        System.out.println(n2);
    }
}       
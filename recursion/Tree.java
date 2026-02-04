public class Tree{

    static void multi(int x){
        if(x<=0){
            return;
        }
        System.out.println("Pre"+x);
        multi(x-1);
        System.out.println("In"+x);
        multi(x-2);
        System.out.println("Post"+x);
    }
    
    public static void main(String[] args) {
        multi(4);
    }
}
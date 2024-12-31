import java.util.ArrayList;

public class example {
    //8h delete, 0.5h new, 24 months
    static int totalTimes=365*2*24;
    static int timesPerDel=16;

    public static void main(String[] args) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int i = 1; i <= totalTimes; i++) {
            list.add(i);
            if(i%timesPerDel==0){
                ArrayList<Integer> newList = new ArrayList<>();
                newList.add(list.get(0));
                double threshold=(i-list.get(0))* 3 /4.0;
                for (int j = 1; j < list.size()-1; j++) {
                    if(i-list.get(j)<=threshold){
                        newList.add(list.get(j));
                        threshold=threshold* 3/4.0;
                    }
                }
                newList.add(list.get(list.size()-1));
                list=newList;
                for (int j = 0; j < list.size(); j++) {
                    System.out.print((i-list.get(j))/2.0+" ");
                }
                System.out.println("\n"+list.size()+"\n");
            }
        }
    }
}

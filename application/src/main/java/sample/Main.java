package sample;

/**
 * Created by sakura on 2016/11/07.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        MyData myData1 = new MyData();
        myData1.setName("name1");
        myData1.setValue("value1");
        MyData myData2 = new MyData();
        myData2.setName("name1");
        myData2.setValue("value1");
        System.out.println(myData1.equals(myData2));
    }
}

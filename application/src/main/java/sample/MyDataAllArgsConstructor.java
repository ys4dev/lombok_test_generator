package sample;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by sakura on 2016/11/14.
 */
@Data
@AllArgsConstructor
public class MyDataAllArgsConstructor {
    private final String str;
    private int n;

    public MyDataAllArgsConstructor(String str) {
        this.str = str;
    }
}

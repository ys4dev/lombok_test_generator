package sample.nest;

import lombok.Data;

/**
 * Created by sakura on 2016/11/14.
 */
public class MyData {
    @Data
    static class MyInner {
        private String str;
    }
}

package sample;

import lombok.Data;

/**
 * Created by sakura on 2016/11/14.
 */
public class MyEnclosing {
    @Data
    private static class MyPrivateEnclosed {
        String str;
    }

    @Data
    public static class MyEnclosed {
        String str;
    }
}

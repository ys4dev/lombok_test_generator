package sample;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by sakura on 2016/11/14.
 */
@Data
public class MyDataCollection {
    private List<String> list;
    private Set<String> set;
    private Map<String, String> map;
}

package sample;

import lombok.Data;
import lombok.ToString;
import lombok.Value;

/**
 * Created by sakura on 2016/11/09.
 *
 */
@Value
@ToString(of = {"name", "value"})
public class MyToString {
    private String name;
    private String value;
    private String ignore;
}

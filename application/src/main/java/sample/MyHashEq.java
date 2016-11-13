package sample;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by sakura on 2016/11/09.
 *
 */
@Data
@EqualsAndHashCode(of = {"name", "value"})
public class MyHashEq {
    private String name;
    private String value;
    private String ignore;
}

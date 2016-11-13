package sample;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by sakura on 2016/11/07.
 *
 */
@Data
public class MyData {
    private String name;
    private String value;
    int n;
    private Date date;
    private LocalDate localDate;
    private LocalTime localTime;
    private LocalDateTime localDateTime;
    private List<String> list;
    private Set<String> set;
    private Map<String, String> map;
}

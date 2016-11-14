package sample;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

/**
 * Created by sakura on 2016/11/14.
 */
@Data
public class MyDataDates {
    private Date date;
    private LocalDate localDate;
    private LocalTime localTime;
}

package org.b3log.symphony.util;

import java.time.LocalTime;

public class ClockEmojiMapper {

    public static String getClockEmojiCode(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();

        boolean half = false;

        if (minute >= 15 && minute < 45) {
            half = true;
        } else if (minute >= 45) {
            hour = (hour + 1) % 24;
        }

        int displayHour = hour % 12;
        if (displayHour == 0) {
            displayHour = 12;
        }

        if (half) {
            return ":clock" + displayHour + "30:";
        } else {
            return ":clock" + displayHour + ":";
        }
    }
}

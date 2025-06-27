package utils;

import controller.ConstantNames;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;



public class DateParser {
    private DateParser(){}

    public static Date parseStringToDate(String date){
        try {
            return new SimpleDateFormat(ConstantNames.FORMATTING_STRING).parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int countMatches(String text, String str)
    {
        if (text.isEmpty() || str.isEmpty()) {
            return 0;
        }

        int index = 0;
        int count = 0;

        while (true)
        {
            index = text.indexOf(str, index);
            if (index != -1)
            {
                count ++;
                index += str.length();
            }
            else {
                break;
            }
        }

        return count;
    }
}
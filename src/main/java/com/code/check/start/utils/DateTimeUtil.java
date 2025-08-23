package com.code.check.start.utils;

import org.springframework.util.ObjectUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @Author yueyue.guan
 * @date 2025/8/21 17:05
 * @desc
 */
public class DateTimeUtil {

    public static class DateFormat{
        public static final String FORMAT_DATETIME_NORMAL="yyyy-MM-dd HH:mm:ss";
        public static final String FORMAT_DATE_NORMAL="yyyy-MM-dd";
        public static final String FORMAT_DATE_NUMBER="yyyy/MM/dd";
        public static final String FORMAT_DATETIME_NUMBER="yyyyMMddHHmmss";
        public static final String FORMAT_DATE_SMALL="yyyyMMdd";
    }

    /**
     *
     * @author 张长朋
     * @date 20187年6月22日 下午1:13:26
     * @version 1.0
     * @description 格式化时间
     *
     * @param date  需要格式化的时间
     * @param dateFormat   DateTimeUtil.DateFormat.xxxx
     * @return
     */
    public static String getFormatDateTime(Date date, String dateFormat){
        if(date==null){
            return null;
        }
        if(!ObjectUtils.isEmpty(dateFormat)){
            dateFormat= DateFormat.FORMAT_DATETIME_NORMAL;
        }
        SimpleDateFormat sdf=new SimpleDateFormat(dateFormat);
        String dateStr=sdf.format(date);
        return dateStr;
    }
}

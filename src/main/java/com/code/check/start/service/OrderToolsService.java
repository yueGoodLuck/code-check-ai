package com.code.check.start.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * @Author yueyue.guan
 * @date 2025/8/13 17:12
 * @desc
 */
@Service
@Slf4j
public class OrderToolsService {


    @Tool(name = "getOrderStatus", description = "根据订单号查询订单状态")
   public String getOrderStatus(
            @ToolParam(description = "订单号") String orderNo
    ){
        log.info("查询订单状态：{}", orderNo);
        return "订单 " + orderNo + " 正在提车中，车辆将在3个工作周内到账";
    }


    @Tool(name = "getWeather", description = "查询城市天气")
    public String getWeather(
            @ToolParam(description = "城市名称") String city
    ) {
        log.info("查询城市天气：{}", city);
        return city + "：晴天，25℃";
    }


}

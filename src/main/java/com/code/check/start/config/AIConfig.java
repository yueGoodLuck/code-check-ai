package com.code.check.start.config;

import com.code.check.start.service.OrderToolsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author yueyue.guan
 * @date 2025/8/13 17:04
 * @desc
 */
@Configuration
public class AIConfig {

    @Bean
    public ChatClient carChatClient(
            ChatClient.Builder builder,
            OrderToolsService orderToolsService // 注入工具
    ) {
        return builder
                .defaultTools(orderToolsService)
                .defaultSystem("你是一名车辆订单助手，帮助用户查询订单状态和操作方法")
                .build();
    }


    @Bean
    public ChatClient gitlabChatClient(
            ChatClient.Builder builder
    ) {
        return builder
                .defaultSystem("你是一名高级代码审计员，帮助企业分析提交代码中的潜在问题")
                .build();
    }

}

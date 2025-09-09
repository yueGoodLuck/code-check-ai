package com.code.check.start.service.notify;

import com.code.check.start.utils.WeChatMessageSplitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author yueyue.guan
 * @date 2025/8/21 15:28
 * @desc
 */
@Slf4j
public class WeChatNotificationService {

    private final String webhookUrl;
    private final OkHttpClient httpClient;

    public WeChatNotificationService(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = new OkHttpClient();
    }

    /**
     * 发送文本消息到企业微信
     * @param content 消息内容
     * @param msgType 消息类型，可以是 "text" 或 "markdown"
     * @return 是否发送成功
     */
    public boolean sendMessage(String content, String msgType) {
        boolean isMarkdown = "markdown".equals(msgType);
        List<String> strings = WeChatMessageSplitter.splitMessage(content, isMarkdown);
        boolean sendRe = true;

        ObjectMapper objectMapper = new ObjectMapper();

        for (String thisStr : strings) {
            try {
                // 构建JSON对象
                Map<String, Object> messageMap = new HashMap<>();
                Map<String, String> contentMap = new HashMap<>();

                contentMap.put("content", thisStr);
                messageMap.put(msgType, contentMap);
                messageMap.put("msgtype", msgType);

                // 转换为JSON字符串
                String jsonBody = objectMapper.writeValueAsString(messageMap);

                RequestBody body = RequestBody.create(
                        jsonBody,
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(webhookUrl)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build();

                log.info("Sending message to WeChat: {}", jsonBody);

                try (Response response = httpClient.newCall(request).execute()) {
                    log.info("Sending message Response: {}", response.body().string());
                    if (response.isSuccessful()) {
                        log.info("Message sent successfully");
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "null";
                        log.error("Send failed: {}, response: {}", response.code(), responseBody);
                        sendRe = false;
                    }
                }

                Thread.sleep(5000L);

            } catch (Exception e) {
                log.error("Error sending message", e);
                sendRe = false;
            }
        }
        return sendRe;
    }

    /**
     * 发送文本消息的便捷方法
     * @param content 消息内容
     * @return 是否发送成功
     */
    public boolean sendTextMessage(String content) {
        return sendMessage(content, "text");
    }

    /**
     * 发送markdown消息的便捷方法
     * @param content markdown格式内容
     * @return 是否发送成功
     */
    public boolean sendMarkdownMessage(String content) {
        return sendMessage(content, "markdown");
    }


    public static void main(String[] args) {
        // 初始化服务
        String webhookUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxxx";
        WeChatNotificationService service = new WeChatNotificationService(webhookUrl);
        // 发送文本消息
        service.sendTextMessage("Hello, this is a test message.");
    }



}

package com.code.check.start.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author yueyue.guan
 * @date 2025/8/13 18:02
 * @desc
 */
@RestController
@Slf4j
public class DeepSeekController {

    @Autowired
    private ChatClient carChatClient;


    @GetMapping("/deep/{prompt}")
    public String chat(@PathVariable(value = "prompt") String prompt) {


        return carChatClient.prompt().user(prompt).call().content();
    }
}

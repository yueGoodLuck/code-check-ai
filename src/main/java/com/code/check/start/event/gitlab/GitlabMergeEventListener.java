package com.code.check.start.event.gitlab;

import com.alibaba.fastjson.JSON;
import com.code.check.start.event.GitlabEvent;
import com.code.check.start.model.CodeSubmission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author yueyue.guan
 * @date 2025/8/19 17:02
 * @desc
 */
@Slf4j
@Service
public class GitlabMergeEventListener extends GitLabEventListenerAbs {


    @Override
    public void onEvent(GitlabEvent event) {
        log.info("gitlab merge 事件监听:{}", JSON.toJSONString(event));
        CodeSubmission submission = event.getSubmission();
        log.info("提交信息: {}", JSON.toJSONString(submission));
    }

    @Override
    public void init() {
        GitlabEventPublisher.addListener( this);
    }
}

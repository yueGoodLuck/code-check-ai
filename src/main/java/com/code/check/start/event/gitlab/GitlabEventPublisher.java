package com.code.check.start.event.gitlab;

import com.code.check.start.event.GitlabEvent;
import com.code.check.start.model.CodeSubmission;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author yueyue.guan
 * @date 2025/8/19 16:59
 * @desc
 */
public class GitlabEventPublisher {

    private static final List<GitLabEventListenerAbs> listeners = new ArrayList<>();

    // 注册监听器
    public static void addListener(GitLabEventListenerAbs listener) {
        listeners.add(listener);
    }

    // 移除监听器
    public static void removeListener(GitLabEventListenerAbs listener) {
        listeners.remove(listener);
    }

    // 发布事件
    public static void publishEvent(CodeSubmission submission) {
        GitlabEvent event = new GitlabEvent("GitlabEventPublisher", submission);
        for (GitLabEventListenerAbs listener : listeners) {
            listener.onEvent(event); // 通知所有监听器
        }
    }

}

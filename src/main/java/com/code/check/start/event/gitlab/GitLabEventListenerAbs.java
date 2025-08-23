package com.code.check.start.event.gitlab;

import com.code.check.start.event.GitlabEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.EventListener;

/**
 * @Author yueyue.guan
 * @date 2025/8/19 16:47
 * @desc
 */
@Component
public abstract class GitLabEventListenerAbs implements EventListener {

    public abstract void onEvent(GitlabEvent event);

    /**
     * 注册当前类到策略map中
     */
    @PostConstruct
    public abstract void init();

}

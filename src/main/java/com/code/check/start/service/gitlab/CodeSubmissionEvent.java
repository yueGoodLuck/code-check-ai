package com.code.check.start.service.gitlab;

import com.code.check.start.model.CodeSubmission;
import org.springframework.context.ApplicationEvent;

/**
 * @Author yueyue.guan
 * @date 2025/8/18 15:55
 * @desc
 */
public class CodeSubmissionEvent extends ApplicationEvent {

    private final CodeSubmission submission;

    public CodeSubmissionEvent(Object source, CodeSubmission submission) {
        super(source);
        this.submission = submission;
    }

    public CodeSubmission getSubmission() {
        return submission;
    }
}

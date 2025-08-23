package com.code.check.start.event;


import com.code.check.start.model.CodeSubmission;

import java.util.EventObject;

/**
 * @Author yueyue.guan
 * @date 2025/8/19 16:45
 * @desc
 */
public class GitlabEvent extends EventObject {

    private CodeSubmission submission;

    public GitlabEvent(Object source, CodeSubmission submission) {
        super(source);
        this.submission = submission;
    }


    public void setSubmission(CodeSubmission submission) {
        this.submission = submission;
    }

    public CodeSubmission getSubmission() {
        return submission;
    }

}

package com.code.check.start.model;

import org.springframework.util.ObjectUtils;

/**
 * @Author yueyue.guan
 * @date 2025/8/20 11:32
 * @desc
 */
public enum GitlabEventType {

    PUSH("Push Hook", "代码提交操作", CodeSubmission.SubmissionType.PUSH),
    MERGE("Merge Request Hook", "合并代码提交操作", CodeSubmission.SubmissionType.MERGE_REQUEST),
    TAG("Tag Push Hook", "TAG创建操作", CodeSubmission.SubmissionType.TAG),
    ;

    private String eventType;

    private String eventName;

    private CodeSubmission.SubmissionType submissionType;


    GitlabEventType(String eventType, String eventName, CodeSubmission.SubmissionType submissionType) {
        this.eventType = eventType;
        this.eventName = eventName;
        this.submissionType = submissionType;
    }

    public static GitlabEventType getByEventType(String eventType) {
        if (ObjectUtils.isEmpty(eventType)) {
            return null;
        }
        for (GitlabEventType value : values()) {
            if (value.eventType.equals(eventType)) {
                return value;
            }
        }
        return null;
    }

    public CodeSubmission.SubmissionType getSubmissionType() {
        return submissionType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventName() {
        return eventName;
    }


}

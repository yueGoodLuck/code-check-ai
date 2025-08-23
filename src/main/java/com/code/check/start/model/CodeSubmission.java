package com.code.check.start.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author yueyue.guan
 * @date 2025/8/18 15:57
 * @desc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSubmission {

    private Long projectId;
    private String projectName;
    private String repositoryUrl;
    private String commitId;
    private Long mergeRequestId;
    private String title;
    private String author;
    private String message;
    private SubmissionType type;

    public enum SubmissionType {
        PUSH, MERGE_REQUEST, TAG
    }

}

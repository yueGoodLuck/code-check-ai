package com.code.check.start.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @Author yueyue.guan
 * @date 2025/8/18 15:56
 * @desc
 */
@Data
@Builder
public class CodeInspectionResult {

    private CodeSubmission submission;
    private boolean success;
    private String summary;
    private List<CodeIssue> issues;
    private long processingTimeMs;
    private String errorMessage;

}

package com.code.check.start.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author yueyue.guan
 * @date 2025/8/21 17:09
 * @desc
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileInspectionResult {

    private String filePath;
    private  Boolean hasIssues;
    private List<CodeIssue> issues;
    private Long processingTimeMs;
    private String fileEvaluation;
}

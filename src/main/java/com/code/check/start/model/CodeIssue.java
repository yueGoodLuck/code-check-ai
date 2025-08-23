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
public class CodeIssue {

    private String fileName;
    private String filePath;
    private String description;
    private Integer lineNumber;
    private String issueType; // 错误|警告|建议
    private String severity; // 高|中|低
    private String suggestedFix;
    private String fixedCodeExample; // 新增字段：修改后的代码示例
    private String reason; // 新增字段：修改原因

}

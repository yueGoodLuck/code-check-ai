package com.code.check.start.service.gitlab;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.code.check.start.model.CodeChange;
import com.code.check.start.model.CodeIssue;
import com.code.check.start.model.CodeSubmission;
import com.code.check.start.model.FileInspectionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AnalysisService {

    @Autowired
    private ChatClient gitlabChatClient;

    @Autowired
    private CodeProcessingService codeProcessingService;

    @Value("${app.code-inspect.timeout-seconds}")
    private int timeoutSeconds;

    /**
     * 按文件分别分析代码提交
     */
    public Map<String, FileInspectionResult> analyzeEachFileInSubmission(CodeSubmission submission) {

        try {
            // 1. 获取按文件组织的代码变更
            Map<String, CodeChange> fileChanges = codeProcessingService.processSubmissionByFile(submission);

            if (fileChanges.isEmpty()) {
                log.info("No code changes to inspect in submission");
                return Collections.emptyMap();
            }

            // 2. 为每个文件单独分析
            Map<String, FileInspectionResult> results = new ConcurrentHashMap<>();
            for (Map.Entry<String, CodeChange> entry : fileChanges.entrySet()) {
                long startTime = System.currentTimeMillis();

                String filePath = entry.getKey();
                CodeChange fileChange = entry.getValue();

                // 为单个文件生成提示
                String promptText = codeProcessingService.generateFilePrompt(fileChange, submission.getMessage());
                log.info("Generated prompt for file: {} promptText:{}", filePath, promptText);

                // 调用AI分析单个文件
                String analysisResult = callAiModel(promptText);
                log.info("analysisResult:{}", analysisResult);

                // 解析该文件的分析结果
                FileInspectionResult fileInspectionResult = parseFileAnalysisResult(analysisResult, filePath, startTime);

                results.put(filePath, fileInspectionResult);
            }
            log.info("代码检测结果 {}", JSON.toJSONString(results));
            return results;

        } catch (Exception e) {
            log.error("Error analyzing code submission", e);
            Map<String, FileInspectionResult> errorResult = new HashMap<>();
            errorResult.put("error", new FileInspectionResult(
                    "error",
                    false,
                    Collections.emptyList(),
                    0L,
                    "文件大模型识别失败"
            ));
            return errorResult;
        }
    }

    /**
     * 调用AI模型分析单个文件
     */
    private String callAiModel(String promptText) {
        Prompt prompt = new Prompt(promptText);

        return gitlabChatClient.prompt().user(prompt.getContents()).call().content();
    }


    /**
     * 解析结构化的文件分析结果
     */
    public static FileInspectionResult parseFileAnalysisResult(String analysisText, String filePath, Long startTime) {
        FileInspectionResult fileInspectionResult = new FileInspectionResult();
        List<CodeIssue> issues = new ArrayList<>();

        try {

            // 解析JSON
            JsonNode rootNode = new ObjectMapper().readTree(analysisText);
            fileInspectionResult = JSONObject.parseObject(analysisText, FileInspectionResult.class);
            // 如果没有问题，直接返回空列表
            if (!rootNode.get("hasIssues").asBoolean()) {
                return fileInspectionResult;
            }

            // 解析问题列表
            JsonNode issuesNode = rootNode.get("issues");
            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    CodeIssue issue = CodeIssue.builder()
                            .fileName(extractFileName(filePath))
                            .filePath(filePath)
                            .description(safeGetText(issueNode, "description"))
                            .lineNumber(safeGetInt(issueNode, "codeLine", -1))
                            .issueType(safeGetText(issueNode, "issueType", "未分类"))
                            .severity(safeGetText(issueNode, "severity", "中"))
                            .suggestedFix(safeGetText(issueNode, "suggestedFix"))
                            .fixedCodeExample(safeGetText(issueNode, "fixedCodeExample"))
                            .reason(safeGetText(issueNode, "reason"))
                            .build();

                    issues.add(issue);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse file analysis result for {}: {}", filePath, e.getMessage());
            // 添加解析错误作为一个问题
            issues.add(CodeIssue.builder()
                    .fileName(extractFileName(filePath))
                    .filePath(filePath)
                    .description("分析结果解析失败：" + e.getMessage())
                    .issueType("系统错误")
                    .severity("高")
                    .suggestedFix("请检查AI返回格式是否符合要求")
                    .build());
        }
        fileInspectionResult.setIssues( issues);
        fileInspectionResult.setFilePath(filePath);
        fileInspectionResult.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        return fileInspectionResult;
    }

    /**
     * 安全获取JSON节点文本
     */
    private static String safeGetText(JsonNode node, String field) {
        return safeGetText(node, field, "");
    }

    /**
     * 安全获取JSON节点文本，带默认值
     */
    private static String safeGetText(JsonNode node, String field, String defaultValue) {
        JsonNode fieldNode = node.get(field);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : defaultValue;
    }

    /**
     * 安全获取JSON节点整数，带默认值
     */
    private static int safeGetInt(JsonNode node, String field, int defaultValue) {
        JsonNode fieldNode = node.get(field);
        return (fieldNode != null && fieldNode.canConvertToInt()) ? fieldNode.asInt() : defaultValue;
    }

    /**
     * 提取文件名
     */
    private static String extractFileName(String filePath) {
        if (filePath == null) {
            return "unknown-file";
        }
        int lastSlashIndex = filePath.lastIndexOf('/');
        return lastSlashIndex > -1 ? filePath.substring(lastSlashIndex + 1) : filePath;
    }


}

package com.code.check.start.service.gitlab;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.code.check.start.model.CodeChange;
import com.code.check.start.model.CodeIssue;
import com.code.check.start.model.CodeSubmission;
import com.code.check.start.model.FileInspectionResult;
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
                String promptText = codeProcessingService.generateFilePrompt2(fileChange, submission.getMessage());
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


    public static FileInspectionResult parseFileAnalysisResult(String analysisText, String filePath, Long startTime) {
        FileInspectionResult fileInspectionResult = new FileInspectionResult();
        List<CodeIssue> issues = new ArrayList<>();

        try {
            // 处理可能包含代码块的JSON字符串
            String cleanJsonText = extractJsonFromText(analysisText);

            // 解析JSON为FileInspectionResult对象
            fileInspectionResult = JSON.parseObject(cleanJsonText, FileInspectionResult.class);

            // 如果没有问题，直接返回
            if (fileInspectionResult.getHasIssues() != null && !fileInspectionResult.getHasIssues()) {
                fileInspectionResult.setFilePath(filePath);
                fileInspectionResult.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                return fileInspectionResult;
            }

            // 解析问题列表
            JSONObject jsonObject = JSON.parseObject(cleanJsonText);
            JSONArray issuesArray = jsonObject.getJSONArray("issues");

            if (issuesArray != null && !issuesArray.isEmpty()) {
                for (int i = 0; i < issuesArray.size(); i++) {
                    JSONObject issueObj = issuesArray.getJSONObject(i);

                    CodeIssue issue = CodeIssue.builder()
                            .fileName(extractFileName(filePath))
                            .filePath(filePath)
                            .description(safeGetString(issueObj, "description"))
                            .lineNumber(safeGetInteger(issueObj, "codeLine", -1))
                            .issueType(safeGetString(issueObj, "issueType", "未分类"))
                            .severity(safeGetString(issueObj, "severity", "中"))
                            .suggestedFix(safeGetString(issueObj, "suggestedFix"))
                            .fixedCodeExample(safeGetString(issueObj, "fixedCodeExample"))
                            .reason(safeGetString(issueObj, "reason"))
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

        fileInspectionResult.setIssues(issues);
        fileInspectionResult.setFilePath(filePath);
        fileInspectionResult.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        return fileInspectionResult;
    }

    /**
     * 从文本中提取JSON内容（处理可能包含代码块的情况）
     */
    private static String extractJsonFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "{}";
        }

        String trimmedText = text.trim();

        // 情况a: 直接就是JSON对象
        if (trimmedText.startsWith("{") && trimmedText.endsWith("}")) {
            return trimmedText;
        }

        // 情况b: 包含```json代码块
        if (trimmedText.startsWith("```json")) {
            int startIndex = trimmedText.indexOf("{");
            int endIndex = trimmedText.lastIndexOf("}");

            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                return trimmedText.substring(startIndex, endIndex + 1);
            }
        }

        // 情况c: 包含```代码块（没有json标识）
        if (trimmedText.startsWith("```")) {
            // 找到第一个```后的内容和最后一个```前的内容
            int firstBacktickEnd = trimmedText.indexOf("```") + 3;
            int lastBacktickStart = trimmedText.lastIndexOf("```");

            if (firstBacktickEnd < lastBacktickStart) {
                String content = trimmedText.substring(firstBacktickEnd, lastBacktickStart).trim();
                // 检查内容是否是JSON
                if (content.startsWith("{") && content.endsWith("}")) {
                    return content;
                }
            }
        }

        // 情况d: 尝试找到JSON对象
        int jsonStart = trimmedText.indexOf("{");
        int jsonEnd = trimmedText.lastIndexOf("}");

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return trimmedText.substring(jsonStart, jsonEnd + 1);
        }

        // 如果都找不到，返回原始文本（让JSON解析器处理异常）
        return trimmedText;
    }


    /**
     * 安全获取字符串值
     */
    private static String safeGetString(JSONObject jsonObject, String key) {
        return safeGetString(jsonObject, key, null);
    }

    private static String safeGetString(JSONObject jsonObject, String key, String defaultValue) {
        if (jsonObject == null || !jsonObject.containsKey(key)) {
            return defaultValue;
        }
        try {
            String value = jsonObject.getString(key);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全获取整数值
     */
    private static Integer safeGetInteger(JSONObject jsonObject, String key, Integer defaultValue) {
        if (jsonObject == null || !jsonObject.containsKey(key)) {
            return defaultValue;
        }
        try {
            return jsonObject.getInteger(key);
        } catch (Exception e) {
            try {
                String strValue = jsonObject.getString(key);
                return strValue != null ? Integer.parseInt(strValue) : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }

    /**
     * 安全获取布尔值
     */
    private static Boolean safeGetBoolean(JSONObject jsonObject, String key, Boolean defaultValue) {
        if (jsonObject == null || !jsonObject.containsKey(key)) {
            return defaultValue;
        }
        try {
            return jsonObject.getBoolean(key);
        } catch (Exception e) {
            try {
                String strValue = jsonObject.getString(key);
                return strValue != null ? Boolean.parseBoolean(strValue) : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }

    /**
     * 提取文件名
     */
    private static String extractFileName(String filePath) {
        if (filePath == null) {
            return "unknown";
        }
        int lastSlash = filePath.lastIndexOf("/");
        if (lastSlash == -1) {
            lastSlash = filePath.lastIndexOf("\\");
        }
        return lastSlash != -1 ? filePath.substring(lastSlash + 1) : filePath;
    }



}

package com.code.check.start.event.gitlab;

import com.alibaba.cloud.nacos.utils.StringUtils;
import com.alibaba.fastjson.JSON;
import com.code.check.start.event.GitlabEvent;
import com.code.check.start.model.CodeIssue;
import com.code.check.start.model.CodeSubmission;
import com.code.check.start.model.FileInspectionResult;
import com.code.check.start.service.gitlab.AnalysisService;
import com.code.check.start.service.notify.WeChatNotificationService;
import com.code.check.start.utils.DateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Date;
import java.util.Map;

/**
 * @Author yueyue.guan
 * @date 2025/8/19 16:46
 * @desc
 */
@Service
@Slf4j
public class GitlabPushEventListener extends GitLabEventListenerAbs {

    @Autowired
    private AnalysisService analysisService;

    @Value("${wechat.webhook-url}")
    private String webhookUrl;

    @Override
    public void onEvent(GitlabEvent event) {
        log.info("gitlab push 事件监听:{}", JSON.toJSONString(event));
        if (!ObjectUtils.isEmpty(event.getSubmission().getMessage()) && event.getSubmission().getMessage().startsWith("Merge branch")){
            log.info("合并请求事件监听不处理:{}", JSON.toJSONString(event));
            return;
        }
        CodeSubmission submission = event.getSubmission();
        Map<String, FileInspectionResult> stringFileInspectionResultMap = analysisService.analyzeEachFileInSubmission(submission);
        String s = generateSummaryNotification(submission, stringFileInspectionResultMap);
        log.info("gitlab push 事件汇总通知:{}", s);
        WeChatNotificationService weChatNotificationService = new WeChatNotificationService(this.webhookUrl);
        weChatNotificationService.sendMarkdownMessage(s);
    }


    @Override
    public void init() {
        GitlabEventPublisher.addListener(this);
    }



    public static String generateSummaryNotification(CodeSubmission submission,
                                                     Map<String, FileInspectionResult> results) {
        StringBuilder markdown = new StringBuilder();

        // 标题部分
        markdown.append("### ⚠️【代码检查】AI检查结果-")
                .append(DateTimeUtil.getFormatDateTime(new Date(), DateTimeUtil.DateFormat.FORMAT_DATE_NORMAL))
                .append("\n\n");

        // 基本信息部分
        markdown.append("**项目：**").append(escapeWeChatMarkdown(submission.getProjectName())).append("\n");
        markdown.append("**提交人：**").append(escapeWeChatMarkdown(submission.getAuthor())).append("\n");
        markdown.append("**提交信息：**").append(escapeWeChatMarkdown(submission.getMessage())).append("\n");
        markdown.append("**检查文件数：**").append(results.size()).append("\n\n");

        // 按文件遍历结果
        int fileIndex = 1;
        for (Map.Entry<String, FileInspectionResult> fileEntry : results.entrySet()) {
            String filePath = fileEntry.getKey();
            FileInspectionResult fileResult = fileEntry.getValue();

            // 文件信息
            markdown.append("#### 文件").append(fileIndex).append("：")
                    .append(escapeWeChatMarkdown(filePath)).append("\n");
            markdown.append("**文件评价：**").append(escapeWeChatMarkdown(fileResult.getFileEvaluation())).append("\n");
            markdown.append("**问题数：**").append(fileResult.getIssues().size()).append("\n");

            // 问题列表
            if (!fileResult.getIssues().isEmpty()) {
                markdown.append("**问题详情：**\n");
                int issueIndex = 1;
                for (CodeIssue issue : fileResult.getIssues()) {
                    markdown.append(issueIndex).append(". **问题等级：**").append(issue.getSeverity()).append("\n");
                    markdown.append("   **代码行数：**").append(!ObjectUtils.isEmpty(issue.getLineNumber() ) && issue.getLineNumber() > 0 ? issue.getLineNumber() : "未知").append("\n");
                    markdown.append("   **问题描述：**").append(escapeWeChatMarkdown(issue.getDescription())).append("\n");

                    // 代码块处理 - 企业微信Markdown支持```代码块
                    if (StringUtils.isNotBlank(issue.getFixedCodeExample())) {
                        markdown.append("   **建议代码：**\n```\n")
                                .append(issue.getFixedCodeExample().trim())
                                .append("\n```\n");
                    }

                    if (StringUtils.isNotBlank(issue.getSuggestedFix())) {
                        markdown.append("   **修改建议：**\n> ")
                                .append(escapeWeChatMarkdown(issue.getSuggestedFix()))
                                .append("\n");
                    }
                    issueIndex++;
                }
            } else {
                markdown.append("✅ **未发现需要修改的问题**\n");
            }

            if (fileIndex < results.size()) {
                markdown.append("\n---\n\n"); // 分隔线
            }
            fileIndex++;
        }

        return markdown.toString();
    }

    /**
     * 转义企业微信Markdown特殊字符
     */
    private static String escapeWeChatMarkdown(String text) {
        if (text == null) {
            return "";
        }
        // 企业微信Markdown需要转义的字符
        return text.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace("#", "\\#")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    /**
     * 转义Markdown特殊字符，避免格式错乱
     */
    public static String escapeMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 转义Markdown特殊字符：* _ [] () # + - . !
        return text.replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }

}

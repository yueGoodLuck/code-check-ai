package com.code.check.start.service.gitlab;

import com.code.check.start.model.CodeChange;
import com.code.check.start.model.CodeLine;
import com.code.check.start.model.CodeSubmission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.Diff;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeProcessingService {

    private final GitLabService gitLabService;

    @Value("${app.code-inspect.ignore-file-types}")
    private String ignoreFileTypes;

    @Value("${app.code-inspect.max-code-lines}")
    private int maxCodeLines;

    private static final Pattern DIFF_HEADER_PATTERN = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");
    private static final Pattern NEW_FILE_PATTERN = Pattern.compile("^\\+\\+\\+ b/(.+)");
    private static final Pattern DELETED_FILE_PATTERN = Pattern.compile("^--- a/(.+)");

    /**
     * 处理代码提交，按文件组织代码变更
     */
    public Map<String, CodeChange> processSubmissionByFile(CodeSubmission submission) {
        try {
            List<Diff> allDiffs = new ArrayList<>();

            // 根据提交类型获取所有差异
            if (submission.getType() == CodeSubmission.SubmissionType.PUSH) {
                allDiffs.addAll(gitLabService.getCommitDiffs(
                        submission.getProjectId(),
                        submission.getCommitId()
                ));
            } else if (submission.getType() == CodeSubmission.SubmissionType.MERGE_REQUEST) {
                List<Commit> commits = gitLabService.getMergeRequestCommits(
                        submission.getProjectId(),
                        submission.getMergeRequestId()
                );

                for (Commit commit : commits) {
                    allDiffs.addAll(gitLabService.getCommitDiffs(submission.getProjectId(), commit.getId()));
                }
            }

            // 按文件分组处理差异
            return groupDiffsByFile(allDiffs);

        } catch (Exception e) {
            log.error("Error processing code submission by file", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 将差异按文件分组，每个文件对应一个CodeChange对象
     */
    private Map<String, CodeChange> groupDiffsByFile(List<Diff> diffs) {
        // 解析需要忽略的文件类型
        List<String> ignoreTypes = Arrays.asList(ignoreFileTypes.split(","));

        // 按文件路径分组
        Map<String, List<Diff>> diffsByFile = diffs.stream()
                .filter(diff -> shouldProcessDiff(diff, ignoreTypes))
                .collect(Collectors.groupingBy(this::getFilePath));

        // 为每个文件创建CodeChange对象
        Map<String, CodeChange> fileChanges = new LinkedHashMap<>();
        for (Map.Entry<String, List<Diff>> entry : diffsByFile.entrySet()) {
            String filePath = entry.getKey();
            List<Diff> fileDiffs = entry.getValue();

            CodeChange codeChange = aggregateDiffsForFile(filePath, fileDiffs);
            fileChanges.put(filePath, codeChange);
        }

        return fileChanges;
    }

    /**
     * 聚合同一文件的所有差异
     */
    private CodeChange aggregateDiffsForFile(String filePath, List<Diff> fileDiffs) {
        String fileName = extractFileName(filePath);
        List<CodeLine> addedLines = new ArrayList<>();
        List<CodeLine> removedLines = new ArrayList<>();
        boolean isNewFile = false;
        boolean isDeleted = false;
        String oldPath = null;

        // 处理文件的所有差异
        for (Diff diff : fileDiffs) {
            isNewFile = isNewFile || diff.getNewFile();
            isDeleted = isDeleted || diff.getDeletedFile();
            if (oldPath == null && diff.getOldPath() != null) {
                oldPath = diff.getOldPath();
            }

            // 解析差异内容
            String diffContent = diff.getDiff();
            if (diffContent != null && !diffContent.isEmpty()) {
                parseDiffContent(diffContent, addedLines, removedLines);
            }
        }

        // 限制处理的代码行数
        if (addedLines.size() > maxCodeLines) {
            addedLines = addedLines.subList(0, maxCodeLines);
            addedLines.add(new CodeLine(-1, "[代码过长，已截断剩余内容]"));
        }

        return CodeChange.builder()
                .fileName(fileName)
                .filePath(filePath)
                .oldPath(oldPath)
                .isNewFile(isNewFile)
                .isDeleted(isDeleted)
                .addedLines(addedLines)
                .removedLines(removedLines)
                .build();
    }

    /**
     * 解析Git diff内容，提取新增和删除的代码行
     * @param diffContent Git diff输出内容
     * @param addedLines 存储新增的代码行
     * @param removedLines 存储删除的代码行
     */
    private void parseDiffContent(String diffContent, List<CodeLine> addedLines, List<CodeLine> removedLines) {
        if (diffContent == null || diffContent.trim().isEmpty()) {
            return;
        }

        String[] lines = diffContent.split("\n");
        int currentLineNumber = 0;
        int oldLineNumber = 0;
        int newLineNumber = 0;
        String currentFile = null;
        boolean inHunk = false;

        for (String line : lines) {
            // 检查是否是文件头
            Matcher newFileMatcher = NEW_FILE_PATTERN.matcher(line);
            if (newFileMatcher.find()) {
                currentFile = newFileMatcher.group(1);
                continue;
            }

            // 检查是否是diff块头
            Matcher diffHeaderMatcher = DIFF_HEADER_PATTERN.matcher(line);
            if (diffHeaderMatcher.find()) {
                inHunk = true;
                // 解析旧文件起始行和行数
                oldLineNumber = Integer.parseInt(diffHeaderMatcher.group(1));
                String oldLineCountStr = diffHeaderMatcher.group(2);
                int oldLineCount = oldLineCountStr != null ? Integer.parseInt(oldLineCountStr) : 1;

                // 解析新文件起始行和行数
                newLineNumber = Integer.parseInt(diffHeaderMatcher.group(3));
                String newLineCountStr = diffHeaderMatcher.group(4);
                int newLineCount = newLineCountStr != null ? Integer.parseInt(newLineCountStr) : 1;

                // 重置当前行号
                currentLineNumber = newLineNumber;
                continue;
            }

            if (!inHunk) {
                continue;
            }

            // 处理diff内容行
            if (line.startsWith("+") && !line.startsWith("++")) {
                // 新增的行
                String code = line.substring(1);
                addedLines.add(new CodeLine(currentLineNumber, code));
                currentLineNumber++;
                newLineNumber++;
            } else if (line.startsWith("-") && !line.startsWith("--")) {
                // 删除的行
                String code = line.substring(1);
                removedLines.add(new CodeLine(oldLineNumber, code));
                oldLineNumber++;
            } else if (line.startsWith(" ")) {
                // 未更改的行，更新行号计数器
                currentLineNumber++;
                oldLineNumber++;
            } else if (line.startsWith("\\")) {
                //  diff的结束标记，如：\ No newline at end of file
                continue;
            } else {
                // 其他情况（可能是diff块结束）
                inHunk = false;
            }
        }
    }

    /**
     * 为单个文件生成AI检查提示
     */
    public String generateFilePrompt(CodeChange fileChange, String commitMessage) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("请检查以下文件的代码变更，专注于找出需要修改的问题(使用阿里巴巴java开发规范,特别检查金额参数不能出现魔法值赋值)：\n\n");
        promptBuilder.append("文件路径: ").append(fileChange.getFilePath()).append("\n");
        promptBuilder.append("提交信息: ").append(commitMessage).append("\n");

        if (fileChange.getIsNewFile()) {
            promptBuilder.append("注意: 这是一个新文件\n");
        }

        if (!fileChange.getAddedLines().isEmpty()) {
            promptBuilder.append("\n新增代码：\n");
            for (int i = 0; i < fileChange.getAddedLines().size(); i++) {
                CodeLine codeLine = fileChange.getAddedLines().get(i);
                promptBuilder.append("+ 第").append(codeLine.getLineNumber()).append("行: ").append(codeLine.getCodeLine()).append("\n");
            }
        }

        if (!fileChange.getRemovedLines().isEmpty() && fileChange.getRemovedLines().size() <= 10) {
            promptBuilder.append("\n删除代码：\n");
            for (int i = 0; i < fileChange.getRemovedLines().size(); i++) {
                CodeLine codeLine = fileChange.getRemovedLines().get(i);
                promptBuilder.append("- 第").append(codeLine.getLineNumber()).append("行: ").append(codeLine.getCodeLine()).append("\n");
            }
        }

        // 严格的格式约束
        promptBuilder.append("\n请严格按照以下JSON格式输出检查结果，不要添加任何额外内容：\n");
        promptBuilder.append("{\n");
        promptBuilder.append("  \"hasIssues\": true/false,\n");
        promptBuilder.append("  \"fileEvaluation\": \"对文件整体的简要评价\",\n");
        promptBuilder.append("  \"issues\": [\n");
        promptBuilder.append("    {\n");
        promptBuilder.append("      \"description\": \"问题详细描述\",\n");
        promptBuilder.append("      \"codeLine\": 问题所在的代码行号（整数，从1开始）,\n");
        promptBuilder.append("      \"issueType\": \"错误|警告|建议\",\n");
        promptBuilder.append("      \"severity\": \"高|中|低\",\n");
        promptBuilder.append("      \"suggestedFix\": \"具体修改建议\",\n");
        promptBuilder.append("      \"fixedCodeExample\": \"修改后的代码示例\",\n");
        promptBuilder.append("      \"reason\": \"修改原因，包括相关规范或风险\"\n");
        promptBuilder.append("    }\n");
        promptBuilder.append("  ]\n");
        promptBuilder.append("}\n");
        promptBuilder.append("说明：\n");
        promptBuilder.append("1. hasIssues为false时，issues数组应为空\n");
        promptBuilder.append("2. codeLine请尽量填写实际行号，无法确定时填-1\n");
        promptBuilder.append("3. 所有字段值均为字符串类型，使用双引号包裹\n");
        promptBuilder.append("4. 确保JSON格式正确，可被标准JSON解析器解析\n");


        return promptBuilder.toString();
    }

    public String generateFilePrompt2(CodeChange fileChange, String commitMessage) {
        StringBuilder promptBuilder = new StringBuilder();

        // 精简指令部分（减少50% Token）
        promptBuilder.append("代码审计：检查代码变更，专注阿里规范。重点金额魔法值参数。\n\n");
        promptBuilder.append("文件: ").append(fileChange.getFilePath()).append("\n");
        promptBuilder.append("提交: ").append(commitMessage).append("\n");

        if (fileChange.getIsNewFile()) {
            promptBuilder.append("[新文件]\n");
        }

        // 优化代码展示格式
        if (!fileChange.getAddedLines().isEmpty()) {
            promptBuilder.append("\n+++ 新增:\n");
            for (CodeLine codeLine : fileChange.getAddedLines()) {
                promptBuilder.append("+L").append(codeLine.getLineNumber())
                        .append(": ").append(codeLine.getCodeLine()).append("\n");
            }
        }

        if (!fileChange.getRemovedLines().isEmpty() && fileChange.getRemovedLines().size() <= 10) {
            promptBuilder.append("\n--- 删除:\n");
            for (CodeLine codeLine : fileChange.getRemovedLines()) {
                promptBuilder.append("-L").append(codeLine.getLineNumber())
                        .append(": ").append(codeLine.getCodeLine()).append("\n");
            }
        }

        // 大幅精简JSON格式说明（减少70% Token）
        promptBuilder.append("\n输出严格JSON格式：\n");
        promptBuilder.append("{\"hasIssues\":bool,\"fileEvaluation\":\"str\",\"issues\":[{\"description\":\"str\",\"codeLine\":int,\"issueType\":\"错误|警告|建议\",\"severity\":\"高|中|低\",\"suggestedFix\":\"str\",\"fixedCodeExample\":\"str\",\"reason\":\"str\"}]}\n");
        promptBuilder.append("规则: hasIssues为false时issues为空; codeLine不确定填-1; 确保JSON可解析");

        return promptBuilder.toString();
    }

    // 其他辅助方法保持不变
    private boolean shouldProcessDiff(Diff diff, List<String> ignoreTypes) {
        if (diff.getDeletedFile()) {
            return false;
        }

        String filePath = getFilePath(diff);
        for (String type : ignoreTypes) {
            if (filePath != null && filePath.endsWith(type)) {
                return false;
            }
        }

        return true;
    }

    private String getFilePath(Diff diff) {
        return diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
    }

    private String extractFileName(String filePath) {
        if (filePath == null) {
            return "unknown-file";
        }
        int lastSlashIndex = filePath.lastIndexOf('/');
        return lastSlashIndex > -1 ? filePath.substring(lastSlashIndex + 1) : filePath;
    }
}

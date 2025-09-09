package com.code.check.start.utils;

import com.alibaba.fastjson.JSON;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 企业微信消息分割工具
 */
public class WeChatMessageSplitter {

    private static final int MAX_MARKDOWN_BYTES = 4096;
    private static final int MAX_TEXT_BYTES = 4000;
    private static final int SAFETY_MARGIN = 1101;
    private static final Pattern FILE_PATTERN = Pattern.compile("文件\\d+：\\s*[^\\n**文件评价]+");

    /**
     * 分割消息为多个片段
     *
     * @param content    原始内容
     * @param isMarkdown 是否为markdown格式
     * @return 分割后的消息片段列表
     */
    public static List<String> splitMessage(String content, boolean isMarkdown) {
        List<String> segments = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            segments.add(content);
            return segments;
        }

        int maxBytes = isMarkdown ? MAX_MARKDOWN_BYTES : MAX_TEXT_BYTES;
        maxBytes -= SAFETY_MARGIN;

        // 如果是文本格式，直接按字符数分割
        if (!isMarkdown) {
            return splitTextMessage(content, maxBytes);
        }

        // Markdown格式，需要智能分割
        return splitMarkdownMessage(content, maxBytes);
    }

    /**
     * 分割文本消息（按字符数简单分割）
     */
    private static List<String> splitTextMessage(String content, int maxBytes) {
        List<String> segments = new ArrayList<>();
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        int totalBytes = contentBytes.length;

        if (totalBytes <= maxBytes) {
            segments.add(content);
            return segments;
        }

        int start = 0;
        int segmentCount = 1;

        while (start < totalBytes) {
            int end = Math.min(start + maxBytes, totalBytes);

            // 如果是中间片段，需要找到合适的截断位置（避免截断中文字符）
            if (end < totalBytes) {
                end = findSafeSplitPosition(contentBytes, start, end);
            }

            String segment = new String(contentBytes, start, end - start, StandardCharsets.UTF_8);
            StringBuilder stringBuilder = new StringBuilder(segment);

            // 添加分段标识
            if (segmentCount > 1 || end < totalBytes) {
                stringBuilder.append("\n").append(String.format("**📝 分段发送 (%d/N)**", segmentCount));
            }

            segments.add(stringBuilder.toString());
            start = end;
            segmentCount++;
        }


        // 更新最后一段的分段标识
        if (segments.size() > 1) {
            for (int i = 0; i < segments.size(); i++) {
                String oldSegment = segments.get(i);
                String newSegment = oldSegment.replace("分段 (" + (i + 1) + "/N)",
                        "分段 (" + (i + 1) + "/" + segments.size() + ")");
                segments.set(i, newSegment);
            }
        }

        return segments;
    }

    /**
     * 分割Markdown消息（在文件描述位置智能分割）
     */
    private static List<String> splitMarkdownMessage(String content, int maxBytes) {
        List<String> segments = new ArrayList<>();
        List<FileSegment> fileSegments = findFileSegments(content);

        int currentStart = 0;
        int segmentCount = 1;

        while (currentStart < content.length()) {
            // 找到当前片段的结束位置
            int endPosition = findNextSegmentEnd(content, currentStart, maxBytes, fileSegments);

            String segment = content.substring(currentStart, endPosition);
            StringBuilder stringBuilder = new StringBuilder(segment);
            // 添加分段标识
            if (segmentCount > 1 || endPosition < content.length()) {
                stringBuilder.append("\n").append(String.format("**📝 分段发送 (%d/N)**", segmentCount));
            }

            segments.add(stringBuilder.toString());
            currentStart = endPosition;
            segmentCount++;

            // 跳过连续的空白行
            while (currentStart < content.length() &&
                    Character.isWhitespace(content.charAt(currentStart))) {
                currentStart++;
            }
        }

        // 更新最后一段的分段标识
        if (segments.size() > 1) {
            for (int i = 0; i < segments.size(); i++) {
                String oldSegment = segments.get(i);

                String newSegment = oldSegment.replace("分段发送 (" + (i + 1) + "/N)",
                        "分段发送 (" + (i + 1) + "/" + segments.size() + ")");
                segments.set(i, newSegment);
            }
        }

        return segments;
    }

    /**
     * 查找所有文件描述段的位置信息
     */
    private static List<FileSegment> findFileSegments(String content) {
        List<FileSegment> segments = new ArrayList<>();
        Matcher matcher = FILE_PATTERN.matcher(content);

        while (matcher.find()) {
            segments.add(new FileSegment(matcher.start(), matcher.end(), matcher.group()));
        }

        return segments;
    }

    /**
     * 找到下一个片段的结束位置
     */
    private static int findNextSegmentEnd(String content, int start, int maxBytes,
                                          List<FileSegment> fileSegments) {
        if (start == 0) {
            maxBytes -= 700;
        }
        // 首先检查从start开始maxBytes字节内的内容
        String testContent = content.substring(start);
        byte[] testBytes = testContent.getBytes(StandardCharsets.UTF_8);

        if (testBytes.length <= maxBytes) {
            return content.length();
        }

        // 计算最大字符数（近似值）
        int maxChars = (int) (maxBytes * 0.8); // 中文字符占3字节，英文字符占1字节

        int potentialEnd = start + Math.min(maxChars, content.length() - start);

        // 尝试在文件描述之前截断
        FileSegment thisSegment = fileSegments.stream().filter(fileSegment -> fileSegment.start > start && fileSegment.start <= potentialEnd &&
                fileSegment.end <= potentialEnd).max((s1, s2) -> Integer.compare(s1.end, s2.end)).orElse(null);
        if (thisSegment != null) {
            return thisSegment.start;
        }

        // 如果没有找到合适的文件描述位置，按字节智能截断
        return findSafeMarkdownSplit(content, start, maxBytes);
    }

    /**
     * 安全地分割Markdown内容
     */
    private static int findSafeMarkdownSplit(String content, int start, int maxBytes) {
        String remainingContent = content.substring(start);
        byte[] remainingBytes = remainingContent.getBytes(StandardCharsets.UTF_8);

        if (remainingBytes.length <= maxBytes) {
            return content.length();
        }

        // 找到最大字节位置
        int byteEnd = start + maxBytes;

        // 向前找段落分隔符
        for (int i = byteEnd; i > start; i--) {
            if (i < content.length() - 2 &&
                    content.charAt(i) == '\n' &&
                    content.charAt(i + 1) == '\n') {
                return i;
            }
        }

        // 向前找行尾
        for (int i = byteEnd; i > start; i--) {
            if (content.charAt(i) == '\n') {
                return i + 1;
            }
        }

        // 按字节截断，避免截断中文字符
        return findSafeByteSplit(content, start, maxBytes);
    }

    /**
     * 按字节安全分割（避免截断中文字符）
     */
    private static int findSafeByteSplit(String content, int start, int maxBytes) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        int totalBytes = contentBytes.length;
        int endByte = Math.min(start + maxBytes, totalBytes);

        // 确保不截断中文字符
        while (endByte > start && (contentBytes[endByte] & 0x80) != 0) {
            endByte--;
        }

        // 转换为字符位置
        String segment = new String(contentBytes, start, endByte - start, StandardCharsets.UTF_8);
        return start + segment.length();
    }

    /**
     * 查找安全的文本分割位置
     */
    private static int findSafeSplitPosition(byte[] bytes, int start, int potentialEnd) {
        // 避免截断中文字符
        for (int i = potentialEnd - 1; i > start; i--) {
            if ((bytes[i] & 0x80) == 0) { // ASCII字符
                return i;
            }
        }
        return potentialEnd;
    }

    /**
     * 文件描述段信息
     */
    private static class FileSegment {
        int start;
        int end;
        String content;

        FileSegment(int start, int end, String content) {
            this.start = start;
            this.end = end;
            this.content = content;
        }
    }

    /**
     * 使用示例
     */
    public static void main(String[] args) {
        // 构建测试内容
        String str = "### ⚠\uFE0F【代码检查】AI检查结果-2025-08-22 18:06:05\\n\\n**项目：**hshccforder\\n**提交人：**管跃跃\\n**提交信息：**story-7424: 分配线索页面样式调整，列表查询效率优化\\n\\n**检查文件数：**7\\n\\n#### 文件1：src/main/resources/mappers/capital/route/CapitalRouteOrderMapper.xml\\n**文件评价：**该文件变更主要涉及SQL查询优化和线索分配逻辑的调整，新增了针对ID列表查询、线索变化统计及最大ID获取的相关SQL语句。整体结构清晰，符合MyBatis XML映射文件的基本规范。\\n**问题数：**0\\n✅ **未发现需要修改的问题**\\n\\n---\\n\\n#### 文件2：src/main/webapp/business/capital/route/index.js\\n**文件评价：**该文件变更涉及页面标题的修改，存在硬编码字符串问题，需要优化以提高代码可维护性。\\n**问题数：**1\\n**问题详情：**\\n1. **问题等级：**中\\n   **代码行数：**2\\n   **问题描述：**代码中存在硬编码的页面标题字符串'线索分配'，属于魔法值赋值问题\\n   **建议代码：**\\n```\\nconst ALLOCATE_CLUE_TITLE = '线索分配';\\nopenOnNewTab(tabId, ALLOCATE_CLUE_TITLE, pageUrl);\\n```\\n   **修改建议：**\\n> 将页面标题定义为常量或从配置文件中获取，避免硬编码\\n\\n---\\n\\n#### 文件3：src/main/java/com/hshc/hshccforder/modules/capital/route/dao/CapitalRouteOrderDaoImpl.java\\n**文件评价：**该文件变更主要为分页查询方法的实现，代码结构清晰，符合基本的DAO层编码规范。\\n**问题数：**0\\n✅ **未发现需要修改的问题**\\n\\n---\\n\\n#### 文件4：src/main/webapp/business/capital/route/clueChangeStaff.html\\n**文件评价：**该文件主要为前端HTML代码，涉及表单和列表展示。整体结构较清晰，但存在样式硬编码、重复ID及元素未闭合等可优化问题。\\n**问题数：**7\\n**问题详情：**\\n1. **问题等级：**高\\n   **代码行数：**21\\n   **问题描述：**HTML标签未正确闭合，缺少对应的结束标签 </div>。\\n   **建议代码：**\\n```\\n</div>\\n```\\n   **修改建议：**\\n> 添加缺失的 </div> 标签以确保HTML结构完整。\\n2. **问题等级：**中\\n   **代码行数：**9\\n   **问题描述：**内联样式 'width: 200px' 和 'width: 150px' 属于魔法值，应通过CSS类管理。\\n   **建议代码：**\\n```\\n<div class=\\\"layui-input-inline input-width-large\\\">\\n```\\n   **修改建议：**\\n> 将内联样式替换为CSS类，例如 class=\\\"input-width-large\\\" 并在CSS中定义宽度。\\n3. **问题等级：**中\\n   **代码行数：**18\\n   **问题描述：**内联样式 'width: 200px' 属于魔法值，应通过CSS类管理。\\n   **建议代码：**\\n```\\n<div class=\\\"layui-input-inline input-width-large\\\">\\n```\\n   **修改建议：**\\n> 将内联样式替换为CSS类，例如 class=\\\"input-width-large\\\" 并在CSS中定义宽度。\\n4. **问题等级：**中\\n   **代码行数：**24\\n   **问题描述：**内联样式 'width: 150px' 属于魔法值，应通过CSS类管理。\\n   **建议代码：**\\n```\\n<div class=\\\"layui-input-inline input-width-medium\\\">\\n```\\n   **修改建议：**\\n> 将内联样式替换为CSS类，例如 class=\\\"input-width-medium\\\" 并在CSS中定义宽度。\\n5. **问题等级：**中\\n   **代码行数：**30\\n   **问题描述：**内联样式 'width: 150px' 属于魔法值，应通过CSS类管理。\\n   **建议代码：**\\n```\\n<div class=\\\"layui-input-inline input-width-medium\\\">\\n```\\n   **修改建议：**\\n> 将内联样式替换为CSS类，例如 class=\\\"input-width-medium\\\" 并在CSS中定义宽度。\\n6. **问题等级：**低\\n   **代码行数：**40\\n   **问题描述：**按钮类名中存在重复类 'overlay-btn'，可能是书写错误。\\n   **建议代码：**\\n```\\nclass=\\\"layui-btn layui-btn-sm op overlay-btn-primary-new overlay-btn-primary\\\"\\n```\\n   **修改建议：**\\n> 移除重复的类名 'overlay-btn'。\\n7. **问题等级：**高\\n   **代码行数：**49\\n   **问题描述：**存在两个 select 元素具有相同的 name 属性 'saleStoreCode'，可能引发表单提交冲突。\\n   **建议代码：**\\n```\\nname=\\\"allocateSaleStoreCode\\\"\\n```\\n   **修改建议：**\\n> 修改第二个 select 的 name 属性为唯一值，例如 'allocateSaleStoreCode'。\\n\\n---\\n\\n#### 文件5：src/main/java/com/hshc/hshccforder/modules/capital/route/service/CapitalRouteOrderServiceImpl.java\\n**文件评价：**该文件主要实现了线索分配页面的查询逻辑优化，通过分页先查询ID再查询详细信息以提升性能。整体结构清晰，但存在部分代码可读性和维护性问题，尤其是魔法值和潜在空指针风险。\\n**问题数：**2\\n**问题详情：**\\n1. **问题等级：**中\\n   **代码行数：**10\\n   **问题描述：**使用了魔法值0作为startIndex的初始值，应使用常量或变量替代以增强可读性和维护性\\n   **建议代码：**\\n```\\nsearchDTO.setStartIndex(dto.getStartIndex() != null ? dto.getStartIndex() : 0);\\n```\\n   **修改建议：**\\n> 定义一个常量或者使用dto中的默认值来代替直接赋值为0\\n2. **问题等级：**高\\n   **代码行数：**19\\n   **问题描述：**未对leadsDeatisResponseDTO.getLeadsBasicResponseDTO\\(\\)返回的对象属性做空校验，可能导致空指针异常\\n   **建议代码：**\\n```\\nLeadsBasicResponseDTO basicDTO = leadsDeatisResponseDTO.getLeadsBasicResponseDTO();\\nif (basicDTO != null) {\\n    vo.setClueSalesStaffName(basicDTO.getSaleName());\\n    vo.setClueSaleStoreName(basicDTO.getSaleDep());\\n    vo.setClueSaleStoreCode(basicDTO.getSaleDepId());\\n}\\n```\\n   **修改建议：**\\n> 在获取具体字段前增加非空判断\\n\\n---\\n\\n#### 文件6：src/main/webapp/business/capital/route/clueChangeStaff.js\\n**文件评价：**该文件主要实现了线索分配页面的初始化逻辑和相关交互功能，整体结构较清晰，但存在多处魔法值使用、潜在错误处理缺失及代码规范问题。\\n**问题数：**8\\n**问题详情：**\\n1. **问题等级：**高\\n   **代码行数：**8\\n   **问题描述：**在ajax请求中使用了魔法值 type: 4，未定义常量或枚举说明其含义\\n   **建议代码：**\\n```\\ndata: {\\\"type\\\": DEPT_TYPE_SALE_STORE}\\n```\\n   **修改建议：**\\n> 将数字4替换为具有明确语义的常量，如DEPT\\_TYPE\\_SALE\\_STORE，并在文件顶部或配置文件中定义该常量\\n2. **问题等级：**高\\n   **代码行数：**25\\n   **问题描述：**split\\('\\_'\\) 后直接通过索引访问数组元素，未校验数组长度可能导致运行时错误\\n   **建议代码：**\\n```\\nvar parts = saleVal.split('_');\\nif(parts.length < 2) {\\n    console.error('Invalid sale value format');\\n    return;\\n}\\nvar result = parts[1];\\n```\\n   **修改建议：**\\n> 在访问parts\\[1\\]之前先判断parts数组的长度是否足够\\n3. **问题等级：**高\\n   **代码行数：**92\\n   **问题描述：**split\\('\\_'\\) 后直接通过索引访问数组元素，未校验数组长度可能导致运行时错误\\n   **建议代码：**\\n```\\nvar staffCheckArr = staffCheckVal.split('_');\\nif(staffCheckArr.length < 2) {\\n    console.error('Invalid allocate sale store value format');\\n    return;\\n}\\nlet saleStoreId=staffCheckArr[1];\\nlet saleStoreCode=staffCheckArr[0];\\n```\\n   **修改建议：**\\n> 在访问staffCheckArr\\[1\\]和staffCheckArr\\[0\\]之前先判断数组长度是否足够\\n4. **问题等级：**中\\n   **代码行数：**72\\n   **问题描述：**分页配置中的limit默认值10为魔法值，应使用常量定义\\n   **建议代码：**\\n```\\nlimit: DEFAULT_PAGE_SIZE, // 默认每页显示条数\\n```\\n   **修改建议：**\\n> 将默认每页显示条数定义为常量，如DEFAULT\\_PAGE\\_SIZE\\n5. **问题等级：**中\\n   **代码行数：**71\\n   **问题描述：**limits数组中的分页选项为魔法值集合，应使用常量定义\\n   **建议代码：**\\n```\\nlimits: PAGE_SIZE_OPTIONS, // 分页选项\\n```\\n   **修改建议：**\\n> 将分页选项定义为常量数组，如PAGE\\_SIZE\\_OPTIONS\\n6. **问题等级：**中\\n   **代码行数：**82\\n   **问题描述：**在initStaffSelect函数中，对后端返回数据的判断条件不完整，仅检查res.code && res.re可能遗漏错误情况\\n   **建议代码：**\\n```\\nif (res.code === 200 && res.re) {\\n```\\n   **修改建议：**\\n> 增加更完整的响应状态判断，如检查res.code是否等于成功状态码\\n7. **问题等级：**低\\n   **代码行数：**23\\n   **问题描述：**代码中存在多个console.log调试语句，在生产环境中应移除\\n   **建议代码：**\\n```\\n// console.log(data)\\n```\\n   **修改建议：**\\n> 移除或注释掉调试用的console.log语句\\n8. **问题等级：**低\\n   **代码行数：**30\\n   **问题描述：**代码中存在多个console.log调试语句，在生产环境中应移除\\n   **建议代码：**\\n```\\n// console.log(selectedText)\\n```\\n   **修改建议：**\\n> 移除或注释掉调试用的console.log语句\\n\\n---\\n\\n#### 文件7：src/main/java/com/hshc/hshccforder/modules/capital/route/pojo/CapitalRouteOrderSearchDTO.java\\n**文件评价：**该文件为数据传输对象（DTO），主要用于封装查询参数。本次变更新增了时间范围查询字段和ID集合字段，整体结构清晰，符合常规DTO设计规范。\\n**问题数：**0\\n✅ **未发现需要修改的问题**\\n";

        // 测试文本分割
        List<String> textSegments = splitMessage(str, true);
        System.out.println("文本分割为 " + textSegments.size() + " 个片段");
        System.out.println(JSON.toJSONString(textSegments));
    }
}
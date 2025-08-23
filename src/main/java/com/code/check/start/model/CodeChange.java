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
public class CodeChange {

    private String fileName;
    private String filePath;
    private String oldPath;
    private Boolean isNewFile;
    private Boolean isDeleted;

    private List<CodeLine> addedLines;

    private List<CodeLine> modifiedLines;

    private List<CodeLine> removedLines;
}

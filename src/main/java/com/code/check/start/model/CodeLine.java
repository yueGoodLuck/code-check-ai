package com.code.check.start.model;

import lombok.Data;

/**
 * @Author yueyue.guan
 * @date 2025/8/23 19:31
 * @desc
 */
@Data
public class CodeLine {

    /**
     * 代码行数
     */
    private Integer lineNumber;

    /**
     * 代码
     */
    private String codeLine;


    public CodeLine(Integer lineNumber, String codeLine) {
        this.codeLine = codeLine;
        this.lineNumber = lineNumber;
    }

    public CodeLine() {
    }
}

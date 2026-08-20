package com.seven7m.kanban.mapper.dev;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 导入映射工具：状态语义映射 + 项目 Key 生成。
 * 纯静态方法，不持有状态，便于测试与复用。
 */
public final class ImportMapper {

    /** 系统默认缺陷流状态的 category 取值（与 ProjectInitializer 保持一致） */
    public static final String CAT_TO_DO = "TODO";
    public static final String CAT_IN_PROGRESS = "IN_PROGRESS";
    public static final String CAT_DONE = "DONE";

    private ImportMapper() {
    }

    /**
     * 将 Excel 原始状态文本映射到系统默认缺陷流状态的 category。
     * 系统默认状态为缺陷流（待确认/再次打开/处理中/已修复/暂不修复/已关闭），
     * 其 category 分别为 TODO / REOPENED / IN_PROGRESS / FIXED / WONT_FIX / CLOSED。
     * 按用户确认：待办→TODO(待确认)、进行中→IN_PROGRESS(处理中)、已完成→CLOSED(DONE，已关闭)。
     * 空/未知统一归 TODO。
     */
    public static String mapStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return CAT_TO_DO;
        }
        // Excel 实际状态取值：已完成/待办/进行中/研发中/测试中/1/进行中,已完成
        String s = raw.trim();
        if (s.contains("已完成")) {
            return CAT_DONE;
        }
        if (s.contains("进行中") || s.equals("研发中") || s.equals("测试中")) {
            return CAT_IN_PROGRESS;
        }
        return CAT_TO_DO;
    }

    /**
     * 将 sheet 名转为安全且可识别的项目 Key。
     * 规则：取非中文非字母数字字符，转 ASCII 安全缩写；无可用字符时回退 IMP + 序号。
     * 调用方负责按已存在 Key 去重（加后缀序号）。
     */
    public static String toProjectKeyBase(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return "IMP";
        }
        // 提取 ASCII 字母/数字
        StringBuilder sb = new StringBuilder();
        for (char c : sheetName.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(Character.toUpperCase(c));
            }
        }
        String base = sb.toString();
        if (base.isEmpty()) {
            // 纯中文名：取其首字符 Unicode 码点派生，保证可读稳定
            int cp = sheetName.codePointAt(0);
            base = "P" + (cp % 1000);
        }
        // 限制长度，避免超长
        return base.length() > 12 ? base.substring(0, 12) : base;
    }

    /**
     * 规范化负责人姓名为 trim 后的非空数组（按中文/英文逗号分割）。
     */
    public static String[] splitAssignees(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return Pattern.compile("[,，]")
                .splitAsStream(raw)
                .map(s -> Normalizer.normalize(s, Normalizer.Form.NFC).trim())
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}

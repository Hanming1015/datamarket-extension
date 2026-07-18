package com.synapse.dataset.util;

import com.synapse.dataset.entity.FieldSchema;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从 CSV 表头 + 采样行推断字段结构(纯函数,无状态)。
 * <ul>
 *   <li>类型:按采样值判 integer / decimal / boolean / date,否则 string</li>
 *   <li>敏感度:按列名关键字启发式初判(owner 可后续覆盖)</li>
 * </ul>
 */
public final class SchemaInferer {

    private SchemaInferer() {
    }

    private static final Pattern INT = Pattern.compile("-?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+\\.\\d+");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}([ T].*)?");
    private static final Set<String> BOOLS = Set.of("true", "false", "yes", "no", "0", "1");

    /** 命中即判敏感(大小写不敏感的子串匹配)。 */
    private static final String[] SENSITIVE_KEYWORDS = {
            "ssn", "social", "email", "phone", "mobile", "tel", "dob", "birth",
            "address", "name", "passport", "idcard", "id_card", "national",
            "credit", "card", "bank", "account", "iban", "tax", "gender",
            "salary", "income", "medical", "health", "diagnos", "password", "secret"
    };

    /**
     * @param headers 列名(表头)
     * @param sampleRows 采样数据行(每行按列对齐的单元格字符串)
     */
    public static List<FieldSchema> infer(List<String> headers, List<List<String>> sampleRows) {
        return headers.stream().map(h -> {
            int idx = headers.indexOf(h);
            String type = inferType(sampleRows, idx);
            boolean sensitive = isSensitive(h);
            return new FieldSchema(h, type, sensitive);
        }).toList();
    }

    private static String inferType(List<List<String>> rows, int col) {
        boolean any = false;
        boolean allInt = true, allDecimal = true, allBool = true, allDate = true;
        for (List<String> row : rows) {
            if (col >= row.size()) {
                continue;
            }
            String v = row.get(col);
            if (v == null || v.isBlank()) {
                continue;
            }
            any = true;
            String s = v.trim();
            if (!INT.matcher(s).matches()) allInt = false;
            if (!DECIMAL.matcher(s).matches() && !INT.matcher(s).matches()) allDecimal = false;
            if (!BOOLS.contains(s.toLowerCase())) allBool = false;
            if (!DATE.matcher(s).matches()) allDate = false;
        }
        if (!any) return "string";
        if (allBool) return "boolean";
        if (allInt) return "integer";
        if (allDecimal) return "decimal";
        if (allDate) return "date";
        return "string";
    }

    private static boolean isSensitive(String header) {
        String h = header.toLowerCase();
        for (String kw : SENSITIVE_KEYWORDS) {
            if (h.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}

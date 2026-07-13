package com.chng.powerexdashboardbackend.utils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CsvExportUtil {

    private CsvExportUtil() {
    }

    public static byte[] toCsvBytes(List<String> headers, List<List<?>> rows) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, headers);
        for (List<?> row : rows) {
            appendRow(sb, row);
        }
        // UTF-8 BOM for better Excel compatibility
        return ("\uFEFF" + sb).getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder sb, List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(format(values.get(i))));
        }
        sb.append('\n');
    }

    private static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalDate d) {
            return d.toString();
        }
        if (value instanceof LocalDateTime dt) {
            return dt.toString();
        }
        return String.valueOf(value);
    }

    private static String escape(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}

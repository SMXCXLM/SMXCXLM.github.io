package com.jsh.plugin.support;

import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.erp.utils.Tools;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 插件通用工具：统一返回结构、租户识别、分页、字符串处理。
 */
public final class PluginUtils {

    private PluginUtils() {
    }

    public static BaseResponseInfo ok(Object data) {
        BaseResponseInfo res = new BaseResponseInfo();
        res.code = 200;
        res.data = data;
        return res;
    }

    public static BaseResponseInfo fail(String message) {
        BaseResponseInfo res = new BaseResponseInfo();
        res.code = 500;
        res.data = message;
        return res;
    }

    public static Map<String, Object> page(Object rows, long total) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("rows", rows);
        map.put("total", total);
        return map;
    }

    /**
     * 从请求头 X-Access-Token 中解析租户id；0 表示超管，不做租户过滤。
     */
    public static Long tenantId(HttpServletRequest request) {
        if (request == null) {
            return 0L;
        }
        String token = request.getHeader("X-Access-Token");
        Long tenantId = Tools.getTenantIdByToken(token);
        return tenantId == null ? 0L : tenantId;
    }

    /**
     * 从请求头 X-Access-Token 中解析用户id；token 第一段为 userId。
     */
    public static Long userId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String token = request.getHeader("X-Access-Token");
        if (isEmpty(token) || token.indexOf("_") < 0) {
            return null;
        }
        String[] parts = token.split("_");
        try {
            return parts.length > 0 ? Long.parseLong(parts[0]) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成租户过滤条件。返回 null 表示无需过滤。
     */
    public static String tenantClause(Long tenantId, String tableAlias) {
        if (tenantId == null || tenantId == 0L) {
            return null;
        }
        return " and " + (tableAlias == null ? "" : tableAlias + ".") + "tenant_id = " + tenantId + " ";
    }

    public static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static String like(String value) {
        return "%" + trim(value) + "%";
    }

    public static int intValue(String value, int defaultValue) {
        try {
            return Integer.parseInt(trim(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static long longValue(String value, long defaultValue) {
        try {
            return Long.parseLong(trim(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** 转义单引号，防止拼接条件被注入 */
    public static String escape(String value) {
        return trim(value).replace("'", "''");
    }
}

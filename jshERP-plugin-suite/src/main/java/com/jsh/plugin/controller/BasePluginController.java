package com.jsh.plugin.controller;

import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.plugin.support.PluginUtils;
import com.jsh.plugin.support.SchemaInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 插件控制器基类：统一的 JdbcTemplate 访问、分页与租户过滤。
 */
public abstract class BasePluginController {

    @Autowired
    protected DataSource dataSource;

    /**
     * 控制器由插件容器保证实例化，因此在这里兜底执行一次建表，
     * 不依赖普通 @Component 是否被插件包扫描到。
     */
    @PostConstruct
    public void initPluginSchema() {
        SchemaInitializer.ensure(dataSource);
    }

    protected JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    protected Map<String, Object> page(String countSql, String dataSql, List<Object> args,
                                       int pageNo, int pageSize) {
        JdbcTemplate t = jdbc();
        Object[] params = args.toArray();
        Long total = t.queryForObject(countSql, params, Long.class);
        int size = pageSize <= 0 ? 20 : pageSize;
        int offset = (Math.max(pageNo, 1) - 1) * size;
        List<Map<String, Object>> rows = t.queryForList(dataSql + " limit " + offset + "," + size, params);
        return PluginUtils.page(rows, total == null ? 0L : total);
    }

    protected List<Object> args() {
        return new ArrayList<Object>();
    }

    protected Long tenant(HttpServletRequest request) {
        return PluginUtils.tenantId(request);
    }

    protected String tenantClause(Long tenantId, String alias) {
        return PluginUtils.tenantClause(tenantId, alias);
    }

    protected BaseResponseInfo error(Exception e) {
        return PluginUtils.fail("查询失败：" + e.getMessage());
    }
}

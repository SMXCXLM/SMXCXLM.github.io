package com.jsh.plugin.controller;

import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.plugin.support.CrudSupport;
import com.jsh.plugin.support.PluginUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件通用单表接口：车辆、客户跟进、资产、提成规则、打印模板、审核流程等基础档案共用。
 */
@RestController
@RequestMapping("/crud")
@Api(tags = {"插件通用档案"})
public class CrudController extends BasePluginController {

    @GetMapping("/tables")
    @ApiOperation(value = "可维护的插件表")
    public BaseResponseInfo tables() {
        return PluginUtils.ok(CrudSupport.TABLES);
    }

    @GetMapping("/columns")
    @ApiOperation(value = "表字段")
    public BaseResponseInfo columns(@RequestParam("table") String table) {
        try {
            CrudSupport.checkTable(table);
            List<Map<String, Object>> rows = jdbc().queryForList(
                    "select column_name, data_type, column_comment from information_schema.columns "
                            + "where table_schema = database() and table_name = ? order by ordinal_position", table);
            return PluginUtils.ok(rows);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/list")
    @ApiOperation(value = "通用列表")
    public BaseResponseInfo list(@RequestParam("table") String table,
                                 @RequestParam(value = "keyword", required = false) String keyword,
                                 @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                 @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                 HttpServletRequest request) {
        try {
            CrudSupport.checkTable(table);
            JdbcTemplate jdbc = jdbc();
            List<String> cols = CrudSupport.columns(jdbc, table);
            Long tenantId = tenant(request);
            List<Object> a = args();
            StringBuilder where = new StringBuilder(" where 1=1 ");
            if (cols.contains("delete_flag")) {
                where.append(" and ifnull(delete_flag,'0') <> '1' ");
            }
            if (tenantId != null && tenantId != 0L && cols.contains("tenant_id")) {
                where.append(" and tenant_id = ? ");
                a.add(tenantId);
            }
            // 精确过滤：请求参数名与字段同名时生效
            Map<String, String[]> params = request.getParameterMap();
            for (Map.Entry<String, String[]> e : params.entrySet()) {
                String key = e.getKey();
                if (key.startsWith("f_")) {
                    key = key.substring(2);
                } else {
                    continue;
                }
                if (cols.contains(key.toLowerCase()) && e.getValue() != null && e.getValue().length > 0
                        && !PluginUtils.isEmpty(e.getValue()[0])) {
                    where.append(" and ").append(key).append(" = ? ");
                    a.add(e.getValue()[0]);
                }
            }
            if (!PluginUtils.isEmpty(keyword)) {
                List<String> texts = CrudSupport.textColumns(jdbc, table);
                if (!texts.isEmpty()) {
                    where.append(" and (");
                    String like = PluginUtils.like(keyword);
                    for (int i = 0; i < texts.size(); i++) {
                        if (i > 0) {
                            where.append(" or ");
                        }
                        where.append(texts.get(i)).append(" like ? ");
                        a.add(like);
                    }
                    where.append(") ");
                }
            }
            String orderBy = cols.contains("id") ? " order by id desc " : "";
            Map<String, Object> data = page("select count(*) from " + table + where,
                    "select * from " + table + where + orderBy, a, currentPage, pageSize);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/get")
    @ApiOperation(value = "通用详情")
    public BaseResponseInfo get(@RequestParam("table") String table, @RequestParam("id") Long id) {
        try {
            CrudSupport.checkTable(table);
            List<Map<String, Object>> rows = jdbc().queryForList("select * from " + table + " where id = ?", id);
            return PluginUtils.ok(rows.isEmpty() ? null : rows.get(0));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/save")
    @ApiOperation(value = "通用保存（有 id 则更新）")
    public BaseResponseInfo save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            String table = String.valueOf(body.get("table"));
            CrudSupport.checkTable(table);
            Long tenantId = tenant(request);
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                CrudSupport.insert(jdbc(), table, body, tenantId);
            } else {
                CrudSupport.update(jdbc(), table, id, body);
            }
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/delete")
    @ApiOperation(value = "通用删除（逻辑删除）")
    public BaseResponseInfo delete(@RequestBody Map<String, Object> body) {
        try {
            String table = String.valueOf(body.get("table"));
            CrudSupport.checkTable(table);
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), table, id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/options")
    @ApiOperation(value = "下拉选项：仓库/商品/人员/客户/账户等")
    public BaseResponseInfo options(@RequestParam("type") String type, HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            String sql;
            if ("depot".equals(type)) {
                sql = "select id as value, name as label from jsh_depot where ifnull(delete_flag,'0') <> '1'";
            } else if ("material".equals(type)) {
                sql = "select m.id as value, concat(m.name, ifnull(concat(' / ',m.standard),''), ifnull(concat(' / ',m.model),'') ) as label, "
                        + "m.unit as unit, m.unit as material_unit, (select e.id from jsh_material_extend e where e.material_id = m.id "
                        + "and ifnull(e.default_flag,'0') = '1' and ifnull(e.delete_flag,'0') <> '1' order by e.id limit 1) as extendId "
                        + "from jsh_material m where ifnull(m.delete_flag,'0') <> '1' and ifnull(m.enabled,'1') <> '0'";
            } else if ("person".equals(type)) {
                sql = "select id as value, name as label from jsh_person where ifnull(delete_flag,'0') <> '1'";
            } else if ("user".equals(type)) {
                sql = "select id as value, concat(username, ' (', ifnull(login_name,''), ')') as label from jsh_user where ifnull(delete_flag,'0') <> '1'";
            } else if ("supplier".equals(type)) {
                sql = "select id as value, supplier as label from jsh_supplier where ifnull(delete_flag,'0') <> '1'";
            } else if ("customer".equals(type)) {
                sql = "select id as value, supplier as label from jsh_supplier where ifnull(delete_flag,'0') <> '1' and type = '客户'";
            } else if ("account".equals(type)) {
                sql = "select id as value, name as label from jsh_account where ifnull(delete_flag,'0') <> '1'";
            } else {
                return PluginUtils.fail("不支持的选项类型：" + type);
            }
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = " + tenantId;
            }
            List<Map<String, Object>> rows = jdbc().queryForList(sql + " order by id desc limit 500");
            return PluginUtils.ok(rows);
        } catch (Exception e) {
            return error(e);
        }
    }


    @SuppressWarnings("unused")
    private Map<String, Object> empty() {
        return new LinkedHashMap<String, Object>();
    }
}

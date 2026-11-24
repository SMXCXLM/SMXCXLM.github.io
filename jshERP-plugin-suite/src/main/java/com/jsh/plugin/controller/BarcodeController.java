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
import java.util.List;
import java.util.Map;

/**
 * 条码打印模板：商品自选、加载全部、模板配置，导入导出由前端 Excel 完成。
 */
@RestController
@RequestMapping("/barcode")
@Api(tags = {"条码打印插件"})
public class BarcodeController extends BasePluginController {

    private static final String PRODUCT_SQL = "select m.id as materialId, m.name as materialName, m.standard as standard, "
            + "m.model as model, m.mnemonic as mnemonic, m.unit as unit, "
            + "me.id as extendId, me.bar_code as barCode, ifnull(me.commodity_unit, m.unit) as saleUnit, "
            + "ifnull(me.sku,'') as sku, ifnull(me.commodity_decimal,0) as commodityPrice, "
            + "ifnull(me.wholesale_decimal,0) as wholesalePrice, ifnull(me.low_decimal,0) as lowPrice, "
            + "ifnull(mcs.current_number,0) as stock "
            + "from jsh_material m join jsh_material_extend me on me.material_id = m.id "
            + "left join jsh_material_current_stock mcs on mcs.material_id = m.id and mcs.depot_id = ? "
            + "and ifnull(mcs.delete_flag,'0') <> '1' "
            + "where ifnull(m.delete_flag,'0') <> '1' and ifnull(me.delete_Flag,'0') <> '1' and m.enabled = 1 ";

    @GetMapping("/products")
    @ApiOperation(value = "条码打印-商品列表")
    public BaseResponseInfo products(@RequestParam(value = "keyword", required = false) String keyword,
                                     @RequestParam(value = "barCode", required = false) String barCode,
                                     @RequestParam(value = "materialName", required = false) String materialName,
                                     @RequestParam(value = "standard", required = false) String standard,
                                     @RequestParam(value = "depotId", required = false) Long depotId,
                                     @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                     @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                     HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            a.add(depotId == null ? 0L : depotId);
            String where = filters(keyword, barCode, materialName, standard, tenantId, a);
            String base = PRODUCT_SQL + where;
            String countSql = "select count(*) from (" + base + ") t";
            return PluginUtils.ok(page(countSql, base + " order by m.name, me.default_flag desc, me.id", a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/products/all")
    @ApiOperation(value = "条码打印-加载全部商品")
    public BaseResponseInfo all(@RequestParam(value = "depotId", required = false) Long depotId,
                                @RequestParam(value = "limit", defaultValue = "2000") Integer limit,
                                HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            a.add(depotId == null ? 0L : depotId);
            String base = PRODUCT_SQL + filters(null, null, null, null, tenantId, a);
            int max = limit == null || limit <= 0 ? 2000 : Math.min(limit, 5000);
            return PluginUtils.ok(jdbc().queryForList(base + " order by m.name, me.default_flag desc, me.id limit " + max, a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/template/list")
    @ApiOperation(value = "条码模板列表")
    public BaseResponseInfo templates(HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            ensureDefaultTemplate(tenantId);
            List<Object> a = args();
            String sql = "select * from jsh_barcode_template where ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by is_default desc, id desc", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/template/save")
    @ApiOperation(value = "保存条码模板")
    public BaseResponseInfo saveTemplate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                CrudSupport.insert(jdbc, "jsh_barcode_template", body, tenantId);
                id = CrudSupport.longVal(body.get("id"));
            } else {
                CrudSupport.update(jdbc, "jsh_barcode_template", id, body);
            }
            if ("1".equals(String.valueOf(body.get("is_default")))) {
                if (tenantId != null && tenantId != 0L) {
                    jdbc.update("update jsh_barcode_template set is_default = '0' where tenant_id = ? and id <> ?", tenantId, id);
                } else {
                    jdbc.update("update jsh_barcode_template set is_default = '0' where id <> ?", id);
                }
                jdbc.update("update jsh_barcode_template set is_default = '1' where id = ?", id);
            }
            body.put("id", id);
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/template/delete")
    @ApiOperation(value = "删除条码模板")
    public BaseResponseInfo deleteTemplate(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_barcode_template", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/template/default")
    @ApiOperation(value = "获取默认条码模板")
    public BaseResponseInfo defaultTemplate(HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            ensureDefaultTemplate(tenantId);
            List<Object> a = args();
            String sql = "select * from jsh_barcode_template where ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            List<Map<String, Object>> rows = jdbc().queryForList(sql + " order by is_default desc, id desc limit 1", a.toArray());
            return PluginUtils.ok(rows.isEmpty() ? null : rows.get(0));
        } catch (Exception e) {
            return error(e);
        }
    }

    private String filters(String keyword, String barCode, String materialName, String standard,
                           Long tenantId, List<Object> a) {
        StringBuilder w = new StringBuilder(" ");
        if (!PluginUtils.isEmpty(keyword)) {
            w.append(" and (m.name like ? or m.mnemonic like ? or m.model like ? or me.bar_code like ? or m.standard like ?) ");
            for (int i = 0; i < 5; i++) {
                a.add(PluginUtils.like(keyword));
            }
        }
        if (!PluginUtils.isEmpty(barCode)) {
            w.append(" and me.bar_code like ? ");
            a.add(PluginUtils.like(barCode));
        }
        if (!PluginUtils.isEmpty(materialName)) {
            w.append(" and m.name like ? ");
            a.add(PluginUtils.like(materialName));
        }
        if (!PluginUtils.isEmpty(standard)) {
            w.append(" and m.standard like ? ");
            a.add(PluginUtils.like(standard));
        }
        if (tenantId != null && tenantId != 0L) {
            w.append(" and m.tenant_id = ? ");
            a.add(tenantId);
        }
        return w.toString();
    }

    private void ensureDefaultTemplate(Long tenantId) {
        JdbcTemplate jdbc = jdbc();
        List<Object> a = new ArrayList<Object>();
        String sql = "select count(*) from jsh_barcode_template where ifnull(delete_flag,'0') <> '1' ";
        if (tenantId != null && tenantId != 0L) {
            sql += " and tenant_id = ? ";
            a.add(tenantId);
        }
        Long count = jdbc.queryForObject(sql, a.toArray(), Long.class);
        if (count != null && count > 0) {
            return;
        }
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("template_name", "默认条码模板");
        body.put("width", 40);
        body.put("height", 30);
        body.put("columns", 3);
        body.put("barcode_height", 30);
        body.put("show_title", "1");
        body.put("title_text", "商品条码");
        body.put("show_name", "1");
        body.put("show_standard", "1");
        body.put("show_unit", "0");
        body.put("show_price", "1");
        body.put("price_type", "commodity");
        body.put("show_barcode", "1");
        body.put("show_shop", "0");
        body.put("is_default", "1");
        CrudSupport.insert(jdbc, "jsh_barcode_template", body, tenantId);
    }
}

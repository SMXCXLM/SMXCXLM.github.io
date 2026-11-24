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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三联打印：模板设置（表头/明细/公司信息/图标自动存库）、单据打印数据。
 */
@RestController
@RequestMapping("/printTemplate")
@Api(tags = {"三联打印插件"})
public class PrintTemplateController extends BasePluginController {

    private static final List<String> BILL_TYPES = Arrays.asList(
            "零售出库", "零售退货", "销售出库", "销售退货", "采购入库", "采购退货",
            "其它入库", "其它出库", "调拨出库", "调拨入库", "收款单", "付款单",
            "收入单", "支出单", "转账单", "组装单", "拆卸单", "盘点录入", "盘点复盘",
            "采购订单", "销售订单");

    @GetMapping("/types")
    @ApiOperation(value = "支持打印的单据类型")
    public BaseResponseInfo types() {
        return PluginUtils.ok(BILL_TYPES);
    }

    @GetMapping("/list")
    @ApiOperation(value = "打印模板列表")
    public BaseResponseInfo list(@RequestParam(value = "billType", required = false) String billType,
                                 HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select * from jsh_print_template where ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(billType)) {
                sql += " and bill_type = ? ";
                a.add(billType.trim());
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by bill_type, is_default desc, id desc", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/save")
    @ApiOperation(value = "保存打印模板（自动存库）")
    public BaseResponseInfo save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                CrudSupport.insert(jdbc, "jsh_print_template", body, tenantId);
                id = CrudSupport.longVal(body.get("id"));
            } else {
                CrudSupport.update(jdbc, "jsh_print_template", id, body);
            }
            if ("1".equals(String.valueOf(body.get("is_default")))) {
                jdbc.update("update jsh_print_template set is_default = '0' where bill_type = ? and id <> ? "
                        + "and tenant_id = ?", body.get("bill_type"), id, tenantId);
                jdbc.update("update jsh_print_template set is_default = '1' where id = ?", id);
            }
            body.put("id", id);
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/delete")
    @ApiOperation(value = "删除打印模板")
    public BaseResponseInfo delete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_print_template", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/data")
    @ApiOperation(value = "单据打印数据（表头+明细+模板）")
    public BaseResponseInfo data(@RequestParam("billType") String billType,
                                 @RequestParam("billId") Long billId,
                                 @RequestParam(value = "templateId", required = false) Long templateId,
                                 HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            List<Map<String, Object>> heads = jdbc.queryForList(
                    "select dh.*, d.name as depotName, s.supplier as organName, u.username as creatorName "
                            + "from jsh_depot_head dh left join jsh_depot d on d.id = (select depot_id from jsh_depot_item "
                            + "where header_id = dh.id limit 1) left join jsh_supplier s on s.id = dh.organ_id "
                            + "left join jsh_user u on u.id = dh.creator where dh.id = ?", billId);
            List<Map<String, Object>> items = jdbc.queryForList(
                    "select i.*, m.name as materialName, m.standard as standard, m.model as model, m.unit as unitName, "
                            + "me.bar_code as barCode from jsh_depot_item i left join jsh_material m on m.id = i.material_id "
                            + "left join jsh_material_extend me on me.id = i.material_extend_id "
                            + "where i.header_id = ? and ifnull(i.delete_flag,'0') <> '1' order by i.id", billId);
            result.put("head", heads.isEmpty() ? null : heads.get(0));
            result.put("items", items);
            List<Map<String, Object>> templates;
            if (templateId != null) {
                templates = jdbc.queryForList("select * from jsh_print_template where id = ?", templateId);
            } else {
                templates = jdbc.queryForList("select * from jsh_print_template where bill_type = ? "
                        + "and ifnull(delete_flag,'0') <> '1' and tenant_id = ? order by is_default desc, id desc limit 1",
                        billType, tenantId);
            }
            result.put("template", templates.isEmpty() ? null : templates.get(0));
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

}

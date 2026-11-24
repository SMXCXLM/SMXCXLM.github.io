package com.jsh.plugin.controller;

import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.plugin.support.CrudSupport;
import com.jsh.plugin.support.PluginUtils;
import com.jsh.plugin.support.StockHelper;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 盘点管理：盘点录入 + 差异复盘（初盘差异绝对值大于 5 的商品强制二次复盘）。
 */
@RestController
@RequestMapping("/stockCheck")
@Api(tags = {"盘点管理插件"})
public class StockCheckController extends BasePluginController {

    /** 复盘阈值：初盘差异绝对值大于该值需要复盘 */
    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("5");

    @GetMapping("/order/list")
    @ApiOperation(value = "盘点单列表")
    public BaseResponseInfo orderList(@RequestParam(value = "status", required = false) String status,
                                      @RequestParam(value = "depotId", required = false) Long depotId,
                                      @RequestParam(value = "keyword", required = false) String keyword,
                                      @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                      @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                      HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select o.*, d.name as depotName, u.username as creatorName, "
                    + "(select count(*) from jsh_sc_item i where i.check_id = o.id and ifnull(i.delete_flag,'0') <> '1') as itemCount, "
                    + "(select count(*) from jsh_sc_item i where i.check_id = o.id and i.need_second = '1' and ifnull(i.delete_flag,'0') <> '1') as secondCount "
                    + "from jsh_sc_order o left join jsh_depot d on d.id = o.depot_id "
                    + "left join jsh_user u on u.id = o.creator where ifnull(o.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and o.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(status)) {
                base += " and o.status = ? ";
                a.add(status.trim());
            }
            if (depotId != null) {
                base += " and o.depot_id = ? ";
                a.add(depotId);
            }
            if (!PluginUtils.isEmpty(keyword)) {
                base += " and o.check_no like ? ";
                a.add(PluginUtils.like(keyword));
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t", base + " order by o.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/order/get")
    @ApiOperation(value = "盘点单详情")
    public BaseResponseInfo orderGet(@RequestParam("id") Long id) {
        try {
            JdbcTemplate jdbc = jdbc();
            List<Map<String, Object>> head = jdbc.queryForList("select o.*, d.name as depotName from jsh_sc_order o "
                    + "left join jsh_depot d on d.id = o.depot_id where o.id = ?", id);
            List<Map<String, Object>> items = jdbc.queryForList("select i.*, m.name as materialName, m.standard as standard, "
                    + "m.model as model, me.bar_code as barCode from jsh_sc_item i "
                    + "left join jsh_material m on m.id = i.material_id "
                    + "left join jsh_material_extend me on me.id = i.material_extend_id "
                    + "where i.check_id = ? and ifnull(i.delete_flag,'0') <> '1' order by i.id", id);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("order", head.isEmpty() ? null : head.get(0));
            data.put("items", items);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/order/create")
    @ApiOperation(value = "新建盘点单（自动带出账面库存）")
    public BaseResponseInfo orderCreate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long depotId = CrudSupport.longVal(body.get("depot_id"));
            if (depotId == null) {
                return PluginUtils.fail("请选择盘点仓库");
            }
            Map<String, Object> head = new LinkedHashMap<String, Object>();
            head.put("check_no", "PD" + System.currentTimeMillis());
            head.put("depot_id", depotId);
            head.put("check_type", PluginUtils.isEmpty(String.valueOf(body.get("check_type"))) ? "全盘" : body.get("check_type"));
            head.put("check_date", body.get("check_date") == null ? new java.util.Date() : body.get("check_date"));
            head.put("status", "0");
            head.put("creator", CrudSupport.longVal(body.get("creator")));
            head.put("remark", body.get("remark"));
            CrudSupport.insert(jdbc, "jsh_sc_order", head, tenantId);
            Long checkId = CrudSupport.longVal(head.get("id"));

            List<Map<String, Object>> stocks = jdbc.queryForList(
                    "select cs.material_id, cs.depot_id, ifnull(cs.current_number,0) as qty, m.name, m.standard, m.model, "
                    + "m.unit, me.sku from jsh_material_current_stock cs "
                    + "join jsh_material m on m.id = cs.material_id "
                    + "left join jsh_material_extend me on me.id = (select id from jsh_material_extend where material_id = cs.material_id "
                    + "and ifnull(default_flag,'0') = '1' and ifnull(delete_flag,'0') <> '1' limit 1) "
                    + "where cs.depot_id = ? and ifnull(cs.delete_flag,'0') <> '1' and ifnull(m.delete_flag,'0') <> '1' "
                    + "and ifnull(cs.current_number,0) <> 0", depotId);
            int count = 0;
            for (Map<String, Object> s : stocks) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("check_id", checkId);
                item.put("material_id", s.get("material_id"));
                item.put("sku", s.get("sku"));
                item.put("unit", s.get("unit"));
                item.put("book_qty", s.get("qty"));
                item.put("first_diff", BigDecimal.ZERO);
                item.put("need_second", "0");
                CrudSupport.insert(jdbc, "jsh_sc_item", item, tenantId);
                count++;
            }
            head.put("itemCount", count);
            return PluginUtils.ok(head);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/order/first")
    @ApiOperation(value = "保存初盘数量（自动标记需复盘商品）")
    @SuppressWarnings("unchecked")
    public BaseResponseInfo saveFirst(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long checkId = CrudSupport.longVal(body.get("id"));
            if (checkId == null) {
                return PluginUtils.fail("缺少盘点单");
            }
            List<Object> items = body.get("items") instanceof List ? (List<Object>) body.get("items") : new ArrayList<Object>();
            int needReview = 0;
            for (Object o : items) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<String, Object> item = (Map<String, Object>) o;
                Long id = CrudSupport.longVal(item.get("id"));
                if (id == null) {
                    continue;
                }
                BigDecimal firstQty = PluginUtils.decimal(item.get("first_qty"));
                List<Map<String, Object>> rows = jdbc.queryForList("select ifnull(book_qty,0) as book_qty from jsh_sc_item where id = ?", id);
                BigDecimal bookQty = rows.isEmpty() ? BigDecimal.ZERO : PluginUtils.decimal(rows.get(0).get("book_qty"));
                BigDecimal diff = firstQty.subtract(bookQty);
                String needSecond = diff.abs().compareTo(REVIEW_THRESHOLD) > 0 ? "1" : "0";
                if ("1".equals(needSecond)) {
                    needReview++;
                }
                jdbc.update("update jsh_sc_item set first_qty = ?, first_diff = ?, need_second = ?, remark = ?, "
                                + "final_qty = case when ? = '0' then ? else final_qty end where id = ?",
                        firstQty, diff, needSecond, item.get("remark"), needSecond, firstQty, id);
            }
            Long pending = jdbc.queryForObject("select count(*) from jsh_sc_item where check_id = ? and need_second = '1' "
                    + "and second_qty is null and ifnull(delete_flag,'0') <> '1'", Long.class, checkId);
            String status = needReview > 0 || (pending != null && pending > 0) ? "1" : "2";
            jdbc.update("update jsh_sc_order set status = ? where id = ?", status, checkId);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("needSecond", needReview);
            data.put("status", status);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/order/second")
    @ApiOperation(value = "保存复盘数量（差异需二次确认）")
    @SuppressWarnings("unchecked")
    public BaseResponseInfo saveSecond(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long checkId = CrudSupport.longVal(body.get("id"));
            if (checkId == null) {
                return PluginUtils.fail("缺少盘点单");
            }
            List<Object> items = body.get("items") instanceof List ? (List<Object>) body.get("items") : new ArrayList<Object>();
            for (Object o : items) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<String, Object> item = (Map<String, Object>) o;
                Long id = CrudSupport.longVal(item.get("id"));
                if (id == null || item.get("second_qty") == null) {
                    continue;
                }
                BigDecimal secondQty = PluginUtils.decimal(item.get("second_qty"));
                List<Map<String, Object>> rows = jdbc.queryForList("select ifnull(book_qty,0) as book_qty from jsh_sc_item where id = ?", id);
                BigDecimal bookQty = rows.isEmpty() ? BigDecimal.ZERO : PluginUtils.decimal(rows.get(0).get("book_qty"));
                BigDecimal diff = secondQty.subtract(bookQty);
                jdbc.update("update jsh_sc_item set second_qty = ?, second_diff = ?, final_qty = ?, remark = ? where id = ?",
                        secondQty, diff, secondQty, item.get("remark"), id);
            }
            Long pending = jdbc.queryForObject("select count(*) from jsh_sc_item where check_id = ? and need_second = '1' "
                    + "and second_qty is null and ifnull(delete_flag,'0') <> '1'", Long.class, checkId);
            if (pending != null && pending == 0) {
                jdbc.update("update jsh_sc_order set status = '2' where id = ?", checkId);
            }
            return PluginUtils.ok("复盘已保存");
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/order/finish")
    @ApiOperation(value = "盘点完成并生成差异出入库")
    public BaseResponseInfo finish(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long checkId = CrudSupport.longVal(body.get("id"));
            if (checkId == null) {
                return PluginUtils.fail("缺少盘点单");
            }
            List<Map<String, Object>> orders = jdbc.queryForList("select * from jsh_sc_order where id = ?", checkId);
            if (orders.isEmpty()) {
                return PluginUtils.fail("盘点单不存在");
            }
            Map<String, Object> order = orders.get(0);
            Long tenantId = CrudSupport.longVal(order.get("tenant_id"));
            Long depotId = CrudSupport.longVal(order.get("depot_id"));
            List<Map<String, Object>> items = jdbc.queryForList("select * from jsh_sc_item where check_id = ? "
                    + "and ifnull(delete_flag,'0') <> '1'", checkId);
            int adjust = 0;
            for (Map<String, Object> item : items) {
                BigDecimal finalQty = item.get("final_qty") == null ? null : PluginUtils.decimal(item.get("final_qty"));
                if (finalQty == null && item.get("second_qty") != null) {
                    finalQty = PluginUtils.decimal(item.get("second_qty"));
                }
                if (finalQty == null && item.get("first_qty") != null) {
                    finalQty = PluginUtils.decimal(item.get("first_qty"));
                }
                if (finalQty == null) {
                    continue;
                }
                BigDecimal bookQty = PluginUtils.decimal(item.get("book_qty"));
                BigDecimal diff = finalQty.subtract(bookQty);
                if (diff.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                StockHelper.move(jdbc, tenantId, diff.compareTo(BigDecimal.ZERO) > 0 ? "入库" : "出库", "盘点",
                        depotId, CrudSupport.longVal(item.get("material_id")),
                        CrudSupport.longVal(item.get("material_extend_id")), String.valueOf(item.get("unit")),
                        item.get("sku") == null ? null : String.valueOf(item.get("sku")), diff.abs(), BigDecimal.ZERO,
                        "盘点单 " + order.get("check_no") + " 差异调整", null);
                adjust++;
            }
            jdbc.update("update jsh_sc_order set status = '3' where id = ?", checkId);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("adjustCount", adjust);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/order/delete")
    @ApiOperation(value = "删除盘点单")
    public BaseResponseInfo orderDelete(@RequestBody Map<String, Object> body) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc, "jsh_sc_order", id);
            jdbc.update("update jsh_sc_item set delete_flag = '1' where check_id = ?", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/order/addItem")
    @ApiOperation(value = "盘点单追加商品")
    public BaseResponseInfo addItem(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long checkId = CrudSupport.longVal(body.get("check_id"));
            Long materialId = CrudSupport.longVal(body.get("material_id"));
            if (checkId == null || materialId == null) {
                return PluginUtils.fail("缺少盘点单或商品");
            }
            Long exists = jdbc.queryForObject("select count(*) from jsh_sc_item where check_id = ? and material_id = ? "
                    + "and ifnull(delete_flag,'0') <> '1'", Long.class, checkId, materialId);
            if (exists != null && exists > 0) {
                return PluginUtils.fail("该商品已在盘点单中");
            }
            List<Map<String, Object>> stock = jdbc.queryForList("select ifnull(current_number,0) as qty, "
                    + "d.name as depotName from jsh_material_current_stock cs left join jsh_depot d on d.id = cs.depot_id "
                    + "where cs.material_id = ? and cs.depot_id = ? and ifnull(cs.delete_flag,'0') <> '1'",
                    materialId, CrudSupport.longVal(body.get("depot_id")));
            body.put("check_id", checkId);
            body.put("book_qty", stock.isEmpty() ? BigDecimal.ZERO : PluginUtils.decimal(stock.get(0).get("qty")));
            body.put("need_second", "0");
            CrudSupport.insert(jdbc, "jsh_sc_item", body, tenant(request));
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

}

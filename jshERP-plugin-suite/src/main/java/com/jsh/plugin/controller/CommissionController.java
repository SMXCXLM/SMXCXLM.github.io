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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业绩提成：统一提成 / 阶梯提成，多人均摊，仅统计付款完成的销售单据。
 */
@RestController
@RequestMapping("/commission")
@Api(tags = {"业绩提成插件"})
public class CommissionController extends BasePluginController {

    @GetMapping("/rule/list")
    @ApiOperation(value = "提成规则列表")
    public BaseResponseInfo ruleList(HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            String sql = "select * from jsh_commission_rule where ifnull(delete_flag,'0') <> '1'";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = " + tenantId;
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by id desc"));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/rule/get")
    @ApiOperation(value = "规则详情（含阶梯区间）")
    public BaseResponseInfo ruleGet(@RequestParam("id") Long id) {
        try {
            JdbcTemplate jdbc = jdbc();
            List<Map<String, Object>> rules = jdbc.queryForList("select * from jsh_commission_rule where id = ?", id);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("rule", rules.isEmpty() ? null : rules.get(0));
            data.put("ladders", jdbc.queryForList("select * from jsh_commission_ladder where rule_id = ? "
                    + "and ifnull(delete_flag,'0') <> '1' order by min_amount", id));
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/rule/save")
    @ApiOperation(value = "保存提成规则（含阶梯区间）")
    @SuppressWarnings("unchecked")
    public BaseResponseInfo ruleSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                CrudSupport.insert(jdbc, "jsh_commission_rule", body, tenantId);
                id = CrudSupport.longVal(body.get("id"));
            } else {
                CrudSupport.update(jdbc, "jsh_commission_rule", id, body);
                jdbc.update("update jsh_commission_ladder set delete_flag = '1' where rule_id = ?", id);
            }
            if (body.get("ladders") instanceof List) {
                for (Object o : (List<Object>) body.get("ladders")) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> l = new LinkedHashMap<String, Object>((Map<String, Object>) o);
                    l.remove("id");
                    l.put("rule_id", id);
                    CrudSupport.insert(jdbc, "jsh_commission_ladder", l, tenantId);
                }
            }
            body.put("id", id);
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/rule/delete")
    @ApiOperation(value = "删除提成规则")
    public BaseResponseInfo ruleDelete(@RequestBody Map<String, Object> body) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc, "jsh_commission_rule", id);
            jdbc.update("update jsh_commission_ladder set delete_flag = '1' where rule_id = ?", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/calc")
    @ApiOperation(value = "按期间试算提成（不写库）")
    public BaseResponseInfo calc(@RequestParam(value = "period", required = false) String period,
                                 @RequestParam(value = "beginTime", required = false) String beginTime,
                                 @RequestParam(value = "endTime", required = false) String endTime,
                                 @RequestParam(value = "ruleId", required = false) Long ruleId,
                                 HttpServletRequest request) {
        try {
            Map<String, Object> data = calculate(period, beginTime, endTime, ruleId, tenant(request));
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/save")
    @ApiOperation(value = "保存提成计算结果")
    public BaseResponseInfo save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long ruleId = CrudSupport.longVal(body.get("ruleId"));
            String period = PluginUtils.trim(String.valueOf(body.get("period")));
            String beginTime = PluginUtils.trim(String.valueOf(body.get("beginTime")));
            String endTime = PluginUtils.trim(String.valueOf(body.get("endTime")));
            Map<String, Object> data = calculate(period, beginTime, endTime, ruleId, tenantId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
            if (!PluginUtils.isEmpty(period)) {
                jdbc.update("update jsh_commission_record set delete_flag = '1' where period = ? and tenant_id = ?",
                        period, tenantId);
            }
            for (Map<String, Object> row : rows) {
                Map<String, Object> record = new LinkedHashMap<String, Object>();
                record.put("bill_id", row.get("billId"));
                record.put("bill_number", row.get("billNumber"));
                record.put("sales_man_id", row.get("salesManId"));
                record.put("sales_man_name", row.get("salesManName"));
                record.put("bill_amount", row.get("billAmount"));
                record.put("rate", row.get("rate"));
                record.put("commission", row.get("commission"));
                record.put("period", PluginUtils.isEmpty(period) ? row.get("period") : period);
                CrudSupport.insert(jdbc, "jsh_commission_record", record, tenantId);
            }
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("saved", rows.size());
            result.put("summary", data.get("summary"));
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/record/list")
    @ApiOperation(value = "提成记录")
    public BaseResponseInfo recordList(@RequestParam(value = "period", required = false) String period,
                                       @RequestParam(value = "salesManName", required = false) String salesManName,
                                       @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                       @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                       HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select * from jsh_commission_record where ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(period)) {
                base += " and period = ? ";
                a.add(period.trim());
            }
            if (!PluginUtils.isEmpty(salesManName)) {
                base += " and sales_man_name like ? ";
                a.add(PluginUtils.like(salesManName));
            }
            Map<String, Object> data = page("select count(*) from (" + base + ") t", base + " order by id desc",
                    a, currentPage, pageSize);
            List<Object> sa = args();
            String sumSql = "select round(ifnull(sum(commission),0),2) as commission, count(*) as billCount "
                    + "from jsh_commission_record where ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sumSql += " and tenant_id = ? ";
                sa.add(tenantId);
            }
            if (!PluginUtils.isEmpty(period)) {
                sumSql += " and period = ? ";
                sa.add(period.trim());
            }
            List<Map<String, Object>> sums = jdbc().queryForList(sumSql, sa.toArray());
            data.put("summary", sums.isEmpty() ? null : sums.get(0));
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/record/delete")
    @ApiOperation(value = "删除提成记录")
    public BaseResponseInfo recordDelete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_commission_record", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 计算逻辑 ----------------

    private Map<String, Object> calculate(String period, String beginTime, String endTime, Long ruleId, Long tenantId) {
        JdbcTemplate jdbc = jdbc();
        Map<String, Object> rule = loadRule(jdbc, ruleId, tenantId);
        String type = rule == null ? "1" : String.valueOf(rule.get("type"));
        BigDecimal value = rule == null ? BigDecimal.ZERO : PluginUtils.decimal(rule.get("value"));
        String valueType = rule == null ? "1" : String.valueOf(rule.get("value_type"));
        List<Map<String, Object>> ladders = rule == null ? new ArrayList<Map<String, Object>>()
                : jdbc.queryForList("select * from jsh_commission_ladder where rule_id = ? and ifnull(delete_flag,'0') <> '1' "
                + "order by min_amount", rule.get("id"));

        List<Object> a = args();
        String sql = "select dh.id as billId, dh.number as billNumber, dh.sub_type as subType, dh.oper_time as operTime, "
                + "dh.sales_man as salesMan, dh.total_price as totalPrice, "
                + "round(ifnull(sum(abs(de.basic_number)),0),2) as totalQty, round(ifnull(sum(ifnull(de.all_price,0)),0),2) as amount "
                + "from jsh_depot_head dh join jsh_depot_item de on de.header_id = dh.id "
                + "where dh.type = '出库' and dh.sub_type in ('销售','零售') and dh.status = '2' "
                + "and ifnull(dh.delete_flag,'0') <> '1' and ifnull(de.delete_flag,'0') <> '1' ";
        if (!PluginUtils.isEmpty(period)) {
            sql += " and date_format(dh.oper_time,'%Y-%m') = ? ";
            a.add(period.trim());
        }
        if (!PluginUtils.isEmpty(beginTime)) {
            sql += " and dh.oper_time >= ? ";
            a.add(beginTime.trim() + " 00:00:00");
        }
        if (!PluginUtils.isEmpty(endTime)) {
            sql += " and dh.oper_time <= ? ";
            a.add(endTime.trim() + " 23:59:59");
        }
        if (tenantId != null && tenantId != 0L) {
            sql += " and dh.tenant_id = ? ";
            a.add(tenantId);
        }
        sql += " group by dh.id, dh.number, dh.sub_type, dh.oper_time, dh.sales_man, dh.total_price "
                + "order by dh.oper_time";

        List<Map<String, Object>> bills = jdbc.queryForList(sql, a.toArray());
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        BigDecimal totalCommission = BigDecimal.ZERO;
        for (Map<String, Object> bill : bills) {
            BigDecimal amount = PluginUtils.decimal(bill.get("amount"));
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                amount = PluginUtils.decimal(bill.get("totalPrice"));
            }
            BigDecimal qty = PluginUtils.decimal(bill.get("totalQty"));
            BigDecimal rate = BigDecimal.ZERO;
            BigDecimal commission;
            if ("2".equals(type)) {
                rate = ladderRate(ladders, amount);
                commission = amount.multiply(rate).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
            } else if ("2".equals(valueType)) {
                commission = qty.multiply(value).setScale(2, BigDecimal.ROUND_HALF_UP);
                rate = amount.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                        : commission.multiply(new BigDecimal("100")).divide(amount, 2, BigDecimal.ROUND_HALF_UP);
            } else {
                rate = value;
                commission = amount.multiply(rate).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
            }
            List<String> salesMen = splitSalesMen(String.valueOf(bill.get("salesMan")));
            if (salesMen.isEmpty()) {
                salesMen.add("未指定");
            }
            BigDecimal share = commission.divide(new BigDecimal(salesMen.size()), 2, BigDecimal.ROUND_HALF_UP);
            for (String name : salesMen) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("billId", bill.get("billId"));
                row.put("billNumber", bill.get("billNumber"));
                row.put("subType", bill.get("subType"));
                row.put("operTime", String.valueOf(bill.get("operTime")));
                row.put("period", periodOf(bill.get("operTime")));
                row.put("salesManName", name);
                row.put("salesManId", resolvePersonId(jdbc, name, tenantId));
                row.put("billAmount", amount);
                row.put("billQty", qty);
                row.put("rate", rate);
                row.put("commission", share);
                row.put("shareCount", salesMen.size());
                rows.add(row);
                totalCommission = totalCommission.add(share);
            }
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", rows);
        data.put("total", rows.size());
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("billCount", bills.size());
        summary.put("commission", totalCommission);
        summary.put("rule", rule);
        data.put("summary", summary);
        return data;
    }

    private Map<String, Object> loadRule(JdbcTemplate jdbc, Long ruleId, Long tenantId) {
        if (ruleId != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("select * from jsh_commission_rule where id = ?", ruleId);
            return rows.isEmpty() ? null : rows.get(0);
        }
        String sql = "select * from jsh_commission_rule where ifnull(delete_flag,'0') <> '1' and ifnull(enabled,'1') <> '0'";
        if (tenantId != null && tenantId != 0L) {
            sql += " and tenant_id = " + tenantId;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql + " order by id desc limit 1");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BigDecimal ladderRate(List<Map<String, Object>> ladders, BigDecimal amount) {
        for (Map<String, Object> l : ladders) {
            BigDecimal min = PluginUtils.decimal(l.get("min_amount"));
            BigDecimal max = l.get("max_amount") == null ? null : PluginUtils.decimal(l.get("max_amount"));
            if (amount.compareTo(min) >= 0 && (max == null || amount.compareTo(max) <= 0)) {
                return PluginUtils.decimal(l.get("rate"));
            }
        }
        return BigDecimal.ZERO;
    }

    private List<String> splitSalesMen(String value) {
        List<String> list = new ArrayList<String>();
        if (PluginUtils.isEmpty(value)) {
            return list;
        }
        String[] parts = value.split("[,，;；/|]");
        for (String p : parts) {
            if (!PluginUtils.isEmpty(p)) {
                list.add(p.trim());
            }
        }
        return list;
    }

    private Long resolvePersonId(JdbcTemplate jdbc, String name, Long tenantId) {
        String sql = "select id from jsh_person where name = ? and ifnull(delete_flag,'0') <> '1'";
        if (tenantId != null && tenantId != 0L) {
            sql += " and tenant_id = " + tenantId;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql + " limit 1", name);
        return rows.isEmpty() ? null : CrudSupport.longVal(rows.get(0).get("id"));
    }

    private String periodOf(Object time) {
        if (time == null) {
            return null;
        }
        String s = String.valueOf(time);
        return s.length() >= 7 ? s.substring(0, 7) : s;
    }

}

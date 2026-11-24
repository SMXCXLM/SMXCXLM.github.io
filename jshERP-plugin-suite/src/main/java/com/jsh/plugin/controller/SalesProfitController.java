package com.jsh.plugin.controller;

import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.plugin.support.PluginUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售利润模板：单据级利润、商品明细级利润，支持按单号/条码/名称/规格/时间段查询与合计。
 */
@RestController
@RequestMapping("/salesProfit")
@Api(tags = {"销售利润模板"})
public class SalesProfitController extends BasePluginController {

    private static final String COST = "de.basic_number * ifnull(nullif(de.purchase_unit_price,0), "
            + "ifnull(nullif(mcs.current_unit_price,0), ifnull(me.purchase_decimal,0)))";

    @GetMapping("/bills")
    @ApiOperation(value = "销售利润-按单据汇总")
    public BaseResponseInfo bills(@RequestParam(value = "beginTime", required = false) String beginTime,
                                  @RequestParam(value = "endTime", required = false) String endTime,
                                  @RequestParam(value = "billNumber", required = false) String billNumber,
                                  @RequestParam(value = "barCode", required = false) String barCode,
                                  @RequestParam(value = "materialName", required = false) String materialName,
                                  @RequestParam(value = "standard", required = false) String standard,
                                  @RequestParam(value = "subType", required = false) String subType,
                                  @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                  @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                  HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select dh.id as billId, dh.number as billNumber, dh.sub_type as subType, "
                    + "date_format(dh.oper_time,'%Y-%m-%d %H:%i:%s') as operTime, s.supplier as organName, "
                    + "count(de.id) as itemCount, round(ifnull(sum(de.basic_number),0),2) as qty, "
                    + "round(ifnull(sum(ifnull(de.all_price,0)),0),2) as amount, "
                    + "round(ifnull(sum(" + COST + "),0),2) as cost, "
                    + "round(ifnull(sum(ifnull(de.all_price,0)),0) - ifnull(sum(" + COST + "),0),2) as profit, "
                    + "round(case when ifnull(sum(ifnull(de.all_price,0)),0) = 0 then 0 else "
                    + "100 * (ifnull(sum(ifnull(de.all_price,0)),0) - ifnull(sum(" + COST + "),0)) / ifnull(sum(ifnull(de.all_price,0)),0) end,2) as profitRate "
                    + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + "left join jsh_supplier s on s.id = dh.organ_id "
                    + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id "
                    + "and ifnull(mcs.delete_flag,'0') <> '1' "
                    + where(beginTime, endTime, billNumber, barCode, materialName, standard, subType, tenantId, a)
                    + " group by dh.id, dh.number, dh.sub_type, dh.oper_time, s.supplier ";
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by dh.oper_time desc, dh.id desc", a, currentPage, pageSize);
            Map<String, Object> result = new HashMap<String, Object>(data);
            result.put("summary", summary(beginTime, endTime, billNumber, barCode, materialName, standard, subType, tenantId, true));
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/items")
    @ApiOperation(value = "销售利润-商品明细")
    public BaseResponseInfo items(@RequestParam(value = "beginTime", required = false) String beginTime,
                                  @RequestParam(value = "endTime", required = false) String endTime,
                                  @RequestParam(value = "billNumber", required = false) String billNumber,
                                  @RequestParam(value = "barCode", required = false) String barCode,
                                  @RequestParam(value = "materialName", required = false) String materialName,
                                  @RequestParam(value = "standard", required = false) String standard,
                                  @RequestParam(value = "subType", required = false) String subType,
                                  @RequestParam(value = "billId", required = false) Long billId,
                                  @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                  @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                  HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select de.id as itemId, dh.id as billId, dh.number as billNumber, dh.sub_type as subType, "
                    + "date_format(dh.oper_time,'%Y-%m-%d %H:%i:%s') as operTime, s.supplier as organName, "
                    + "m.name as materialName, m.standard as standard, m.model as model, me.bar_code as barCode, "
                    + "de.material_unit as unit, round(ifnull(de.basic_number,0),2) as qty, "
                    + "round(ifnull(de.all_price,0),2) as amount, round(" + COST + ",2) as cost, "
                    + "round(ifnull(de.all_price,0) - (" + COST + "),2) as profit, "
                    + "round(case when ifnull(de.all_price,0) = 0 then 0 else 100 * (ifnull(de.all_price,0) - (" + COST + ")) / de.all_price end,2) as profitRate "
                    + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + "left join jsh_supplier s on s.id = dh.organ_id "
                    + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id "
                    + "and ifnull(mcs.delete_flag,'0') <> '1' "
                    + where(beginTime, endTime, billNumber, barCode, materialName, standard, subType, tenantId, a);
            if (billId != null) {
                base += " and de.header_id = ? ";
                a.add(billId);
            }
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by dh.oper_time desc, de.id", a, currentPage, pageSize);
            Map<String, Object> result = new HashMap<String, Object>(data);
            Map<String, Object> sum = summary(beginTime, endTime, billNumber, barCode, materialName, standard, subType, tenantId, false);
            if (billId != null) {
                Map<String, Object> one = summaryByBill(billId, tenantId);
                sum.putAll(one);
            }
            result.put("summary", sum);
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/detail")
    @ApiOperation(value = "销售利润-单张单据明细")
    public BaseResponseInfo detail(@RequestParam("billId") Long billId, HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            Map<String, Object> sum = summaryByBill(billId, tenantId);
            List<Object> a = new ArrayList<Object>();
            a.add(billId);
            String sql = "select de.id as itemId, m.name as materialName, m.standard as standard, m.model as model, "
                    + "me.bar_code as barCode, de.material_unit as unit, round(ifnull(de.basic_number,0),2) as qty, "
                    + "round(ifnull(de.all_price,0),2) as amount, round(" + COST + ",2) as cost, "
                    + "round(ifnull(de.all_price,0) - (" + COST + "),2) as profit "
                    + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id "
                    + "and ifnull(mcs.delete_flag,'0') <> '1' "
                    + "where de.header_id = ? and ifnull(de.delete_flag,'0') <> '1' and ifnull(dh.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and de.tenant_id = ? ";
                a.add(tenantId);
            }
            List<Map<String, Object>> rows = jdbc().queryForList(sql + " order by de.id", a.toArray());
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("rows", rows);
            result.put("summary", sum);
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    private String where(String beginTime, String endTime, String billNumber, String barCode,
                         String materialName, String standard, String subType, Long tenantId, List<Object> a) {
        StringBuilder w = new StringBuilder(" where dh.type = '出库' and dh.sub_type in ('销售','零售') ")
                .append(" and dh.status in ('1','2','3') ")
                .append(" and ifnull(dh.delete_flag,'0') <> '1' and ifnull(de.delete_flag,'0') <> '1' ")
                .append(" and ifnull(m.delete_flag,'0') <> '1' ");
        if (!PluginUtils.isEmpty(subType)) {
            w.append(" and dh.sub_type = ? ");
            a.add(subType.trim());
        }
        if (!PluginUtils.isEmpty(billNumber)) {
            w.append(" and dh.number like ? ");
            a.add(PluginUtils.like(billNumber));
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
        if (!PluginUtils.isEmpty(beginTime)) {
            w.append(" and dh.oper_time >= ? ");
            a.add(beginTime.trim() + " 00:00:00");
        }
        if (!PluginUtils.isEmpty(endTime)) {
            w.append(" and dh.oper_time <= ? ");
            a.add(endTime.trim() + " 23:59:59");
        }
        String tenant = tenantClause(tenantId, "de");
        if (tenant != null) {
            w.append(tenant);
        }
        return w.toString();
    }

    private Map<String, Object> summary(String beginTime, String endTime, String billNumber, String barCode,
                                        String materialName, String standard, String subType, Long tenantId, boolean billLevel) {
        List<Object> a = new ArrayList<Object>();
        String w = where(beginTime, endTime, billNumber, barCode, materialName, standard, subType, tenantId, a);
        String sql = "select round(ifnull(sum(ifnull(de.all_price,0)),0),2) as amount, "
                + "round(ifnull(sum(" + COST + "),0),2) as cost, "
                + "round(ifnull(sum(ifnull(de.all_price,0)),0) - ifnull(sum(" + COST + "),0),2) as profit, "
                + "count(distinct dh.id) as billCount, count(de.id) as itemCount, round(ifnull(sum(de.basic_number),0),2) as qty "
                + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                + "join jsh_material m on m.id = de.material_id "
                + "left join jsh_material_extend me on me.id = de.material_extend_id "
                + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id "
                + "and ifnull(mcs.delete_flag,'0') <> '1' " + w;
        List<Map<String, Object>> rows = jdbc().queryForList(sql, a.toArray());
        Map<String, Object> stat = rows.isEmpty() ? new HashMap<String, Object>() : new HashMap<String, Object>(rows.get(0));
        BigDecimal amount = PluginUtils.decimal(stat.get("amount"));
        BigDecimal profit = PluginUtils.decimal(stat.get("profit"));
        stat.put("profitRate", amount.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : profit.multiply(new BigDecimal("100")).divide(amount, 2, RoundingMode.HALF_UP));
        if (!billLevel) {
            stat.remove("billCount");
        }
        return stat;
    }

    private Map<String, Object> summaryByBill(Long billId, Long tenantId) {
        List<Object> a = new ArrayList<Object>();
        a.add(billId);
        String sql = "select round(ifnull(sum(ifnull(de.all_price,0)),0),2) as amount, "
                + "round(ifnull(sum(" + COST + "),0),2) as cost, "
                + "round(ifnull(sum(ifnull(de.all_price,0)),0) - ifnull(sum(" + COST + "),0),2) as profit "
                + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                + "left join jsh_material_extend me on me.id = de.material_extend_id "
                + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id "
                + "and ifnull(mcs.delete_flag,'0') <> '1' "
                + "where de.header_id = ? and ifnull(de.delete_flag,'0') <> '1' ";
        if (tenantId != null && tenantId != 0L) {
            sql += " and de.tenant_id = ? ";
            a.add(tenantId);
        }
        List<Map<String, Object>> rows = jdbc().queryForList(sql, a.toArray());
        return rows.isEmpty() ? new HashMap<String, Object>() : new HashMap<String, Object>(rows.get(0));
    }
}

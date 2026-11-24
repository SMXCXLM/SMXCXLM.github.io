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
 * 利润类插件：利润报表、销售占比、销售排行、销售利润。
 */
@RestController
@RequestMapping("/profitStat")
@Api(tags = {"利润统计插件"})
public class ProfitStatController extends BasePluginController {

    /** 利润报表：按单据明细统计收入、成本、利润 */
    @GetMapping("/profitReport")
    @ApiOperation(value = "利润报表")
    public BaseResponseInfo profitReport(@RequestParam(value = "beginTime", required = false) String beginTime,
                                         @RequestParam(value = "endTime", required = false) String endTime,
                                         @RequestParam(value = "materialParam", required = false) String materialParam,
                                         @RequestParam(value = "depotId", required = false) Long depotId,
                                         @RequestParam(value = "subType", required = false) String subType,
                                         @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                         @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                         HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select dh.id as billId, dh.number as billNumber, dh.sub_type as subType, "
                    + "date_format(dh.oper_time,'%Y-%m-%d %H:%i:%s') as operTime, de.id as itemId, "
                    + "m.name as materialName, m.standard as standard, m.model as model, me.bar_code as barCode, "
                    + "de.material_unit as unit, de.basic_number as qty, "
                    + "ifnull(de.all_price,0) as amount, "
                    + "round(de.basic_number * ifnull(nullif(de.purchase_unit_price,0), ifnull(mcs.current_unit_price,0)), 2) as cost, "
                    + "round(ifnull(de.all_price,0) - de.basic_number * ifnull(nullif(de.purchase_unit_price,0), ifnull(mcs.current_unit_price,0)), 2) as profit, "
                    + "round(case when ifnull(de.all_price,0) = 0 then 0 else 100 * (ifnull(de.all_price,0) - de.basic_number * ifnull(nullif(de.purchase_unit_price,0), ifnull(mcs.current_unit_price,0))) / de.all_price end, 2) as profitRate "
                    + "from jsh_depot_item de "
                    + "join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id and ifnull(mcs.delete_flag,'0') <> '1' "
                    + profitWhere(beginTime, endTime, materialParam, depotId, subType, tenantId, a);
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by dh.oper_time desc, dh.id desc", a, currentPage, pageSize);
            Map<String, Object> stat = sumProfit(beginTime, endTime, materialParam, depotId, subType, tenantId);
            Map<String, Object> result = new HashMap<String, Object>(data);
            result.put("summary", stat);
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** 销售利润：按销售单据汇总单利润 */
    @GetMapping("/saleProfit")
    @ApiOperation(value = "销售利润")
    public BaseResponseInfo saleProfit(@RequestParam(value = "beginTime", required = false) String beginTime,
                                       @RequestParam(value = "endTime", required = false) String endTime,
                                       @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                       @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                       HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select dh.id as billId, dh.number as billNumber, dh.sub_type as subType, "
                    + "date_format(dh.oper_time,'%Y-%m-%d %H:%i:%s') as operTime, "
                    + "count(de.id) as itemCount, round(sum(ifnull(de.all_price,0)),2) as amount, "
                    + "round(sum(de.basic_number * ifnull(nullif(de.purchase_unit_price,0), ifnull(mcs.current_unit_price,0))),2) as cost, "
                    + "round(sum(ifnull(de.all_price,0)) - sum(de.basic_number * ifnull(nullif(de.purchase_unit_price,0), ifnull(mcs.current_unit_price,0))),2) as profit "
                    + "from jsh_depot_item de "
                    + "join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id and ifnull(mcs.delete_flag,'0') <> '1' "
                    + profitWhere(beginTime, endTime, null, null, null, tenantId, a)
                    + " group by dh.id, dh.number, dh.sub_type, dh.oper_time ";
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by dh.oper_time desc", a, currentPage, pageSize);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** 销售利润明细：某张销售单据内每条商品明细的利润 */
    @GetMapping("/saleProfitDetail")
    @ApiOperation(value = "销售利润明细")
    public BaseResponseInfo saleProfitDetail(@RequestParam("billId") Long billId,
                                             HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select de.id as itemId, m.name as materialName, m.standard as standard, m.model as model, "
                    + "me.bar_code as barCode, de.material_unit as unit, de.basic_number as qty, "
                    + "ifnull(de.all_price,0) as amount, "
                    + "round(de.basic_number * ifnull(nullif(de.purchase_unit_price,0), ifnull(mcs.current_unit_price,0)),2) as cost, "
                    + "round(ifnull(de.all_price,0) - de.basic_number * ifnull(nullif(de.purchase_unit_price,0), ifnull(mcs.current_unit_price,0)),2) as profit "
                    + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id "
                    + "and ifnull(mcs.delete_flag,'0') <> '1' "
                    + "where de.header_id = ? and ifnull(de.delete_flag,'0') <> '1' and ifnull(dh.delete_flag,'0') <> '1' ";
            a.add(billId);
            if (tenantId != null && tenantId != 0L) {
                sql += " and de.tenant_id = ? ";
                a.add(tenantId);
            }
            List<Map<String, Object>> rows = jdbc().queryForList(sql + " order by de.id", a.toArray());
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal cost = BigDecimal.ZERO;
            BigDecimal profit = BigDecimal.ZERO;
            for (Map<String, Object> row : rows) {
                amount = amount.add(PluginUtils.decimal(row.get("amount")));
                cost = cost.add(PluginUtils.decimal(row.get("cost")));
                profit = profit.add(PluginUtils.decimal(row.get("profit")));
            }
            Map<String, Object> summary = new HashMap<String, Object>();
            summary.put("amount", amount);
            summary.put("cost", cost);
            summary.put("profit", profit);
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("rows", rows);
            result.put("total", rows.size());
            result.put("summary", summary);
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** 销售占比：商品销量占比（零售 + 销售） */
    @GetMapping("/saleShare")
    @ApiOperation(value = "销售占比")
    public BaseResponseInfo saleShare(@RequestParam(value = "beginTime", required = false) String beginTime,
                                      @RequestParam(value = "endTime", required = false) String endTime,
                                      @RequestParam(value = "materialParam", required = false) String materialParam,
                                      @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                      @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
                                      HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select de.material_id as materialId, m.name as materialName, m.standard as standard, "
                    + "m.model as model, me.bar_code as barCode, round(sum(de.basic_number),2) as qty, "
                    + "round(sum(ifnull(de.all_price,0)),2) as amount "
                    + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + profitWhere(beginTime, endTime, materialParam, null, null, tenantId, a)
                    + " group by de.material_id, m.name, m.standard, m.model, me.bar_code ";
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by qty desc", a, currentPage, pageSize);
            return PluginUtils.ok(withShare(data));
        } catch (Exception e) {
            return error(e);
        }
    }

    /** 销售排行：商品销量排行（零售 + 销售） */
    @GetMapping("/saleRank")
    @ApiOperation(value = "销售排行")
    public BaseResponseInfo saleRank(@RequestParam(value = "beginTime", required = false) String beginTime,
                                     @RequestParam(value = "endTime", required = false) String endTime,
                                     @RequestParam(value = "materialParam", required = false) String materialParam,
                                     @RequestParam(value = "top", defaultValue = "20") Integer top,
                                     @RequestParam(value = "rankBy", defaultValue = "qty") String rankBy,
                                     HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select de.material_id as materialId, m.name as materialName, m.standard as standard, "
                    + "m.model as model, me.bar_code as barCode, round(sum(de.basic_number),2) as qty, "
                    + "round(sum(ifnull(de.all_price,0)),2) as amount "
                    + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + profitWhere(beginTime, endTime, materialParam, null, null, tenantId, a)
                    + " group by de.material_id, m.name, m.standard, m.model, me.bar_code ";
            String orderBy = "amount".equalsIgnoreCase(rankBy) ? " order by amount desc" : " order by qty desc";
            int limit = top == null || top <= 0 ? 20 : Math.min(top, 200);
            List<Map<String, Object>> rows = jdbc().queryForList(base + orderBy + " limit " + limit, a.toArray());
            int rank = 1;
            for (Map<String, Object> row : rows) {
                row.put("rank", rank++);
            }
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("rows", rows);
            result.put("total", rows.size());
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    private String profitWhere(String beginTime, String endTime, String materialParam, Long depotId,
                               String subType, Long tenantId, List<Object> a) {
        StringBuilder where = new StringBuilder(" where dh.type = '出库' and dh.sub_type in ('销售','零售') ")
                .append(" and dh.status in ('1','2','3') ")
                .append(" and ifnull(dh.delete_flag,'0') <> '1' and ifnull(de.delete_flag,'0') <> '1' ")
                .append(" and ifnull(m.delete_flag,'0') <> '1' ");
        if (!PluginUtils.isEmpty(subType)) {
            where.append(" and dh.sub_type = ? ");
            a.add(subType.trim());
        }
        if (!PluginUtils.isEmpty(beginTime)) {
            where.append(" and dh.oper_time >= ? ");
            a.add(beginTime.trim() + " 00:00:00");
        }
        if (!PluginUtils.isEmpty(endTime)) {
            where.append(" and dh.oper_time <= ? ");
            a.add(endTime.trim() + " 23:59:59");
        }
        if (depotId != null) {
            where.append(" and de.depot_id = ? ");
            a.add(depotId);
        }
        if (!PluginUtils.isEmpty(materialParam)) {
            where.append(" and (m.name like ? or m.mnemonic like ? or me.bar_code like ? or m.standard like ? or m.model like ?) ");
            String like = PluginUtils.like(materialParam);
            for (int i = 0; i < 5; i++) {
                a.add(like);
            }
        }
        String tenantClause = tenantClause(tenantId, "de");
        if (tenantClause != null) {
            where.append(tenantClause);
        }
        return where.toString();
    }

    private Map<String, Object> sumProfit(String beginTime, String endTime, String materialParam,
                                          Long depotId, String subType, Long tenantId) {
        List<Object> a = new ArrayList<Object>();
        String where = profitWhere(beginTime, endTime, materialParam, depotId, subType, tenantId, a);
        String sql = "select round(ifnull(sum(ifnull(de.all_price,0)),0),2) as amount, "
                + "round(ifnull(sum(de.basic_number * ifnull(nullif(de.purchase_unit_price,0), ifnull(mcs.current_unit_price,0))),0),2) as cost, "
                + "round(ifnull(sum(ifnull(de.all_price,0)),0) - ifnull(sum(de.basic_number * ifnull(nullif(de.purchase_unit_price,0), ifnull(mcs.current_unit_price,0))),0),2) as profit "
                + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                + "join jsh_material m on m.id = de.material_id "
                + "left join jsh_material_extend me on me.id = de.material_extend_id "
                + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id and ifnull(mcs.delete_flag,'0') <> '1' "
                + where;
        List<Map<String, Object>> list = jdbc().queryForList(sql, a.toArray());
        Map<String, Object> stat = list.isEmpty() ? new HashMap<String, Object>() : list.get(0);
        BigDecimal amount = PluginUtils.decimal(stat.get("amount"));
        BigDecimal profit = PluginUtils.decimal(stat.get("profit"));
        stat.put("profitRate", amount.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : profit.multiply(new BigDecimal("100")).divide(amount, 2, RoundingMode.HALF_UP));
        return stat;
    }

    /** 计算每行销量占总销量的比例 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> withShare(Map<String, Object> data) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            total = total.add(PluginUtils.decimal(row.get("qty")));
        }
        for (Map<String, Object> row : rows) {
            BigDecimal qty = PluginUtils.decimal(row.get("qty"));
            row.put("share", total.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : qty.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP));
        }
        data.put("totalQty", total);
        return data;
    }
}

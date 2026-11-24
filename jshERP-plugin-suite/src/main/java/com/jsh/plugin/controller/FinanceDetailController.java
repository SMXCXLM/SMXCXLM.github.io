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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收支项明细 / 收付款明细：直接查询财务单据主表与明细表，提供合计、打印、导出数据。
 */
@RestController
@RequestMapping("/finance")
@Api(tags = {"收支与收付款明细模板"})
public class FinanceDetailController extends BasePluginController {

    @GetMapping("/income")
    @ApiOperation(value = "收支项明细")
    public BaseResponseInfo income(@RequestParam(value = "type", required = false) String type,
                                   @RequestParam(value = "billNo", required = false) String billNo,
                                   @RequestParam(value = "itemName", required = false) String itemName,
                                   @RequestParam(value = "beginTime", required = false) String beginTime,
                                   @RequestParam(value = "endTime", required = false) String endTime,
                                   @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                   @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                   HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select ah.id as billId, ah.bill_no as billNo, ah.type as billType, "
                    + "date_format(ah.bill_time,'%Y-%m-%d %H:%i:%s') as billTime, s.supplier as organName, "
                    + "ai.id as itemId, io.name as itemName, ai.each_amount as amount, ai.need_debt as needDebt, "
                    + "ai.finish_debt as finishDebt, ah.total_price as totalPrice, ah.change_amount as changeAmount, "
                    + "a.name as accountName, ai.remark as itemRemark, ah.remark as billRemark "
                    + "from jsh_account_head ah join jsh_account_item ai on ai.header_id = ah.id "
                    + "left join jsh_in_out_item io on io.id = ai.in_out_item_id "
                    + "left join jsh_supplier s on s.id = ah.organ_id "
                    + "left join jsh_account a on a.id = ifnull(ai.account_id, ah.account_id) "
                    + "where ah.type in ('收入','支出') and ifnull(ah.delete_flag,'0') <> '1' "
                    + "and ifnull(ai.delete_flag,'0') <> '1' ";
            if (!PluginUtils.isEmpty(type) && !"all".equalsIgnoreCase(type)) {
                base += " and ah.type = ? ";
                a.add(type.trim());
            }
            if (!PluginUtils.isEmpty(billNo)) {
                base += " and ah.bill_no like ? ";
                a.add(PluginUtils.like(billNo));
            }
            if (!PluginUtils.isEmpty(itemName)) {
                base += " and io.name like ? ";
                a.add(PluginUtils.like(itemName));
            }
            if (!PluginUtils.isEmpty(beginTime)) {
                base += " and ah.bill_time >= ? ";
                a.add(beginTime.trim() + " 00:00:00");
            }
            if (!PluginUtils.isEmpty(endTime)) {
                base += " and ah.bill_time <= ? ";
                a.add(endTime.trim() + " 23:59:59");
            }
            String tenant = tenantClause(tenantId, "ah");
            if (tenant != null) {
                base += tenant;
            }
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by ah.bill_time desc, ai.id", a, currentPage, pageSize);
            Map<String, Object> result = new HashMap<String, Object>(data);
            result.put("summary", incomeSummary(type, billNo, itemName, beginTime, endTime, tenantId));
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/payment")
    @ApiOperation(value = "收付款明细")
    public BaseResponseInfo payment(@RequestParam(value = "type", required = false) String type,
                                    @RequestParam(value = "billNo", required = false) String billNo,
                                    @RequestParam(value = "sourceBillNumber", required = false) String sourceBillNumber,
                                    @RequestParam(value = "beginTime", required = false) String beginTime,
                                    @RequestParam(value = "endTime", required = false) String endTime,
                                    @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                    @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                    HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select ah.id as billId, ah.bill_no as billNo, ah.type as billType, "
                    + "date_format(ah.bill_time,'%Y-%m-%d %H:%i:%s') as billTime, s.supplier as organName, "
                    + "ai.id as itemId, ai.each_amount as amount, ai.need_debt as needDebt, ai.finish_debt as finishDebt, "
                    + "a.name as accountName, ah.total_price as totalPrice, ah.change_amount as changeAmount, "
                    + "dh.id as sourceBillId, dh.number as sourceBillNumber, dh.sub_type as sourceSubType, "
                    + "date_format(dh.oper_time,'%Y-%m-%d %H:%i:%s') as sourceTime, dh.total_price as sourceAmount, "
                    + "ai.bill_id as sourceRef, ai.remark as itemRemark, ah.remark as billRemark "
                    + "from jsh_account_head ah join jsh_account_item ai on ai.header_id = ah.id "
                    + "left join jsh_depot_head dh on dh.id = ai.bill_id "
                    + "left join jsh_supplier s on s.id = ah.organ_id "
                    + "left join jsh_account a on a.id = ifnull(ai.account_id, ah.account_id) "
                    + "where ah.type in ('收款','付款') and ifnull(ah.delete_flag,'0') <> '1' "
                    + "and ifnull(ai.delete_flag,'0') <> '1' "
                    + "and ifnull(dh.delete_flag,'0') <> '1' ";
            if (!PluginUtils.isEmpty(type) && !"all".equalsIgnoreCase(type)) {
                base += " and ah.type = ? ";
                a.add(type.trim());
            }
            if (!PluginUtils.isEmpty(billNo)) {
                base += " and ah.bill_no like ? ";
                a.add(PluginUtils.like(billNo));
            }
            if (!PluginUtils.isEmpty(sourceBillNumber)) {
                base += " and dh.number like ? ";
                a.add(PluginUtils.like(sourceBillNumber));
            }
            if (!PluginUtils.isEmpty(beginTime)) {
                base += " and ah.bill_time >= ? ";
                a.add(beginTime.trim() + " 00:00:00");
            }
            if (!PluginUtils.isEmpty(endTime)) {
                base += " and ah.bill_time <= ? ";
                a.add(endTime.trim() + " 23:59:59");
            }
            String tenant = tenantClause(tenantId, "ah");
            if (tenant != null) {
                base += tenant;
            }
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by ah.bill_time desc, ai.id", a, currentPage, pageSize);
            Map<String, Object> result = new HashMap<String, Object>(data);
            result.put("summary", paymentSummary(type, billNo, sourceBillNumber, beginTime, endTime, tenantId));
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/items")
    @ApiOperation(value = "收支项目下拉")
    public BaseResponseInfo items(HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select id as value, name as label, type from jsh_in_out_item where ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by type, name", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    private Map<String, Object> incomeSummary(String type, String billNo, String itemName,
                                              String beginTime, String endTime, Long tenantId) {
        List<Object> a = args();
        String sql = "select round(ifnull(sum(case when ah.type = '收入' then ifnull(ai.each_amount,0) else 0 end),0),2) as incomeAmount, "
                + "round(ifnull(sum(case when ah.type = '支出' then ifnull(ai.each_amount,0) else 0 end),0),2) as expenseAmount, "
                + "round(ifnull(sum(ifnull(ai.each_amount,0)),0),2) as totalAmount, count(distinct ah.id) as billCount, count(ai.id) as itemCount "
                + "from jsh_account_head ah join jsh_account_item ai on ai.header_id = ah.id "
                + "left join jsh_in_out_item io on io.id = ai.in_out_item_id "
                + "where ah.type in ('收入','支出') and ifnull(ah.delete_flag,'0') <> '1' and ifnull(ai.delete_flag,'0') <> '1' ";
        if (!PluginUtils.isEmpty(type) && !"all".equalsIgnoreCase(type)) {
            sql += " and ah.type = ? ";
            a.add(type.trim());
        }
        if (!PluginUtils.isEmpty(billNo)) {
            sql += " and ah.bill_no like ? ";
            a.add(PluginUtils.like(billNo));
        }
        if (!PluginUtils.isEmpty(itemName)) {
            sql += " and io.name like ? ";
            a.add(PluginUtils.like(itemName));
        }
        if (!PluginUtils.isEmpty(beginTime)) {
            sql += " and ah.bill_time >= ? ";
            a.add(beginTime.trim() + " 00:00:00");
        }
        if (!PluginUtils.isEmpty(endTime)) {
            sql += " and ah.bill_time <= ? ";
            a.add(endTime.trim() + " 23:59:59");
        }
        String tenant = tenantClause(tenantId, "ah");
        if (tenant != null) {
            sql += tenant;
        }
        List<Map<String, Object>> rows = jdbc().queryForList(sql, a.toArray());
        Map<String, Object> stat = rows.isEmpty() ? new HashMap<String, Object>() : new HashMap<String, Object>(rows.get(0));
        BigDecimal income = PluginUtils.decimal(stat.get("incomeAmount"));
        BigDecimal expense = PluginUtils.decimal(stat.get("expenseAmount"));
        stat.put("balance", income.subtract(expense));
        return stat;
    }

    private Map<String, Object> paymentSummary(String type, String billNo, String sourceBillNumber,
                                               String beginTime, String endTime, Long tenantId) {
        List<Object> a = args();
        String sql = "select round(ifnull(sum(case when ah.type = '收款' then ifnull(ai.each_amount,0) else 0 end),0),2) as receiptAmount, "
                + "round(ifnull(sum(case when ah.type = '付款' then ifnull(ai.each_amount,0) else 0 end),0),2) as paymentAmount, "
                + "round(ifnull(sum(ifnull(ai.each_amount,0)),0),2) as totalAmount, count(distinct ah.id) as billCount, count(ai.id) as itemCount "
                + "from jsh_account_head ah join jsh_account_item ai on ai.header_id = ah.id "
                + "left join jsh_depot_head dh on dh.id = ai.bill_id "
                + "where ah.type in ('收款','付款') and ifnull(ah.delete_flag,'0') <> '1' and ifnull(ai.delete_flag,'0') <> '1' ";
        if (!PluginUtils.isEmpty(type) && !"all".equalsIgnoreCase(type)) {
            sql += " and ah.type = ? ";
            a.add(type.trim());
        }
        if (!PluginUtils.isEmpty(billNo)) {
            sql += " and ah.bill_no like ? ";
            a.add(PluginUtils.like(billNo));
        }
        if (!PluginUtils.isEmpty(sourceBillNumber)) {
            sql += " and dh.number like ? ";
            a.add(PluginUtils.like(sourceBillNumber));
        }
        if (!PluginUtils.isEmpty(beginTime)) {
            sql += " and ah.bill_time >= ? ";
            a.add(beginTime.trim() + " 00:00:00");
        }
        if (!PluginUtils.isEmpty(endTime)) {
            sql += " and ah.bill_time <= ? ";
            a.add(endTime.trim() + " 23:59:59");
        }
        String tenant = tenantClause(tenantId, "ah");
        if (tenant != null) {
            sql += tenant;
        }
        List<Map<String, Object>> rows = jdbc().queryForList(sql, a.toArray());
        Map<String, Object> stat = rows.isEmpty() ? new HashMap<String, Object>() : new HashMap<String, Object>(rows.get(0));
        BigDecimal receipt = PluginUtils.decimal(stat.get("receiptAmount"));
        BigDecimal payment = PluginUtils.decimal(stat.get("paymentAmount"));
        stat.put("balance", receipt.subtract(payment));
        return stat;
    }
}

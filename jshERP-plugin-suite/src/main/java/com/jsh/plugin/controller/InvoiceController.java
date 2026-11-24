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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 发票管理：采购/销售发票跟进、开票登记、开票统计。
 */
@RestController
@RequestMapping("/invoice")
@Api(tags = {"发票管理插件"})
public class InvoiceController extends BasePluginController {

    private static final String INVOICED_SUB = "select bill_id, "
            + "round(ifnull(sum(case when status = '已开票' then ifnull(amount,0) else 0 end),0),2) as invoiced, "
            + "sum(case when status = '已开票' then 1 else 0 end) as invoiceCount "
            + "from jsh_invoice where ifnull(delete_flag,'0') <> '1' group by bill_id ";

    @GetMapping("/follow")
    @ApiOperation(value = "发票跟进（采购/销售）")
    public BaseResponseInfo follow(@RequestParam(value = "invoiceType", defaultValue = "purchase") String invoiceType,
                                   @RequestParam(value = "keyword", required = false) String keyword,
                                   @RequestParam(value = "status", required = false) String status,
                                   @RequestParam(value = "beginTime", required = false) String beginTime,
                                   @RequestParam(value = "endTime", required = false) String endTime,
                                   @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                   @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                   HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String inner = "select dh.id as billId, dh.number as billNumber, dh.type as billType, dh.sub_type as subType, "
                    + "date_format(dh.oper_time,'%Y-%m-%d %H:%i:%s') as operTime, s.supplier as organName, "
                    + "round(ifnull(dh.total_price,0),2) as billAmount, ifnull(inv.invoiced,0) as invoicedAmount, "
                    + "round(ifnull(dh.total_price,0) - ifnull(inv.invoiced,0),2) as uninvoicedAmount, "
                    + "ifnull(inv.invoiceCount,0) as invoiceCount, "
                    + "case when ifnull(inv.invoiced,0) = 0 then '未开票' "
                    + "when ifnull(inv.invoiced,0) >= ifnull(dh.total_price,0) then '已开票' else '部分开票' end as invoiceStatus "
                    + "from jsh_depot_head dh left join (" + INVOICED_SUB + ") inv on inv.bill_id = dh.id "
                    + "left join jsh_supplier s on s.id = dh.organ_id "
                    + billCondition(invoiceType)
                    + " and ifnull(dh.delete_flag,'0') <> '1' ";
            if (!PluginUtils.isEmpty(keyword)) {
                inner += " and (dh.number like ? or s.supplier like ?) ";
                a.add(PluginUtils.like(keyword));
                a.add(PluginUtils.like(keyword));
            }
            if (!PluginUtils.isEmpty(beginTime)) {
                inner += " and dh.oper_time >= ? ";
                a.add(beginTime.trim() + " 00:00:00");
            }
            if (!PluginUtils.isEmpty(endTime)) {
                inner += " and dh.oper_time <= ? ";
                a.add(endTime.trim() + " 23:59:59");
            }
            String tenant = tenantClause(tenantId, "dh");
            if (tenant != null) {
                inner += tenant;
            }
            String base = "select * from (" + inner + ") t where 1=1 ";
            if (!PluginUtils.isEmpty(status) && !"all".equalsIgnoreCase(status)) {
                if ("未开票".equals(status)) {
                    base += " and t.invoicedAmount = 0 ";
                } else if ("已开票".equals(status)) {
                    base += " and t.invoicedAmount > 0 and t.uninvoicedAmount <= 0 ";
                } else if ("部分开票".equals(status)) {
                    base += " and t.invoicedAmount > 0 and t.uninvoicedAmount > 0 ";
                } else {
                    base += " and t.invoiceStatus = ? ";
                    a.add(status.trim());
                }
            }
            String countSql = "select count(*) from (" + base + ") t2";
            Map<String, Object> data = page(countSql, base + " order by t.operTime desc, t.billId desc", a, currentPage, pageSize);
            Map<String, Object> result = new HashMap<String, Object>(data);
            result.put("summary", followSummary(invoiceType, keyword, status, beginTime, endTime, tenantId));
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/records")
    @ApiOperation(value = "开票登记记录")
    public BaseResponseInfo records(@RequestParam(value = "invoiceType", required = false) String invoiceType,
                                    @RequestParam(value = "status", required = false) String status,
                                    @RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "beginTime", required = false) String beginTime,
                                    @RequestParam(value = "endTime", required = false) String endTime,
                                    @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                    @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                    HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select inv.id as id, inv.invoice_type as invoiceType, inv.bill_id as billId, "
                    + "inv.bill_number as billNumber, inv.invoice_no as invoiceNo, "
                    + "date_format(inv.invoice_date,'%Y-%m-%d') as invoiceDate, inv.amount as amount, "
                    + "inv.tax_amount as taxAmount, inv.title as title, inv.tax_num as taxNum, "
                    + "inv.organ_name as organName, inv.status as status, inv.remark as remark, "
                    + "date_format(inv.create_time,'%Y-%m-%d %H:%i:%s') as createTime, "
                    + "dh.sub_type as subType, dh.type as billType, dh.total_price as billAmount "
                    + "from jsh_invoice inv left join jsh_depot_head dh on dh.id = inv.bill_id "
                    + "where ifnull(inv.delete_flag,'0') <> '1' ";
            if (!PluginUtils.isEmpty(invoiceType) && !"all".equalsIgnoreCase(invoiceType)) {
                base += " and inv.invoice_type = ? ";
                a.add(invoiceType.trim());
            }
            if (!PluginUtils.isEmpty(status) && !"all".equalsIgnoreCase(status)) {
                base += " and inv.status = ? ";
                a.add(status.trim());
            }
            if (!PluginUtils.isEmpty(keyword)) {
                base += " and (inv.invoice_no like ? or inv.bill_number like ? or inv.organ_name like ? or inv.title like ?) ";
                for (int i = 0; i < 4; i++) {
                    a.add(PluginUtils.like(keyword));
                }
            }
            if (!PluginUtils.isEmpty(beginTime)) {
                base += " and inv.invoice_date >= ? ";
                a.add(beginTime.trim());
            }
            if (!PluginUtils.isEmpty(endTime)) {
                base += " and inv.invoice_date <= ? ";
                a.add(endTime.trim());
            }
            String tenant = tenantClause(tenantId, "inv");
            if (tenant != null) {
                base += tenant;
            }
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by inv.invoice_date desc, inv.id desc", a, currentPage, pageSize);
            Map<String, Object> result = new HashMap<String, Object>(data);
            result.put("summary", recordsSummary(invoiceType, status, keyword, beginTime, endTime, tenantId));
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/register")
    @ApiOperation(value = "开票登记")
    public BaseResponseInfo register(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            String type = PluginUtils.trim(String.valueOf(body.get("invoiceType")));
            Long billId = CrudSupport.longVal(body.get("billId"));
            if (!"purchase".equals(type) && !"sales".equals(type)) {
                return PluginUtils.fail("发票类型必须为 purchase 或 sales");
            }
            if (billId == null) {
                return PluginUtils.fail("缺少业务单据");
            }
            List<Map<String, Object>> bills = jdbc.queryForList(
                    "select dh.*, s.supplier as organName from jsh_depot_head dh left join jsh_supplier s on s.id = dh.organ_id "
                            + "where dh.id = ? and ifnull(dh.delete_flag,'0') <> '1'", billId);
            if (bills.isEmpty()) {
                return PluginUtils.fail("业务单据不存在");
            }
            Map<String, Object> bill = bills.get(0);
            String invoiceNo = PluginUtils.trim(String.valueOf(body.get("invoiceNo")));
            if (invoiceNo.isEmpty()) {
                invoiceNo = "FP" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                        + String.format("%03d", new Random().nextInt(1000));
            }
            List<Object> dupArgs = args();
            dupArgs.add(invoiceNo);
            String dupSql = "select count(*) from jsh_invoice where invoice_no = ? and status = '已开票' "
                    + "and ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                dupSql += " and tenant_id = ? ";
                dupArgs.add(tenantId);
            }
            Long dup = jdbc.queryForObject(dupSql, dupArgs.toArray(), Long.class);
            if (dup != null && dup > 0) {
                return PluginUtils.fail("发票号码已存在：" + invoiceNo);
            }
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("invoice_type", type);
            data.put("bill_id", billId);
            data.put("bill_number", bill.get("number"));
            data.put("invoice_no", invoiceNo);
            data.put("invoice_date", body.get("invoiceDate") == null || PluginUtils.isEmpty(String.valueOf(body.get("invoiceDate")))
                    ? new SimpleDateFormat("yyyy-MM-dd").format(new Date()) : body.get("invoiceDate"));
            data.put("amount", body.get("amount") == null ? bill.get("total_price") : body.get("amount"));
            data.put("tax_amount", body.get("taxAmount") == null ? 0 : body.get("taxAmount"));
            data.put("title", body.get("title"));
            data.put("tax_num", body.get("taxNum"));
            data.put("organ_name", body.get("organName") == null ? bill.get("organName") : body.get("organName"));
            data.put("status", "已开票");
            data.put("remark", body.get("remark"));
            data.put("creator", PluginUtils.userId(request));
            data.put("create_time", new Date());
            CrudSupport.insert(jdbc, "jsh_invoice", data, tenantId);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/cancel")
    @ApiOperation(value = "作废发票")
    public BaseResponseInfo cancel(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            jdbc().update("update jsh_invoice set status = '已作废' where id = ?", id);
            return PluginUtils.ok("已作废");
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/delete")
    @ApiOperation(value = "删除发票登记")
    public BaseResponseInfo delete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_invoice", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/stats")
    @ApiOperation(value = "开票统计")
    public BaseResponseInfo stats(@RequestParam(value = "invoiceType", required = false) String invoiceType,
                                  @RequestParam(value = "groupBy", defaultValue = "organ") String groupBy,
                                  @RequestParam(value = "beginTime", required = false) String beginTime,
                                  @RequestParam(value = "endTime", required = false) String endTime,
                                  HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String groupColumn;
            String groupName;
            if ("month".equalsIgnoreCase(groupBy)) {
                groupColumn = "date_format(inv.invoice_date,'%Y-%m')";
                groupName = "period";
            } else if ("type".equalsIgnoreCase(groupBy)) {
                groupColumn = "inv.invoice_type";
                groupName = "invoiceType";
            } else {
                groupColumn = "inv.organ_name";
                groupName = "organName";
            }
            String base = "select " + groupColumn + " as " + groupName + ", "
                    + "count(inv.id) as invoiceCount, round(ifnull(sum(inv.amount),0),2) as amount, "
                    + "round(ifnull(sum(inv.tax_amount),0),2) as taxAmount "
                    + "from jsh_invoice inv where inv.status = '已开票' and ifnull(inv.delete_flag,'0') <> '1' ";
            if (!PluginUtils.isEmpty(invoiceType) && !"all".equalsIgnoreCase(invoiceType)) {
                base += " and inv.invoice_type = ? ";
                a.add(invoiceType.trim());
            }
            if (!PluginUtils.isEmpty(beginTime)) {
                base += " and inv.invoice_date >= ? ";
                a.add(beginTime.trim());
            }
            if (!PluginUtils.isEmpty(endTime)) {
                base += " and inv.invoice_date <= ? ";
                a.add(endTime.trim());
            }
            String tenant = tenantClause(tenantId, "inv");
            if (tenant != null) {
                base += tenant;
            }
            List<Map<String, Object>> rows = jdbc().queryForList(
                    base + " group by " + groupColumn + " order by amount desc", a.toArray());
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal tax = BigDecimal.ZERO;
            for (Map<String, Object> row : rows) {
                amount = amount.add(PluginUtils.decimal(row.get("amount")));
                tax = tax.add(PluginUtils.decimal(row.get("taxAmount")));
            }
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("rows", rows);
            result.put("total", rows.size());
            Map<String, Object> summary = new HashMap<String, Object>();
            summary.put("amount", amount);
            summary.put("taxAmount", tax);
            summary.put("invoiceCount", rows.size());
            result.put("summary", summary);
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    private String billCondition(String invoiceType) {
        if ("sales".equalsIgnoreCase(invoiceType)) {
            return " where dh.type = '出库' and dh.sub_type in ('销售','零售') and dh.status in ('1','2','3') ";
        }
        return " where dh.type = '入库' and dh.sub_type = '采购' and dh.status in ('1','2','3') ";
    }

    private Map<String, Object> followSummary(String invoiceType, String keyword, String status,
                                              String beginTime, String endTime, Long tenantId) {
        List<Object> a = args();
        String inner = "select dh.id as billId, round(ifnull(dh.total_price,0),2) as billAmount, "
                + "ifnull(inv.invoiced,0) as invoicedAmount, "
                + "round(ifnull(dh.total_price,0) - ifnull(inv.invoiced,0),2) as uninvoicedAmount "
                + "from jsh_depot_head dh left join (" + INVOICED_SUB + ") inv on inv.bill_id = dh.id "
                + "left join jsh_supplier s on s.id = dh.organ_id "
                + billCondition(invoiceType) + " and ifnull(dh.delete_flag,'0') <> '1' ";
        if (!PluginUtils.isEmpty(keyword)) {
            inner += " and (dh.number like ? or s.supplier like ?) ";
            a.add(PluginUtils.like(keyword));
            a.add(PluginUtils.like(keyword));
        }
        if (!PluginUtils.isEmpty(beginTime)) {
            inner += " and dh.oper_time >= ? ";
            a.add(beginTime.trim() + " 00:00:00");
        }
        if (!PluginUtils.isEmpty(endTime)) {
            inner += " and dh.oper_time <= ? ";
            a.add(endTime.trim() + " 23:59:59");
        }
        String tenant = tenantClause(tenantId, "dh");
        if (tenant != null) {
            inner += tenant;
        }
        String base = "select count(*) as billCount, round(ifnull(sum(t.billAmount),0),2) as billAmount, "
                + "round(ifnull(sum(t.invoicedAmount),0),2) as invoicedAmount, "
                + "round(ifnull(sum(case when t.uninvoicedAmount > 0 then t.uninvoicedAmount else 0 end),0),2) as uninvoicedAmount, "
                + "sum(case when t.invoicedAmount = 0 then 1 else 0 end) as uninvociedCount "
                + "from (" + inner + ") t where 1=1 ";
        if (!PluginUtils.isEmpty(status) && !"all".equalsIgnoreCase(status)) {
            if ("未开票".equals(status)) {
                base += " and t.invoicedAmount = 0 ";
            } else if ("已开票".equals(status)) {
                base += " and t.invoicedAmount > 0 and t.uninvoicedAmount <= 0 ";
            } else if ("部分开票".equals(status)) {
                base += " and t.invoicedAmount > 0 and t.uninvoicedAmount > 0 ";
            }
        }
        List<Map<String, Object>> rows = jdbc().queryForList(base, a.toArray());
        return rows.isEmpty() ? new HashMap<String, Object>() : new HashMap<String, Object>(rows.get(0));
    }

    private Map<String, Object> recordsSummary(String invoiceType, String status, String keyword,
                                               String beginTime, String endTime, Long tenantId) {
        List<Object> a = args();
        String sql = "select count(inv.id) as invoiceCount, round(ifnull(sum(inv.amount),0),2) as amount, "
                + "round(ifnull(sum(inv.tax_amount),0),2) as taxAmount "
                + "from jsh_invoice inv where ifnull(inv.delete_flag,'0') <> '1' ";
        if (!PluginUtils.isEmpty(invoiceType) && !"all".equalsIgnoreCase(invoiceType)) {
            sql += " and inv.invoice_type = ? ";
            a.add(invoiceType.trim());
        }
        if (!PluginUtils.isEmpty(status) && !"all".equalsIgnoreCase(status)) {
            sql += " and inv.status = ? ";
            a.add(status.trim());
        }
        if (!PluginUtils.isEmpty(keyword)) {
            sql += " and (inv.invoice_no like ? or inv.bill_number like ? or inv.organ_name like ? or inv.title like ?) ";
            for (int i = 0; i < 4; i++) {
                a.add(PluginUtils.like(keyword));
            }
        }
        if (!PluginUtils.isEmpty(beginTime)) {
            sql += " and inv.invoice_date >= ? ";
            a.add(beginTime.trim());
        }
        if (!PluginUtils.isEmpty(endTime)) {
            sql += " and inv.invoice_date <= ? ";
            a.add(endTime.trim());
        }
        String tenant = tenantClause(tenantId, "inv");
        if (tenant != null) {
            sql += tenant;
        }
        List<Map<String, Object>> rows = jdbc().queryForList(sql, a.toArray());
        return rows.isEmpty() ? new HashMap<String, Object>() : new HashMap<String, Object>(rows.get(0));
    }
}

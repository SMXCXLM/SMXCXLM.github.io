package com.jsh.plugin.controller;

import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.plugin.support.PluginUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据导出插件：按单据类型 + 日期区间导出 Excel（列表或明细）。
 */
@RestController
@RequestMapping("/billExport")
@Api(tags = {"单据导出插件"})
public class BillExportController extends BasePluginController {

    /** 支持导出的单据类型（与核心 sub_type 一致） */
    private static final String[][] BILL_TYPES = new String[][]{
            {"采购入库", "采购入库单"}, {"采购退货", "采购退货单"},
            {"销售出库", "销售出库单"}, {"销售退货", "销售退货单"},
            {"零售出库", "零售出库单"}, {"零售退货", "零售退货单"},
            {"其它入库", "其它入库单"}, {"其它出库", "其它出库单"},
            {"调拨出库", "调拨出库单"}, {"调拨入库", "调拨入库单"},
            {"收入单", "收入单"}, {"支出单", "支出单"},
            {"收款单", "收款单"}, {"付款单", "付款单"}, {"转账单", "转账单"},
            {"组装单", "组装单"}, {"拆卸单", "拆卸单"},
            {"盘点录入", "盘点录入单"}, {"盘点复盘", "盘点复盘单"},
            {"采购订单", "采购订单"}, {"销售订单", "销售订单"}
    };

    @GetMapping("/types")
    @ApiOperation(value = "可导出的单据类型")
    public BaseResponseInfo types() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (String[] item : BILL_TYPES) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("value", item[0]);
            map.put("label", item[1]);
            list.add(map);
        }
        return PluginUtils.ok(list);
    }

    /** 单据列表（表头维度） */
    @GetMapping("/list")
    @ApiOperation(value = "单据列表")
    public BaseResponseInfo list(@RequestParam(value = "subType", required = false) String subType,
                                 @RequestParam(value = "beginTime", required = false) String beginTime,
                                 @RequestParam(value = "endTime", required = false) String endTime,
                                 @RequestParam(value = "number", required = false) String number,
                                 @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                 @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                 HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select dh.id as billId, dh.number as number, dh.type as type, dh.sub_type as subType, "
                    + "date_format(dh.oper_time,'%Y-%m-%d %H:%i:%s') as operTime, "
                    + "round(ifnull(dh.total_price,0),2) as totalPrice, dh.pay_type as payType, dh.status as status, "
                    + "dh.remark as remark, dh.sales_man as salesMan, "
                    + "(select count(1) from jsh_depot_item di where di.header_id = dh.id and ifnull(di.delete_flag,'0') <> '1') as itemCount "
                    + billWhere(subType, beginTime, endTime, number, tenantId, a);
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by dh.oper_time desc, dh.id desc", a, currentPage, pageSize);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** 单据明细 */
    @GetMapping("/detail")
    @ApiOperation(value = "单据明细")
    public BaseResponseInfo detail(@RequestParam(value = "billId") Long billId,
                                   @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                   @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
                                   HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            a.add(billId);
            String base = "select de.id as itemId, m.name as materialName, m.standard as standard, m.model as model, "
                    + "me.bar_code as barCode, de.sku as sku, de.batch_number as batchNumber, de.material_unit as unit, "
                    + "de.basic_number as qty, de.unit_price as unitPrice, ifnull(de.all_price,0) as allPrice, de.remark as remark "
                    + "from jsh_depot_item de join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + "where de.header_id = ? and ifnull(de.delete_flag,'0') <> '1' ";
            String tenantClause = tenantClause(tenantId, "de");
            if (tenantClause != null) {
                base += tenantClause;
            }
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by de.id", a, currentPage, pageSize);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** 导出 Excel：exportType = list（单据列表） / detail（单据明细） */
    @GetMapping("/export")
    @ApiOperation(value = "导出单据Excel")
    public void export(@RequestParam(value = "subType", required = false) String subType,
                       @RequestParam(value = "beginTime", required = false) String beginTime,
                       @RequestParam(value = "endTime", required = false) String endTime,
                       @RequestParam(value = "number", required = false) String number,
                       @RequestParam(value = "exportType", defaultValue = "list") String exportType,
                       HttpServletRequest request,
                       HttpServletResponse response) throws Exception {
        Long tenantId = tenant(request);
        String fileName = ("detail".equalsIgnoreCase(exportType) ? "单据明细" : "单据列表") + "-" + PluginUtils.trim(subType);
        fileName = fileName.replace(" ", "") + ".xls";
        response.setContentType("application/vnd.ms-excel;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename="
                + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"));
        WritableWorkbook workbook = Workbook.createWorkbook(response.getOutputStream());
        try {
            if ("detail".equalsIgnoreCase(exportType)) {
                writeDetail(workbook, tenantId, subType, beginTime, endTime, number);
            } else {
                writeList(workbook, tenantId, subType, beginTime, endTime, number);
            }
            workbook.write();
        } finally {
            workbook.close();
        }
    }

    private void writeList(WritableWorkbook workbook, Long tenantId, String subType, String beginTime,
                           String endTime, String number) throws Exception {
        WritableSheet sheet = workbook.createSheet("单据列表", 0);
        String[] heads = {"单据号", "单据类型", "业务时间", "单据金额", "结算方式", "状态", "业务员", "明细行数", "备注"};
        for (int i = 0; i < heads.length; i++) {
            sheet.addCell(new Label(i, 0, heads[i]));
        }
        List<Object> a = args();
        String sql = "select dh.number, dh.sub_type, date_format(dh.oper_time,'%Y-%m-%d %H:%i:%s') oper_time, "
                + "dh.total_price, dh.pay_type, dh.status, dh.sales_man, dh.remark, "
                + "(select count(1) from jsh_depot_item di where di.header_id = dh.id and ifnull(di.delete_flag,'0') <> '1') item_count "
                + billWhere(subType, beginTime, endTime, number, tenantId, a)
                + " order by dh.oper_time desc, dh.id desc limit 20000";
        List<Map<String, Object>> rows = jdbc().queryForList(sql, a.toArray());
        int r = 1;
        for (Map<String, Object> row : rows) {
            sheet.addCell(new Label(0, r, PluginUtils.trim(String.valueOf(row.get("number")))));
            sheet.addCell(new Label(1, r, PluginUtils.trim(String.valueOf(row.get("sub_type")))));
            sheet.addCell(new Label(2, r, PluginUtils.trim(String.valueOf(row.get("oper_time")))));
            sheet.addCell(new Label(3, r, PluginUtils.trim(String.valueOf(row.get("total_price")))));
            sheet.addCell(new Label(4, r, PluginUtils.trim(String.valueOf(row.get("pay_type")))));
            sheet.addCell(new Label(5, r, statusText(row.get("status"))));
            sheet.addCell(new Label(6, r, PluginUtils.trim(String.valueOf(row.get("sales_man")))));
            sheet.addCell(new Label(7, r, PluginUtils.trim(String.valueOf(row.get("item_count")))));
            sheet.addCell(new Label(8, r, PluginUtils.trim(String.valueOf(row.get("remark")))));
            r++;
        }
    }

    private void writeDetail(WritableWorkbook workbook, Long tenantId, String subType, String beginTime,
                             String endTime, String number) throws Exception {
        WritableSheet sheet = workbook.createSheet("单据明细", 0);
        String[] heads = {"单据号", "单据类型", "业务时间", "商品名称", "规格", "型号", "条码", "多属性",
                "批号", "单位", "数量", "单价", "金额", "备注"};
        for (int i = 0; i < heads.length; i++) {
            sheet.addCell(new Label(i, 0, heads[i]));
        }
        List<Object> a = args();
        String sql = "select dh.number, dh.sub_type, date_format(dh.oper_time,'%Y-%m-%d %H:%i:%s') oper_time, "
                + "m.name, m.standard, m.model, me.bar_code, de.sku, de.batch_number, de.material_unit, "
                + "de.basic_number, de.unit_price, de.all_price, de.remark "
                + "from jsh_depot_item de join jsh_depot_head dh on dh.id = de.header_id "
                + "join jsh_material m on m.id = de.material_id "
                + "left join jsh_material_extend me on me.id = de.material_extend_id "
                + billWhere(subType, beginTime, endTime, number, tenantId, a)
                + " order by dh.oper_time desc, dh.id desc, de.id limit 20000";
        List<Map<String, Object>> rows = jdbc().queryForList(sql, a.toArray());
        int r = 1;
        for (Map<String, Object> row : rows) {
            sheet.addCell(new Label(0, r, PluginUtils.trim(String.valueOf(row.get("number")))));
            sheet.addCell(new Label(1, r, PluginUtils.trim(String.valueOf(row.get("sub_type")))));
            sheet.addCell(new Label(2, r, PluginUtils.trim(String.valueOf(row.get("oper_time")))));
            sheet.addCell(new Label(3, r, PluginUtils.trim(String.valueOf(row.get("name")))));
            sheet.addCell(new Label(4, r, PluginUtils.trim(String.valueOf(row.get("standard")))));
            sheet.addCell(new Label(5, r, PluginUtils.trim(String.valueOf(row.get("model")))));
            sheet.addCell(new Label(6, r, PluginUtils.trim(String.valueOf(row.get("bar_code")))));
            sheet.addCell(new Label(7, r, PluginUtils.trim(String.valueOf(row.get("sku")))));
            sheet.addCell(new Label(8, r, PluginUtils.trim(String.valueOf(row.get("batch_number")))));
            sheet.addCell(new Label(9, r, PluginUtils.trim(String.valueOf(row.get("material_unit")))));
            sheet.addCell(new Label(10, r, PluginUtils.trim(String.valueOf(row.get("basic_number")))));
            sheet.addCell(new Label(11, r, PluginUtils.trim(String.valueOf(row.get("unit_price")))));
            sheet.addCell(new Label(12, r, PluginUtils.trim(String.valueOf(row.get("all_price")))));
            sheet.addCell(new Label(13, r, PluginUtils.trim(String.valueOf(row.get("remark")))));
            r++;
        }
    }

    private String billWhere(String subType, String beginTime, String endTime, String number,
                             Long tenantId, List<Object> a) {
        StringBuilder where = new StringBuilder(" where ifnull(dh.delete_flag,'0') <> '1' ");
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
        if (!PluginUtils.isEmpty(number)) {
            where.append(" and dh.number like ? ");
            a.add(PluginUtils.like(number));
        }
        String tenantClause = tenantClause(tenantId, "dh");
        if (tenantClause != null) {
            where.append(tenantClause);
        }
        return where.toString();
    }

    private String statusText(Object status) {
        String s = PluginUtils.trim(String.valueOf(status));
        if ("0".equals(s)) {
            return "未审核";
        } else if ("1".equals(s)) {
            return "已审核";
        } else if ("2".equals(s)) {
            return "已完成";
        } else if ("3".equals(s)) {
            return "部分完成";
        } else if ("9".equals(s)) {
            return "审核中";
        }
        return s;
    }
}

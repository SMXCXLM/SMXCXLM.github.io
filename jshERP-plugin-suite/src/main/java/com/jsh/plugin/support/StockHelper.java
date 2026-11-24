package com.jsh.plugin.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产、盘点等业务与 ERP 库存的衔接：生成出入库单据并同步当前库存。
 */
public final class StockHelper {

    private StockHelper() {
    }

    /**
     * 生成一张出入库单据并更新当前库存。
     *
     * @param type   入库 / 出库
     * @param subType 其它 / 盘点 / 生产领料 / 生产退料 / 生产入库
     */
    public static Long move(JdbcTemplate jdbc, Long tenantId, String type, String subType,
                            Long depotId, Long materialId, Long materialExtendId, String unit,
                            String sku, BigDecimal qty, BigDecimal price, String remark, Long operator) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal absQty = qty.abs();
        BigDecimal unitPrice = price == null ? BigDecimal.ZERO : price;
        BigDecimal amount = absQty.multiply(unitPrice).setScale(2, BigDecimal.ROUND_HALF_UP);

        Map<String, Object> head = new LinkedHashMap<String, Object>();
        head.put("type", type);
        head.put("sub_type", subType);
        head.put("number", number(type));
        head.put("default_number", head.get("number"));
        head.put("create_time", now());
        head.put("oper_time", now());
        head.put("total_price", amount);
        head.put("status", "1");
        head.put("remark", remark == null ? "生产/盘点插件生成" : remark);
        head.put("creator", operator);
        head.put("pay_type", "记账");
        head.put("bill_type", "其它");
        CrudSupport.insert(jdbc, "jsh_depot_head", head, tenantId);
        Long headId = CrudSupport.longVal(head.get("id"));

        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("header_id", headId);
        item.put("material_id", materialId);
        item.put("material_extend_id", materialExtendId);
        item.put("material_unit", unit);
        item.put("sku", sku);
        item.put("oper_number", absQty);
        item.put("basic_number", absQty);
        item.put("unit_price", unitPrice);
        item.put("purchase_unit_price", unitPrice);
        item.put("all_price", amount);
        item.put("depot_id", depotId);
        CrudSupport.insert(jdbc, "jsh_depot_item", item, tenantId);

        updateCurrentStock(jdbc, tenantId, depotId, materialId, "入库".equals(type) ? absQty : absQty.negate(), unitPrice);
        return headId;
    }

    public static void updateCurrentStock(JdbcTemplate jdbc, Long tenantId, Long depotId, Long materialId,
                                          BigDecimal delta, BigDecimal unitPrice) {
        if (depotId == null || materialId == null || delta == null) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, ifnull(current_number,0) as current_number from jsh_material_current_stock "
                        + "where material_id = ? and depot_id = ? and ifnull(delete_flag,'0') <> '1' "
                        + "and tenant_id = ? order by id limit 1", materialId, depotId, tenantId == null ? 0L : tenantId);
        if (rows.isEmpty()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("material_id", materialId);
            row.put("depot_id", depotId);
            row.put("current_number", delta);
            row.put("current_unit_price", unitPrice);
            CrudSupport.insert(jdbc, "jsh_material_current_stock", row, tenantId);
        } else {
            BigDecimal current = new BigDecimal(String.valueOf(rows.get(0).get("current_number")));
            BigDecimal after = current.add(delta);
            jdbc.update("update jsh_material_current_stock set current_number = ? where id = ?",
                    after, rows.get(0).get("id"));
        }
    }

    private static String number(String type) {
        String prefix = "入库".equals(type) ? "RK" : "CK";
        return prefix + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + (int) (Math.random() * 90 + 10);
    }

    private static Date now() {
        return new Date();
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> empty() {
        return new HashMap<String, Object>();
    }
}

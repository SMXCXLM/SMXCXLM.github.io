package com.jsh.plugin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsh.erp.utils.BaseResponseInfo;
import com.jsh.plugin.support.CrudSupport;
import com.jsh.plugin.support.PluginUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 零售收银模板：扫码/序列号、会员预付款、挂单取单、生成零售出库单、小票数据。
 */
@RestController
@RequestMapping("/pos")
@Api(tags = {"零售收银插件"})
public class PosController extends BasePluginController {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private PlatformTransactionManager transactionManager;

    @GetMapping("/init")
    @ApiOperation(value = "收银台初始化数据")
    public BaseResponseInfo init(HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            List<Object> a = args();
            String depotSql = "select id as value, name as label, ifnull(is_default,0) as isDefault from jsh_depot "
                    + "where enabled = 1 and ifnull(delete_Flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                depotSql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            List<Map<String, Object>> depots = jdbc.queryForList(depotSql + " order by is_default desc, id", a.toArray());
            List<Object> b = args();
            String memberSql = "select id as value, supplier as label, ifnull(advance_in,0) as advanceIn, phone_num as phone, "
                    + "contacts as contacts from jsh_supplier where type = '会员' and enabled = 1 and ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                memberSql += " and tenant_id = ? ";
                b.add(tenantId);
            }
            List<Map<String, Object>> members = jdbc.queryForList(memberSql + " order by supplier limit 200", b.toArray());
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("depots", depots);
            result.put("members", members);
            result.put("payTypes", new String[]{"现金", "会员预付款", "微信", "支付宝", "银行卡"});
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/search")
    @ApiOperation(value = "扫码/名称搜索商品")
    public BaseResponseInfo search(@RequestParam(value = "code", required = false) String code,
                                   @RequestParam(value = "depotId", required = false) Long depotId,
                                   @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                   HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            List<Object> a = args();
            String tenantStock = tenantId != null && tenantId != 0L ? " and mcs.tenant_id = " + tenantId + " " : "";
            String productBase = "select m.id as materialId, m.name as materialName, m.standard as standard, "
                    + "m.model as model, m.unit as unit, m.enable_serial_number as enableSerialNumber, "
                    + "me.id as extendId, me.bar_code as barCode, ifnull(me.commodity_unit, m.unit) as saleUnit, "
                    + "ifnull(me.sku,'') as sku, ifnull(me.commodity_decimal,0) as price, "
                    + "ifnull(me.wholesale_decimal,0) as wholesalePrice, ifnull(me.low_decimal,0) as lowPrice, "
                    + "ifnull(mcs.current_number,0) as stock, ifnull(mcs.current_unit_price,0) as costPrice "
                    + "from jsh_material m join jsh_material_extend me on me.material_id = m.id "
                    + "left join jsh_material_current_stock mcs on mcs.material_id = m.id and mcs.depot_id = ? "
                    + "and ifnull(mcs.delete_flag,'0') <> '1' " + tenantStock
                    + "where ifnull(m.delete_flag,'0') <> '1' and ifnull(me.delete_Flag,'0') <> '1' and m.enabled = 1 ";
            Object depot = depotId == null ? 0L : depotId;
            List<Map<String, Object>> rows;
            String trimmed = PluginUtils.trim(code);
            if (trimmed.isEmpty()) {
                List<Object> q = new ArrayList<Object>();
                q.add(depot);
                rows = jdbc.queryForList(productBase + " order by m.name, me.default_flag desc limit 20", q.toArray());
            } else {
                List<Object> q = new ArrayList<Object>();
                q.add(depot);
                q.add(trimmed);
                rows = jdbc.queryForList(productBase + " and me.bar_code = ? order by me.default_flag desc, me.id", q.toArray());
                if (rows.isEmpty()) {
                    List<Object> s = new ArrayList<Object>();
                    s.add(depot);
                    s.add(trimmed);
                    String serialSql = "select m.id as materialId, m.name as materialName, m.standard as standard, "
                            + "m.model as model, m.unit as unit, m.enable_serial_number as enableSerialNumber, "
                            + "me.id as extendId, me.bar_code as barCode, ifnull(me.commodity_unit, m.unit) as saleUnit, "
                            + "ifnull(me.sku,'') as sku, ifnull(me.commodity_decimal,0) as price, "
                            + "ifnull(me.wholesale_decimal,0) as wholesalePrice, ifnull(me.low_decimal,0) as lowPrice, "
                            + "1 as stock, ifnull(mcs.current_unit_price,0) as costPrice, sn.serial_number as serialNumber "
                            + "from jsh_serial_number sn join jsh_material m on m.id = sn.material_id "
                            + "left join jsh_material_extend me on me.material_id = m.id and ifnull(me.default_flag,'1') = '1' "
                            + "left join jsh_material_current_stock mcs on mcs.material_id = m.id and mcs.depot_id = ? "
                            + "and ifnull(mcs.delete_flag,'0') <> '1' " + tenantStock
                            + "where sn.serial_number = ? and ifnull(sn.delete_flag,'0') <> '1' and ifnull(sn.is_sell,'0') <> '1' ";
                    if (tenantId != null && tenantId != 0L) {
                        serialSql += " and sn.tenant_id = " + tenantId + " ";
                    }
                    rows = jdbc.queryForList(serialSql + " limit 10", s.toArray());
                }
                if (rows.isEmpty()) {
                    List<Object> n = new ArrayList<Object>();
                    n.add(depot);
                    n.add(PluginUtils.like(trimmed));
                    n.add(PluginUtils.like(trimmed));
                    n.add(PluginUtils.like(trimmed));
                    int max = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 100);
                    rows = jdbc.queryForList(productBase
                            + " and (m.name like ? or m.mnemonic like ? or m.model like ?) order by m.name limit " + max, n.toArray());
                }
            }
            return PluginUtils.ok(rows);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/member/search")
    @ApiOperation(value = "会员搜索")
    public BaseResponseInfo memberSearch(@RequestParam(value = "keyword", required = false) String keyword,
                                         HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select id as value, supplier as label, ifnull(advance_in,0) as advanceIn, phone_num as phone, "
                    + "contacts as contacts from jsh_supplier where type = '会员' and enabled = 1 and ifnull(delete_flag,'0') <> '1' ";
            if (!PluginUtils.isEmpty(keyword)) {
                sql += " and (supplier like ? or phone_num like ? or contacts like ?) ";
                a.add(PluginUtils.like(keyword));
                a.add(PluginUtils.like(keyword));
                a.add(PluginUtils.like(keyword));
            }
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by supplier limit 50", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/checkout")
    @ApiOperation(value = "收银结算，生成零售出库单")
    public BaseResponseInfo checkout(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            final Long operator = PluginUtils.userId(request);
            Long depotId = CrudSupport.longVal(body.get("depotId"));
            Long memberId = CrudSupport.longVal(body.get("memberId"));
            final String payType = PluginUtils.isEmpty(String.valueOf(body.get("payType"))) ? "现金" : String.valueOf(body.get("payType"));
            final String remark = PluginUtils.trim(String.valueOf(body.get("remark")));
            BigDecimal discount = PluginUtils.decimal(body.get("discount"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (depotId == null) {
                return PluginUtils.fail("请选择收银仓库");
            }
            if (items == null || items.isEmpty()) {
                return PluginUtils.fail("购物车为空");
            }
            final BigDecimal discountFinal = discount.max(BigDecimal.ZERO);
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            Map<String, Object> result = tx.execute(status -> {
                try {
                    JdbcTemplate jdbc = jdbc();
                    String number = nextNumber(jdbc, tenantId);
                    BigDecimal total = BigDecimal.ZERO;
                    for (Map<String, Object> item : items) {
                        BigDecimal qty = PluginUtils.decimal(item.get("qty"));
                        BigDecimal price = PluginUtils.decimal(item.get("price"));
                        total = total.add(qty.multiply(price));
                    }
                    BigDecimal payable = total.subtract(discountFinal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    Long billId = insertHead(jdbc, number, depotId, memberId, operator, payType, remark,
                            total, discountFinal, payable, tenantId);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    for (Map<String, Object> item : items) {
                        Long materialId = CrudSupport.longVal(item.get("materialId"));
                        Long extendId = CrudSupport.longVal(item.get("extendId"));
                        BigDecimal qty = PluginUtils.decimal(item.get("qty"));
                        BigDecimal price = PluginUtils.decimal(item.get("price"));
                        String unit = PluginUtils.trim(String.valueOf(item.get("unit")));
                        String sku = PluginUtils.trim(String.valueOf(item.get("sku")));
                        @SuppressWarnings("unchecked")
                        List<Object> snList = item.get("snList") instanceof List ? (List<Object>) item.get("snList") : new ArrayList<Object>();
                        if (materialId == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                            throw new IllegalArgumentException("商品明细不完整");
                        }
                        List<Object> sq = new ArrayList<Object>();
                        sq.add(materialId);
                        sq.add(depotId);
                        if (tenantId != null && tenantId != 0L) {
                            sq.add(tenantId);
                        }
                        List<Map<String, Object>> stocks = jdbc.queryForList(
                                "select id, ifnull(current_number,0) as currentNumber, ifnull(current_unit_price,0) as currentPrice "
                                        + "from jsh_material_current_stock where material_id = ? and depot_id = ? "
                                        + "and ifnull(delete_flag,'0') <> '1' "
                                        + (tenantId != null && tenantId != 0L ? " and tenant_id = ? " : ""), sq.toArray());
                        if (stocks.isEmpty()) {
                            throw new IllegalArgumentException("商品无库存记录：" + item.get("materialName"));
                        }
                        Map<String, Object> stock = stocks.get(0);
                        if (PluginUtils.decimal(stock.get("currentNumber")).compareTo(qty) < 0) {
                            throw new IllegalArgumentException("库存不足：" + item.get("materialName")
                                    + " 现存 " + stock.get("currentNumber"));
                        }
                        List<Object> snStrings = new ArrayList<Object>();
                        for (Object sn : snList) {
                            String snValue = PluginUtils.trim(String.valueOf(sn));
                            if (snValue.isEmpty()) {
                                continue;
                            }
                            List<Map<String, Object>> snRows = jdbc.queryForList(
                                    "select id from jsh_serial_number where serial_number = ? and material_id = ? "
                                            + "and depot_id = ? and ifnull(is_sell,'0') <> '1' and ifnull(delete_flag,'0') <> '1' "
                                            + (tenantId != null && tenantId != 0L ? " and tenant_id = ? " : ""),
                                    tenantId != null && tenantId != 0L
                                            ? new Object[]{snValue, materialId, depotId, tenantId}
                                            : new Object[]{snValue, materialId, depotId});
                            if (snRows.isEmpty()) {
                                throw new IllegalArgumentException("序列号不可用：" + snValue);
                            }
                            snStrings.add(snValue);
                        }
                        StringBuilder snBuilder = new StringBuilder();
                        for (Object sn : snStrings) {
                            if (snBuilder.length() > 0) {
                                snBuilder.append(",");
                            }
                            snBuilder.append(sn);
                        }
                        String snText = snBuilder.length() == 0 ? null : snBuilder.toString();
                        String sql = "insert into jsh_depot_item (header_id, material_id, material_extend_id, material_unit, sku, "
                                + "oper_number, basic_number, unit_price, purchase_unit_price, all_price, depot_id, tax_rate, "
                                + "tax_money, tax_last_money, sn_list, tenant_id, delete_flag) "
                                + "values (?,?,?,?,?,?,?,?,?,?,?,0,0,0,?,?, '0')";
                        jdbc.update(sql, billId, materialId, extendId, unit, sku, qty, qty, price,
                                stock.get("currentPrice"), qty.multiply(price).setScale(2, RoundingMode.HALF_UP),
                                depotId, snText, tenantId);
                        jdbc.update("update jsh_material_current_stock set current_number = current_number - ? where id = ?",
                                qty, stock.get("id"));
                        for (Object sn : snStrings) {
                            jdbc.update("update jsh_serial_number set is_sell = '1', out_bill_no = ?, update_time = ?, updater = ? "
                                    + "where serial_number = ? and material_id = ? and depot_id = ?",
                                    number, sdf.format(new Date()), operator, sn, materialId, depotId);
                        }
                    }
                    if ("会员预付款".equals(payType)) {
                        if (memberId == null) {
                            throw new IllegalArgumentException("会员预付款支付必须选择会员");
                        }
                        List<Object> ma = new ArrayList<Object>();
                        ma.add(memberId);
                        String msql = "select id, ifnull(advance_in,0) as advanceIn from jsh_supplier where id = ? and type = '会员' ";
                        if (tenantId != null && tenantId != 0L) {
                            msql += " and tenant_id = ? ";
                            ma.add(tenantId);
                        }
                        List<Map<String, Object>> members = jdbc.queryForList(msql, ma.toArray());
                        if (members.isEmpty()) {
                            throw new IllegalArgumentException("会员不存在");
                        }
                        if (PluginUtils.decimal(members.get(0).get("advanceIn")).compareTo(payable) < 0) {
                            throw new IllegalArgumentException("会员预付款余额不足，当前余额 " + members.get(0).get("advanceIn"));
                        }
                        jdbc.update("update jsh_supplier set advance_in = advance_in - ? where id = ?", payable, memberId);
                    }
                    Map<String, Object> data = new HashMap<String, Object>();
                    data.put("billId", billId);
                    data.put("number", number);
                    data.put("totalAmount", total);
                    data.put("discountAmount", discountFinal);
                    data.put("payableAmount", payable);
                    data.put("payType", payType);
                    return data;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e.getMessage(), e);
                }
            });
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/hold")
    @ApiOperation(value = "挂单")
    public BaseResponseInfo hold(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Map<String, Object>> items = body.get("items") instanceof List
                    ? (List<Map<String, Object>>) body.get("items") : new ArrayList<Map<String, Object>>();
            BigDecimal total = BigDecimal.ZERO;
            for (Map<String, Object> item : items) {
                total = total.add(PluginUtils.decimal(item.get("qty")).multiply(PluginUtils.decimal(item.get("price"))));
            }
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("hold_no", "HD" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                    + String.format("%03d", new Random().nextInt(1000)));
            data.put("member_id", body.get("memberId"));
            data.put("member_name", body.get("memberName"));
            data.put("depot_id", body.get("depotId"));
            data.put("items_json", JSON.writeValueAsString(items));
            data.put("total_amount", total);
            data.put("discount_amount", body.get("discount"));
            data.put("remark", body.get("remark"));
            data.put("creator", PluginUtils.userId(request));
            data.put("create_time", new Date());
            CrudSupport.insert(jdbc(), "jsh_pos_hold", data, tenantId);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/holds")
    @ApiOperation(value = "挂单列表")
    public BaseResponseInfo holds(HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select id, hold_no as holdNo, member_id as memberId, member_name as memberName, depot_id as depotId, "
                    + "total_amount as totalAmount, discount_amount as discountAmount, remark, "
                    + "date_format(create_time,'%Y-%m-%d %H:%i:%s') as createTime "
                    + "from jsh_pos_hold where ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by create_time desc", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/hold/get")
    @ApiOperation(value = "取单")
    public BaseResponseInfo holdGet(@RequestParam("id") Long id, HttpServletRequest request) {
        try {
            List<Map<String, Object>> rows = jdbc().queryForList("select * from jsh_pos_hold where id = ? and ifnull(delete_flag,'0') <> '1'", id);
            if (rows.isEmpty()) {
                return PluginUtils.fail("挂单不存在");
            }
            Map<String, Object> data = new HashMap<String, Object>(rows.get(0));
            Object items = data.get("items_json");
            if (items != null) {
                data.put("items", JSON.readValue(String.valueOf(items), List.class));
            }
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/hold/delete")
    @ApiOperation(value = "删除挂单")
    public BaseResponseInfo holdDelete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_pos_hold", id);
            return PluginUtils.ok("已删除");
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/receipt")
    @ApiOperation(value = "小票打印数据")
    public BaseResponseInfo receipt(@RequestParam("billId") Long billId, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            List<Map<String, Object>> heads = jdbc.queryForList(
                    "select dh.*, s.supplier as memberName, u.username as creatorName "
                            + "from jsh_depot_head dh left join jsh_supplier s on s.id = dh.organ_id "
                            + "left join jsh_user u on u.id = dh.creator where dh.id = ?", billId);
            List<Map<String, Object>> items = jdbc.queryForList(
                    "select i.*, m.name as materialName, m.standard as standard, m.model as model, "
                            + "me.bar_code as barCode from jsh_depot_item i left join jsh_material m on m.id = i.material_id "
                            + "left join jsh_material_extend me on me.id = i.material_extend_id "
                            + "where i.header_id = ? and ifnull(i.delete_flag,'0') <> '1' order by i.id", billId);
            List<Object> ta = args();
            String sql = "select * from jsh_print_template where bill_type = '零售出库' and ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                ta.add(tenantId);
            }
            List<Map<String, Object>> templates = jdbc.queryForList(sql + " order by is_default desc, id desc limit 1", ta.toArray());
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("head", heads.isEmpty() ? null : heads.get(0));
            result.put("items", items);
            result.put("template", templates.isEmpty() ? null : templates.get(0));
            return PluginUtils.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    private Long insertHead(JdbcTemplate jdbc, final String number, final Long depotId, final Long memberId,
                            final Long operator, final String payType, final String remark,
                            final BigDecimal total, final BigDecimal discount, final BigDecimal payable,
                            final Long tenantId) {
        String sql = "insert into jsh_depot_head (type, sub_type, default_number, number, create_time, oper_time, "
                + "organ_id, creator, change_amount, back_amount, total_price, pay_type, bill_type, remark, discount, "
                + "discount_money, status, source, tenant_id, delete_flag) "
                + "values ('出库','零售',?,?,now(),now(),?,?,?,0,?,?,?,?,?,?,'1','0',?,'0')";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, number);
            ps.setString(2, number);
            ps.setObject(3, memberId);
            ps.setObject(4, operator);
            ps.setBigDecimal(5, payable);
            ps.setBigDecimal(6, payable);
            ps.setString(7, payType);
            ps.setString(8, "零售出库");
            ps.setString(9, remark);
            ps.setBigDecimal(10, total);
            ps.setBigDecimal(11, discount);
            ps.setObject(12, tenantId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private String nextNumber(JdbcTemplate jdbc, Long tenantId) {
        for (int i = 0; i < 20; i++) {
            String number = "LS" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                    + String.format("%03d", new Random().nextInt(1000));
            List<Object> a = new ArrayList<Object>();
            a.add(number);
            String sql = "select count(*) from jsh_depot_head where number = ? ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            Long count = jdbc.queryForObject(sql, a.toArray(), Long.class);
            if (count == null || count == 0) {
                return number;
            }
        }
        throw new IllegalStateException("生成单据号失败");
    }
}

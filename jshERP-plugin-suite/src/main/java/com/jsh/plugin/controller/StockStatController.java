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
import java.util.List;
import java.util.Map;

/**
 * 库存统计类插件：多属性库存、批次库存、序列号统计、负库存统计。
 */
@RestController
@RequestMapping("/stockStat")
@Api(tags = {"库存统计插件"})
public class StockStatController extends BasePluginController {

    /** 多属性库存：按仓库 + 商品 + 多属性(sku) 汇总库存 */
    @GetMapping("/multiAttr")
    @ApiOperation(value = "多属性库存")
    public BaseResponseInfo multiAttr(@RequestParam(value = "depotId", required = false) Long depotId,
                                      @RequestParam(value = "materialParam", required = false) String materialParam,
                                      @RequestParam(value = "sku", required = false) String sku,
                                      @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                      @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                      HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String where = buildSkuWhere(depotId, materialParam, sku, tenantId, a);
            String base = "select de.depot_id as depotId, d.name as depotName, de.material_id as materialId, "
                    + "m.name as materialName, m.standard as standard, m.model as model, m.mnemonic as mnemonic, "
                    + "de.material_extend_id as materialExtendId, me.bar_code as barCode, me.sku as sku, "
                    + "sum(case when dh.type='入库' then de.basic_number else -de.basic_number end) as qty, "
                    + "round(sum(case when dh.type='入库' then de.basic_number else -de.basic_number end) "
                    + "* ifnull(mcs.current_unit_price,0), 2) as amount "
                    + "from jsh_depot_item de "
                    + "join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + "left join jsh_depot d on d.id = de.depot_id "
                    + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id and ifnull(mcs.delete_flag,'0') <> '1' "
                    + where
                    + " group by de.depot_id, d.name, de.material_id, m.name, m.standard, m.model, m.mnemonic, de.material_extend_id, me.bar_code, me.sku ";
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by qty desc", a, currentPage, pageSize);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** 批次库存：仅统计开启批号商品，按仓库 + 商品 + 批号汇总 */
    @GetMapping("/batch")
    @ApiOperation(value = "批次库存")
    public BaseResponseInfo batch(@RequestParam(value = "depotId", required = false) Long depotId,
                                  @RequestParam(value = "batchNumber", required = false) String batchNumber,
                                  @RequestParam(value = "materialParam", required = false) String materialParam,
                                  @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                  @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                  HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            StringBuilder where = new StringBuilder(" where dh.type in ('入库','出库') and dh.status in ('1','2','3') ")
                    .append(" and ifnull(dh.delete_flag,'0') <> '1' and ifnull(de.delete_flag,'0') <> '1' ")
                    .append(" and ifnull(m.delete_flag,'0') <> '1' ")
                    .append(" and m.enable_batch_number = '1' ")
                    .append(" and ifnull(de.batch_number,'') <> '' ");
            if (depotId != null) {
                where.append(" and de.depot_id = ? ");
                a.add(depotId);
            }
            if (!PluginUtils.isEmpty(batchNumber)) {
                where.append(" and de.batch_number like ? ");
                a.add(PluginUtils.like(batchNumber));
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
            String base = "select de.depot_id as depotId, d.name as depotName, de.material_id as materialId, "
                    + "m.name as materialName, m.standard as standard, m.model as model, m.mnemonic as mnemonic, "
                    + "de.material_extend_id as materialExtendId, me.bar_code as barCode, de.batch_number as batchNumber, "
                    + "date_format(max(de.expiration_date),'%Y-%m-%d') as expirationDate, "
                    + "sum(case when dh.type='入库' then de.basic_number else -de.basic_number end) as qty "
                    + "from jsh_depot_item de "
                    + "join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + "left join jsh_depot d on d.id = de.depot_id "
                    + where
                    + " group by de.depot_id, d.name, de.material_id, m.name, m.standard, m.model, m.mnemonic, "
                    + "de.material_extend_id, me.bar_code, de.batch_number ";
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by qty desc", a, currentPage, pageSize);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** 序列号统计：按序列号维度统计库存状态 */
    @GetMapping("/serial")
    @ApiOperation(value = "序列号统计")
    public BaseResponseInfo serial(@RequestParam(value = "serialNumber", required = false) String serialNumber,
                                   @RequestParam(value = "materialParam", required = false) String materialParam,
                                   @RequestParam(value = "depotId", required = false) Long depotId,
                                   @RequestParam(value = "isSell", required = false) String isSell,
                                   @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                   @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                   HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            StringBuilder where = new StringBuilder(" where ifnull(sn.delete_flag,'0') <> '1' and ifnull(m.delete_flag,'0') <> '1' ");
            if (!PluginUtils.isEmpty(serialNumber)) {
                where.append(" and sn.serial_number like ? ");
                a.add(PluginUtils.like(serialNumber));
            }
            if (!PluginUtils.isEmpty(materialParam)) {
                where.append(" and (m.name like ? or m.mnemonic like ? or me.bar_code like ? or m.standard like ? or m.model like ?) ");
                String like = PluginUtils.like(materialParam);
                for (int i = 0; i < 5; i++) {
                    a.add(like);
                }
            }
            if (depotId != null) {
                where.append(" and sn.depot_id = ? ");
                a.add(depotId);
            }
            if (!PluginUtils.isEmpty(isSell)) {
                where.append(" and sn.is_sell = ? ");
                a.add(isSell.trim());
            }
            String tenantClause = tenantClause(tenantId, "sn");
            if (tenantClause != null) {
                where.append(tenantClause);
            }
            String base = "select sn.id as id, sn.serial_number as serialNumber, sn.is_sell as isSell, "
                    + "sn.in_price as inPrice, sn.in_bill_no as inBillNo, sn.out_bill_no as outBillNo, "
                    + "date_format(sn.create_time,'%Y-%m-%d %H:%i:%s') as createTime, "
                    + "sn.depot_id as depotId, d.name as depotName, sn.material_id as materialId, "
                    + "m.name as materialName, m.standard as standard, m.model as model, me.bar_code as barCode "
                    + "from jsh_serial_number sn "
                    + "join jsh_material m on m.id = sn.material_id "
                    + "left join jsh_depot d on d.id = sn.depot_id "
                    + "left join jsh_material_extend me on me.material_id = sn.material_id and ifnull(me.default_flag,'1') = '1' and ifnull(me.delete_flag,'0') <> '1' "
                    + where;
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by sn.id desc", a, currentPage, pageSize);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** 负库存统计：按仓库 + 商品汇总，只返回库存小于 0 的记录 */
    @GetMapping("/negative")
    @ApiOperation(value = "负库存统计")
    public BaseResponseInfo negative(@RequestParam(value = "depotId", required = false) Long depotId,
                                     @RequestParam(value = "materialParam", required = false) String materialParam,
                                     @RequestParam(value = "categoryId", required = false) Long categoryId,
                                     @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                     @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                     HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            StringBuilder where = new StringBuilder(" where dh.type in ('入库','出库') and dh.status in ('1','2','3') ")
                    .append(" and ifnull(dh.delete_flag,'0') <> '1' and ifnull(de.delete_flag,'0') <> '1' ")
                    .append(" and ifnull(m.delete_flag,'0') <> '1' ");
            if (depotId != null) {
                where.append(" and de.depot_id = ? ");
                a.add(depotId);
            }
            if (categoryId != null) {
                where.append(" and m.category_id = ? ");
                a.add(categoryId);
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
            String base = "select de.depot_id as depotId, d.name as depotName, de.material_id as materialId, "
                    + "m.name as materialName, m.standard as standard, m.model as model, m.mnemonic as mnemonic, "
                    + "me.bar_code as barCode, "
                    + "sum(case when dh.type='入库' then de.basic_number else -de.basic_number end) as qty, "
                    + "ifnull(mcs.current_unit_price,0) as unitPrice, "
                    + "round(sum(case when dh.type='入库' then de.basic_number else -de.basic_number end) "
                    + "* ifnull(mcs.current_unit_price,0), 2) as amount "
                    + "from jsh_depot_item de "
                    + "join jsh_depot_head dh on dh.id = de.header_id "
                    + "join jsh_material m on m.id = de.material_id "
                    + "left join jsh_material_extend me on me.id = de.material_extend_id "
                    + "left join jsh_depot d on d.id = de.depot_id "
                    + "left join jsh_material_current_stock mcs on mcs.material_id = de.material_id and mcs.depot_id = de.depot_id and ifnull(mcs.delete_flag,'0') <> '1' "
                    + where
                    + " group by de.depot_id, d.name, de.material_id, m.name, m.standard, m.model, m.mnemonic, me.bar_code, mcs.current_unit_price "
                    + " having qty < 0 ";
            String countSql = "select count(*) from (" + base + ") t";
            Map<String, Object> data = page(countSql, base + " order by qty asc", a, currentPage, pageSize);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    private String buildSkuWhere(Long depotId, String materialParam, String sku, Long tenantId, List<Object> a) {
        StringBuilder where = new StringBuilder(" where dh.type in ('入库','出库') and dh.status in ('1','2','3') ")
                .append(" and ifnull(dh.delete_flag,'0') <> '1' and ifnull(de.delete_flag,'0') <> '1' ")
                .append(" and ifnull(m.delete_flag,'0') <> '1' ")
                .append(" and ifnull(me.sku,'') <> '' ");
        if (depotId != null) {
            where.append(" and de.depot_id = ? ");
            a.add(depotId);
        }
        if (!PluginUtils.isEmpty(sku)) {
            where.append(" and me.sku like ? ");
            a.add(PluginUtils.like(sku));
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
}

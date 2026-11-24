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
 * 生产管理：BOM、生产任务、领退料/用料、工序汇报、验收、完工入库。
 */
@RestController
@RequestMapping("/production")
@Api(tags = {"生产管理插件"})
public class ProductionController extends BasePluginController {

    // ---------------- BOM ----------------

    @GetMapping("/bom/list")
    @ApiOperation(value = "BOM列表")
    public BaseResponseInfo bomList(@RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "materialId", required = false) Long materialId,
                                    @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                    @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                    HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select b.*, m.name as materialName, m.standard as standard, m.model as model, "
                    + "(select count(*) from jsh_pd_bom_item i where i.bom_id = b.id and ifnull(i.delete_flag,'0') <> '1') as itemCount "
                    + "from jsh_pd_bom b left join jsh_material m on m.id = b.material_id "
                    + "where ifnull(b.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and b.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(keyword)) {
                base += " and (b.bom_no like ? or b.bom_name like ? or m.name like ? or m.mnemonic like ?) ";
                String like = PluginUtils.like(keyword);
                for (int i = 0; i < 4; i++) {
                    a.add(like);
                }
            }
            if (materialId != null) {
                base += " and b.material_id = ? ";
                a.add(materialId);
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t", base + " order by b.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/bom/get")
    @ApiOperation(value = "BOM详情（含物料与工序）")
    public BaseResponseInfo bomGet(@RequestParam("id") Long id, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            List<Map<String, Object>> bom = jdbc.queryForList("select * from jsh_pd_bom where id = ?", id);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("bom", bom.isEmpty() ? null : bom.get(0));
            data.put("items", jdbc.queryForList("select i.*, m.name as materialName, m.standard as standard, m.model as model, "
                    + "me.bar_code as barCode from jsh_pd_bom_item i "
                    + "left join jsh_material m on m.id = i.material_id "
                    + "left join jsh_material_extend me on me.id = i.material_extend_id "
                    + "where i.bom_id = ? and ifnull(i.delete_flag,'0') <> '1' order by i.id", id));
            data.put("processes", jdbc.queryForList("select * from jsh_pd_bom_process where bom_id = ? "
                    + "and ifnull(delete_flag,'0') <> '1' order by seq, id", id));
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/bom/save")
    @ApiOperation(value = "保存BOM")
    @SuppressWarnings("unchecked")
    public BaseResponseInfo bomSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                CrudSupport.insert(jdbc, "jsh_pd_bom", body, tenantId);
                id = CrudSupport.longVal(body.get("id"));
            } else {
                CrudSupport.update(jdbc, "jsh_pd_bom", id, body);
                jdbc.update("update jsh_pd_bom_item set delete_flag = '1' where bom_id = ?", id);
                jdbc.update("update jsh_pd_bom_process set delete_flag = '1' where bom_id = ?", id);
            }
            if (body.get("items") instanceof List) {
                for (Object o : (List<Object>) body.get("items")) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<String, Object>((Map<String, Object>) o);
                    item.remove("id");
                    item.put("bom_id", id);
                    CrudSupport.insert(jdbc, "jsh_pd_bom_item", item, tenantId);
                }
            }
            if (body.get("processes") instanceof List) {
                for (Object o : (List<Object>) body.get("processes")) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> p = new LinkedHashMap<String, Object>((Map<String, Object>) o);
                    p.remove("id");
                    p.put("bom_id", id);
                    CrudSupport.insert(jdbc, "jsh_pd_bom_process", p, tenantId);
                }
            }
            body.put("id", id);
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/bom/delete")
    @ApiOperation(value = "删除BOM")
    public BaseResponseInfo bomDelete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            JdbcTemplate jdbc = jdbc();
            CrudSupport.softDelete(jdbc, "jsh_pd_bom", id);
            jdbc.update("update jsh_pd_bom_item set delete_flag = '1' where bom_id = ?", id);
            jdbc.update("update jsh_pd_bom_process set delete_flag = '1' where bom_id = ?", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 生产任务 ----------------

    @GetMapping("/task/list")
    @ApiOperation(value = "生产任务列表")
    public BaseResponseInfo taskList(@RequestParam(value = "status", required = false) String status,
                                     @RequestParam(value = "keyword", required = false) String keyword,
                                     @RequestParam(value = "beginTime", required = false) String beginTime,
                                     @RequestParam(value = "endTime", required = false) String endTime,
                                     @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                     @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                     HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select t.*, m.name as materialName, m.standard as standard, m.model as model, b.bom_name as bomName "
                    + "from jsh_pd_task t left join jsh_material m on m.id = t.material_id "
                    + "left join jsh_pd_bom b on b.id = t.bom_id where ifnull(t.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and t.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(status)) {
                base += " and t.status = ? ";
                a.add(status.trim());
            }
            if (!PluginUtils.isEmpty(keyword)) {
                base += " and (t.task_no like ? or m.name like ? or t.workshop like ?) ";
                String like = PluginUtils.like(keyword);
                for (int i = 0; i < 3; i++) {
                    a.add(like);
                }
            }
            if (!PluginUtils.isEmpty(beginTime)) {
                base += " and t.start_date >= ? ";
                a.add(beginTime.trim());
            }
            if (!PluginUtils.isEmpty(endTime)) {
                base += " and t.end_date <= ? ";
                a.add(endTime.trim());
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t", base + " order by t.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/task/get")
    @ApiOperation(value = "生产任务详情（含应领料清单）")
    public BaseResponseInfo taskGet(@RequestParam("id") Long id, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            List<Map<String, Object>> task = jdbc.queryForList("select t.*, m.name as materialName, m.standard as standard, "
                    + "m.model as model, b.bom_name as bomName, b.output_qty as outputQty from jsh_pd_task t "
                    + "left join jsh_material m on m.id = t.material_id left join jsh_pd_bom b on b.id = t.bom_id "
                    + "where t.id = ?", id);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            Map<String, Object> row = task.isEmpty() ? null : task.get(0);
            data.put("task", row);
            if (row != null) {
                BigDecimal planQty = PluginUtils.decimal(row.get("plan_qty"));
                BigDecimal output = PluginUtils.decimal(row.get("outputQty"));
                if (output.compareTo(BigDecimal.ZERO) == 0) {
                    output = BigDecimal.ONE;
                }
                BigDecimal times = planQty.divide(output, 4, BigDecimal.ROUND_HALF_UP);
                List<Map<String, Object>> items = jdbc.queryForList("select i.material_id as materialId, i.material_extend_id as materialExtendId, "
                        + "m.name as materialName, m.standard as standard, m.model as model, me.bar_code as barCode, i.unit, i.qty, i.loss_rate as lossRate "
                        + "from jsh_pd_bom_item i left join jsh_material m on m.id = i.material_id "
                        + "left join jsh_material_extend me on me.id = i.material_extend_id "
                        + "where i.bom_id = ? and ifnull(i.delete_flag,'0') <> '1'", row.get("bom_id"));
                for (Map<String, Object> item : items) {
                    BigDecimal need = PluginUtils.decimal(item.get("qty")).multiply(times)
                            .multiply(BigDecimal.ONE.add(PluginUtils.decimal(item.get("lossRate"))
                                    .divide(new BigDecimal("100"), 6, BigDecimal.ROUND_HALF_UP)));
                    item.put("needQty", need.setScale(4, BigDecimal.ROUND_HALF_UP));
                }
                data.put("bomItems", items);
                data.put("materials", jdbc.queryForList("select * from jsh_pd_task_material where task_id = ? "
                        + "and ifnull(delete_flag,'0') <> '1' order by id", id));
                data.put("reports", jdbc.queryForList("select * from jsh_pd_process_report where task_id = ? "
                        + "and ifnull(delete_flag,'0') <> '1' order by id", id));
                data.put("inspections", jdbc.queryForList("select * from jsh_pd_inspection where task_id = ? "
                        + "and ifnull(delete_flag,'0') <> '1' order by id", id));
            }
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/task/save")
    @ApiOperation(value = "保存生产任务")
    public BaseResponseInfo taskSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                if (PluginUtils.isEmpty(String.valueOf(body.get("task_no")))) {
                    body.put("task_no", "SC" + System.currentTimeMillis());
                }
                CrudSupport.insert(jdbc, "jsh_pd_task", body, tenantId);
            } else {
                CrudSupport.update(jdbc, "jsh_pd_task", id, body);
            }
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/task/delete")
    @ApiOperation(value = "删除生产任务")
    public BaseResponseInfo taskDelete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_pd_task", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 领料 / 退料 / 用料 ----------------

    @GetMapping("/material/list")
    @ApiOperation(value = "生产领退料记录")
    public BaseResponseInfo materialList(@RequestParam(value = "taskId", required = false) Long taskId,
                                         @RequestParam(value = "type", required = false) String type,
                                         @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                         @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
                                         HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select tm.*, m.name as materialName, m.standard as standard, m.model as model, "
                    + "d.name as depotName, p.name as operatorName from jsh_pd_task_material tm "
                    + "left join jsh_material m on m.id = tm.material_id "
                    + "left join jsh_depot d on d.id = tm.depot_id "
                    + "left join jsh_person p on p.id = tm.operator "
                    + "where ifnull(tm.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and tm.tenant_id = ? ";
                a.add(tenantId);
            }
            if (taskId != null) {
                base += " and tm.task_id = ? ";
                a.add(taskId);
            }
            if (!PluginUtils.isEmpty(type)) {
                base += " and tm.type = ? ";
                a.add(type.trim());
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t", base + " order by tm.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/material/save")
    @ApiOperation(value = "新增领料/退料/用料（联动库存）")
    public BaseResponseInfo materialSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long taskId = CrudSupport.longVal(body.get("task_id"));
            String type = PluginUtils.trim(String.valueOf(body.get("type")));
            if (taskId == null) {
                return PluginUtils.fail("缺少生产任务");
            }
            if (!"领料".equals(type) && !"退料".equals(type) && !"用料".equals(type)) {
                return PluginUtils.fail("类型只能为 领料 / 退料 / 用料");
            }
            body.put("type", type);
            body.put("oper_time", body.get("oper_time") == null ? new java.util.Date() : body.get("oper_time"));
            CrudSupport.insert(jdbc, "jsh_pd_task_material", body, tenantId);

            BigDecimal qty = PluginUtils.decimal(body.get("qty"));
            Long depotId = CrudSupport.longVal(body.get("depot_id"));
            Long materialId = CrudSupport.longVal(body.get("material_id"));
            String stockType = "退料".equals(type) ? "入库" : "出库";
            StockHelper.move(jdbc, tenantId, stockType, "领料".equals(type) ? "生产领料" : ("退料".equals(type) ? "生产退料" : "生产用料"),
                    depotId, materialId, CrudSupport.longVal(body.get("material_extend_id")),
                    PluginUtils.trim(String.valueOf(body.get("unit"))), null, qty, BigDecimal.ZERO,
                    "生产任务 " + taskId + " " + type, CrudSupport.longVal(body.get("operator")));

            // 领料/用料时任务自动进入生产中
            jdbc.update("update jsh_pd_task set status = '1' where id = ? and status = '0'", taskId);
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/material/delete")
    @ApiOperation(value = "删除领退料记录并回冲库存")
    public BaseResponseInfo materialDelete(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            List<Map<String, Object>> rows = jdbc.queryForList("select * from jsh_pd_task_material where id = ?", id);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                String type = String.valueOf(row.get("type"));
                BigDecimal qty = PluginUtils.decimal(row.get("qty"));
                String reverse = "退料".equals(type) ? "出库" : "入库";
                StockHelper.move(jdbc, CrudSupport.longVal(row.get("tenant_id")), reverse, "其它",
                        CrudSupport.longVal(row.get("depot_id")), CrudSupport.longVal(row.get("material_id")),
                        CrudSupport.longVal(row.get("material_extend_id")), String.valueOf(row.get("unit")),
                        null, qty, BigDecimal.ZERO, "撤销生产" + type, CrudSupport.longVal(row.get("operator")));
            }
            CrudSupport.softDelete(jdbc, "jsh_pd_task_material", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 工序汇报 ----------------

    @GetMapping("/process/report/list")
    @ApiOperation(value = "工序汇报列表")
    public BaseResponseInfo reportList(@RequestParam(value = "taskId", required = false) Long taskId,
                                       @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                       @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
                                       HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select r.*, t.task_no as taskNo from jsh_pd_process_report r "
                    + "left join jsh_pd_task t on t.id = r.task_id where ifnull(r.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and r.tenant_id = ? ";
                a.add(tenantId);
            }
            if (taskId != null) {
                base += " and r.task_id = ? ";
                a.add(taskId);
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t", base + " order by r.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/process/report/save")
    @ApiOperation(value = "保存工序汇报")
    public BaseResponseInfo reportSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            body.put("report_time", body.get("report_time") == null ? new java.util.Date() : body.get("report_time"));
            CrudSupport.insert(jdbc(), "jsh_pd_process_report", body, tenant(request));
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/process/report/delete")
    @ApiOperation(value = "删除工序汇报")
    public BaseResponseInfo reportDelete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_pd_process_report", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 验收 ----------------

    @GetMapping("/inspection/list")
    @ApiOperation(value = "验收记录列表")
    public BaseResponseInfo inspectionList(@RequestParam(value = "taskId", required = false) Long taskId,
                                           @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                           @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
                                           HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select i.*, t.task_no as taskNo from jsh_pd_inspection i "
                    + "left join jsh_pd_task t on t.id = i.task_id where ifnull(i.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and i.tenant_id = ? ";
                a.add(tenantId);
            }
            if (taskId != null) {
                base += " and i.task_id = ? ";
                a.add(taskId);
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t", base + " order by i.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/inspection/save")
    @ApiOperation(value = "保存验收记录")
    public BaseResponseInfo inspectionSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            if (PluginUtils.isEmpty(String.valueOf(body.get("inspect_no")))) {
                body.put("inspect_no", "YS" + System.currentTimeMillis());
            }
            body.put("inspect_time", body.get("inspect_time") == null ? new java.util.Date() : body.get("inspect_time"));
            CrudSupport.insert(jdbc, "jsh_pd_inspection", body, tenant(request));
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/inspection/delete")
    @ApiOperation(value = "删除验收记录")
    public BaseResponseInfo inspectionDelete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_pd_inspection", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 完工入库 ----------------

    @GetMapping("/instock/list")
    @ApiOperation(value = "完工入库列表")
    public BaseResponseInfo instockList(@RequestParam(value = "taskId", required = false) Long taskId,
                                        @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                        @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
                                        HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select s.*, t.task_no as taskNo, m.name as materialName, d.name as depotName "
                    + "from jsh_pd_instock s left join jsh_pd_task t on t.id = s.task_id "
                    + "left join jsh_material m on m.id = s.material_id left join jsh_depot d on d.id = s.depot_id "
                    + "where ifnull(s.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and s.tenant_id = ? ";
                a.add(tenantId);
            }
            if (taskId != null) {
                base += " and s.task_id = ? ";
                a.add(taskId);
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t", base + " order by s.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/instock/save")
    @ApiOperation(value = "完工入库（生成入库单据并增加库存）")
    public BaseResponseInfo instockSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long taskId = CrudSupport.longVal(body.get("task_id"));
            Long materialId = CrudSupport.longVal(body.get("material_id"));
            Long depotId = CrudSupport.longVal(body.get("depot_id"));
            BigDecimal qty = PluginUtils.decimal(body.get("qty"));
            if (materialId == null || depotId == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                return PluginUtils.fail("商品、仓库、入库数量不能为空");
            }
            if (PluginUtils.isEmpty(String.valueOf(body.get("number")))) {
                body.put("number", "CP" + System.currentTimeMillis());
            }
            body.put("oper_time", body.get("oper_time") == null ? new java.util.Date() : body.get("oper_time"));
            Long headId = StockHelper.move(jdbc, tenantId, "入库", "生产入库", depotId, materialId,
                    CrudSupport.longVal(body.get("material_extend_id")), PluginUtils.trim(String.valueOf(body.get("unit"))),
                    null, qty, PluginUtils.decimal(body.get("unit_price")), "生产完工入库", null);
            body.put("depot_head_id", headId);
            CrudSupport.insert(jdbc, "jsh_pd_instock", body, tenantId);

            if (taskId != null) {
                jdbc.update("update jsh_pd_task set finished_qty = ifnull(finished_qty,0) + ? where id = ?", qty, taskId);
                List<Map<String, Object>> task = jdbc.queryForList(
                        "select plan_qty, finished_qty from jsh_pd_task where id = ?", taskId);
                if (!task.isEmpty() && PluginUtils.decimal(task.get(0).get("finished_qty"))
                        .compareTo(PluginUtils.decimal(task.get(0).get("plan_qty"))) >= 0) {
                    jdbc.update("update jsh_pd_task set status = '2' where id = ?", taskId);
                }
            }
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/instock/delete")
    @ApiOperation(value = "删除完工入库（回冲库存）")
    public BaseResponseInfo instockDelete(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            List<Map<String, Object>> rows = jdbc.queryForList("select * from jsh_pd_instock where id = ?", id);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                StockHelper.move(jdbc, CrudSupport.longVal(row.get("tenant_id")), "出库", "其它",
                        CrudSupport.longVal(row.get("depot_id")), CrudSupport.longVal(row.get("material_id")),
                        CrudSupport.longVal(row.get("material_extend_id")), String.valueOf(row.get("unit")),
                        null, PluginUtils.decimal(row.get("qty")), BigDecimal.ZERO, "撤销完工入库", null);
                Long taskId = CrudSupport.longVal(row.get("task_id"));
                if (taskId != null) {
                    jdbc.update("update jsh_pd_task set finished_qty = ifnull(finished_qty,0) - ? where id = ?",
                            PluginUtils.decimal(row.get("qty")), taskId);
                }
            }
            CrudSupport.softDelete(jdbc, "jsh_pd_instock", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

}

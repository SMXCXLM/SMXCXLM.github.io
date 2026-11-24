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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多级审核：流程配置（最多 5 级）、发起、待办/已办/已发、撤回与审核历史。
 */
@RestController
@RequestMapping("/auditFlow")
@Api(tags = {"多级审核插件"})
public class AuditFlowController extends BasePluginController {

    // ---------------- 流程配置 ----------------

    @GetMapping("/flow/list")
    @ApiOperation(value = "审核流程列表")
    public BaseResponseInfo flowList(@RequestParam(value = "billType", required = false) String billType,
                                     HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select f.*, "
                    + "(select group_concat(concat(l.level,':',l.approver_type) order by l.level) from jsh_af_flow_level l "
                    + "where l.flow_id = f.id and ifnull(l.delete_flag,'0') <> '1') as levels "
                    + "from jsh_af_flow f where ifnull(f.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and f.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(billType)) {
                sql += " and f.bill_type = ? ";
                a.add(billType.trim());
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by f.id desc", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/flow/get")
    @ApiOperation(value = "流程详情（含各级审核人配置）")
    public BaseResponseInfo flowGet(@RequestParam("id") Long id) {
        try {
            JdbcTemplate jdbc = jdbc();
            List<Map<String, Object>> flow = jdbc.queryForList("select * from jsh_af_flow where id = ?", id);
            List<Map<String, Object>> levels = jdbc.queryForList("select l.*, u.username as approverName from jsh_af_flow_level l "
                    + "left join jsh_user u on u.id = l.approver_id where l.flow_id = ? "
                    + "and ifnull(l.delete_flag,'0') <> '1' order by l.level", id);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("flow", flow.isEmpty() ? null : flow.get(0));
            data.put("levels", levels);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/flow/save")
    @ApiOperation(value = "保存流程配置（最多 5 级）")
    @SuppressWarnings("unchecked")
    public BaseResponseInfo flowSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            List<Object> rawLevels = body.get("levels") instanceof List ? (List<Object>) body.get("levels") : new ArrayList<Object>();
            if (rawLevels.size() > 5) {
                return PluginUtils.fail("最多支持 5 级审核");
            }
            Long id = CrudSupport.longVal(body.get("id"));
            body.put("max_level", rawLevels.size());
            if (id == null) {
                CrudSupport.insert(jdbc, "jsh_af_flow", body, tenantId);
                id = CrudSupport.longVal(body.get("id"));
            } else {
                CrudSupport.update(jdbc, "jsh_af_flow", id, body);
                jdbc.update("update jsh_af_flow_level set delete_flag = '1' where flow_id = ?", id);
            }
            int level = 1;
            for (Object o : rawLevels) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<String, Object> l = new LinkedHashMap<String, Object>((Map<String, Object>) o);
                l.remove("id");
                l.put("flow_id", id);
                l.put("level", level++);
                CrudSupport.insert(jdbc, "jsh_af_flow_level", l, tenantId);
            }
            body.put("id", id);
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/flow/delete")
    @ApiOperation(value = "删除流程")
    public BaseResponseInfo flowDelete(@RequestBody Map<String, Object> body) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc, "jsh_af_flow", id);
            jdbc.update("update jsh_af_flow_level set delete_flag = '1' where flow_id = ?", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 发起与审批 ----------------

    @PostMapping("/instance/start")
    @ApiOperation(value = "发起审核")
    public BaseResponseInfo start(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            String billType = PluginUtils.trim(String.valueOf(body.get("bill_type")));
            Long billId = CrudSupport.longVal(body.get("bill_id"));
            if (PluginUtils.isEmpty(billType) || billId == null) {
                return PluginUtils.fail("缺少单据类型或单据 id");
            }
            Long flowId = CrudSupport.longVal(body.get("flow_id"));
            if (flowId == null) {
                List<Map<String, Object>> flows = jdbc.queryForList("select id from jsh_af_flow where bill_type = ? "
                                + "and ifnull(enabled,'1') <> '0' and ifnull(delete_flag,'0') <> '1' "
                                + "and tenant_id = ? order by id desc limit 1",
                        billType, tenantId == null ? 0L : tenantId);
                if (flows.isEmpty()) {
                    return PluginUtils.fail("该单据类型未配置审核流程");
                }
                flowId = CrudSupport.longVal(flows.get(0).get("id"));
            }
            Long creator = CrudSupport.longVal(body.get("creator"));
            Map<String, Object> instance = new LinkedHashMap<String, Object>();
            instance.put("flow_id", flowId);
            instance.put("bill_type", billType);
            instance.put("bill_id", billId);
            instance.put("bill_number", body.get("bill_number"));
            instance.put("title", body.get("title") == null ? billType + " 审核" : body.get("title"));
            instance.put("status", "0");
            instance.put("current_level", 1);
            instance.put("creator", creator);
            instance.put("create_time", new java.util.Date());
            CrudSupport.insert(jdbc, "jsh_af_instance", instance, tenantId);
            Long instanceId = CrudSupport.longVal(instance.get("id"));

            Long approver = resolveApprover(jdbc, flowId, 1, creator, tenantId);
            if (approver == null) {
                return PluginUtils.fail("第 1 级未找到审核人，请检查流程配置或部门负责人设置");
            }
            createTask(jdbc, tenantId, instanceId, 1, approver, instance.get("title"));
            return PluginUtils.ok(instance);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/instance/approve")
    @ApiOperation(value = "审核（同意/驳回）")
    public BaseResponseInfo approve(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long taskId = CrudSupport.longVal(body.get("task_id"));
            if (taskId == null) {
                return PluginUtils.fail("缺少待办任务 id");
            }
            String result = "2".equals(String.valueOf(body.get("result"))) ? "2" : "1";
            List<Map<String, Object>> tasks = jdbc.queryForList("select * from jsh_af_task where id = ?", taskId);
            if (tasks.isEmpty()) {
                return PluginUtils.fail("待办不存在");
            }
            Map<String, Object> task = tasks.get(0);
            if ("1".equals(String.valueOf(task.get("status")))) {
                return PluginUtils.fail("该待办已处理");
            }
            Long instanceId = CrudSupport.longVal(task.get("instance_id"));
            List<Map<String, Object>> instances = jdbc.queryForList("select * from jsh_af_instance where id = ?", instanceId);
            if (instances.isEmpty() || !"0".equals(String.valueOf(instances.get(0).get("status")))) {
                return PluginUtils.fail("流程不在审核中");
            }
            Map<String, Object> instance = instances.get(0);
            jdbc.update("update jsh_af_task set status = '1', result = ?, comment = ?, handle_time = ? where id = ?",
                    result, body.get("comment"), new java.util.Date(), taskId);

            if ("2".equals(result)) {
                jdbc.update("update jsh_af_instance set status = '4', finish_time = ? where id = ?",
                        new java.util.Date(), instanceId);
                notify(jdbc, CrudSupport.longVal(instance.get("creator")), "审核被驳回", instance.get("title") + " 被驳回");
                return PluginUtils.ok("已驳回");
            }
            Long flowId = CrudSupport.longVal(instance.get("flow_id"));
            int currentLevel = PluginUtils.intValue(String.valueOf(instance.get("current_level")), 1);
            int maxLevel = getMaxLevel(jdbc, flowId);
            if (currentLevel >= maxLevel) {
                jdbc.update("update jsh_af_instance set status = '1', finish_time = ? where id = ?",
                        new java.util.Date(), instanceId);
                notify(jdbc, CrudSupport.longVal(instance.get("creator")), "审核通过", instance.get("title") + " 已审核通过");
                return PluginUtils.ok("审核完成");
            }
            int nextLevel = currentLevel + 1;
            Long approver = resolveApprover(jdbc, flowId, nextLevel,
                    CrudSupport.longVal(instance.get("creator")), CrudSupport.longVal(instance.get("tenant_id")));
            if (approver == null) {
                return PluginUtils.fail("第 " + nextLevel + " 级未找到审核人");
            }
            createTask(jdbc, CrudSupport.longVal(instance.get("tenant_id")), instanceId, nextLevel, approver, instance.get("title"));
            jdbc.update("update jsh_af_instance set current_level = ? where id = ?", nextLevel, instanceId);
            return PluginUtils.ok("已同意，流转到第 " + nextLevel + " 级");
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/instance/withdraw")
    @ApiOperation(value = "撤回审核")
    public BaseResponseInfo withdraw(@RequestBody Map<String, Object> body) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long instanceId = CrudSupport.longVal(body.get("id"));
            if (instanceId == null) {
                return PluginUtils.fail("缺少实例 id");
            }
            jdbc.update("update jsh_af_instance set status = '2', finish_time = ? where id = ? and status = '0'",
                    new java.util.Date(), instanceId);
            jdbc.update("update jsh_af_task set status = '1', result = '3', comment = '发起人撤回' "
                    + "where instance_id = ? and status = '0'", instanceId);
            return PluginUtils.ok("已撤回");
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/instance/delete")
    @ApiOperation(value = "删除审核实例")
    public BaseResponseInfo deleteInstance(@RequestBody Map<String, Object> body) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long instanceId = CrudSupport.longVal(body.get("id"));
            if (instanceId == null) {
                return PluginUtils.fail("缺少实例 id");
            }
            jdbc.update("update jsh_af_instance set status = '3', delete_flag = '1' where id = ?", instanceId);
            jdbc.update("update jsh_af_task set delete_flag = '1' where instance_id = ?", instanceId);
            return PluginUtils.ok("已删除");
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/instance/history")
    @ApiOperation(value = "审核历史（流转轨迹）")
    public BaseResponseInfo history(@RequestParam("id") Long instanceId) {
        try {
            JdbcTemplate jdbc = jdbc();
            List<Map<String, Object>> instances = jdbc.queryForList("select * from jsh_af_instance where id = ?", instanceId);
            List<Map<String, Object>> tasks = jdbc.queryForList("select t.*, u.username as approverName from jsh_af_task t "
                    + "left join jsh_user u on u.id = t.approver_id where t.instance_id = ? "
                    + "and ifnull(t.delete_flag,'0') <> '1' order by t.level, t.id", instanceId);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("instance", instances.isEmpty() ? null : instances.get(0));
            data.put("tasks", tasks);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/task/todo")
    @ApiOperation(value = "我的待办")
    public BaseResponseInfo todo(@RequestParam(value = "approverId", required = false) Long approverId,
                                 @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                 @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                 HttpServletRequest request) {
        return taskQuery("0", approverId, currentPage, pageSize, request);
    }

    @GetMapping("/task/done")
    @ApiOperation(value = "我的已办")
    public BaseResponseInfo done(@RequestParam(value = "approverId", required = false) Long approverId,
                                 @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                 @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                 HttpServletRequest request) {
        return taskQuery("1", approverId, currentPage, pageSize, request);
    }

    @GetMapping("/instance/mine")
    @ApiOperation(value = "我发起的")
    public BaseResponseInfo mine(@RequestParam(value = "creator", required = false) Long creator,
                                 @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                 @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                 HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select i.*, u.username as creatorName from jsh_af_instance i "
                    + "left join jsh_user u on u.id = i.creator where ifnull(i.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and i.tenant_id = ? ";
                a.add(tenantId);
            }
            if (creator != null && creator != 0L) {
                base += " and i.creator = ? ";
                a.add(creator);
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t", base + " order by i.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    private BaseResponseInfo taskQuery(String status, Long approverId, Integer currentPage, Integer pageSize,
                                       HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select t.*, i.bill_type as billType, i.bill_number as billNumber, i.title, "
                    + "i.status as instanceStatus, i.current_level as currentLevel, i.create_time as createTime, "
                    + "u.username as approverName, c.username as creatorName "
                    + "from jsh_af_task t join jsh_af_instance i on i.id = t.instance_id "
                    + "left join jsh_user u on u.id = t.approver_id left join jsh_user c on c.id = i.creator "
                    + "where ifnull(t.delete_flag,'0') <> '1' and t.status = ? ";
            a.add(status);
            if (tenantId != null && tenantId != 0L) {
                base += " and t.tenant_id = ? ";
                a.add(tenantId);
            }
            if (approverId != null && approverId != 0L) {
                base += " and t.approver_id = ? ";
                a.add(approverId);
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") x", base + " order by t.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 内部方法 ----------------

    private int getMaxLevel(JdbcTemplate jdbc, Long flowId) {
        Long count = jdbc.queryForObject("select ifnull(max(level),1) from jsh_af_flow_level where flow_id = ? "
                + "and ifnull(delete_flag,'0') <> '1'", Long.class, flowId);
        return count == null ? 1 : count.intValue();
    }

    private Long resolveApprover(JdbcTemplate jdbc, Long flowId, int level, Long creator, Long tenantId) {
        List<Map<String, Object>> levels = jdbc.queryForList("select * from jsh_af_flow_level where flow_id = ? "
                + "and level = ? and ifnull(delete_flag,'0') <> '1' limit 1", flowId, level);
        if (levels.isEmpty()) {
            return null;
        }
        Map<String, Object> cfg = levels.get(0);
        String type = String.valueOf(cfg.get("approver_type"));
        if ("3".equals(type)) {
            return CrudSupport.longVal(cfg.get("approver_id"));
        }
        List<Map<String, Object>> creatorRows = creator == null ? new ArrayList<Map<String, Object>>()
                : jdbc.queryForList("select id, department from jsh_user where id = ?", creator);
        String department = creatorRows.isEmpty() ? null : (String) creatorRows.get(0).get("department");
        if ("1".equals(type)) {
            if (!PluginUtils.isEmpty(department)) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "select id from jsh_user where department = ? and ifnull(delete_flag,'0') <> '1' "
                                + "and (ifnull(leader_flag,'0') = '1' or ismanager = 0) and tenant_id = ? "
                                + "order by id limit 1", department, tenantId == null ? 0L : tenantId);
                if (!rows.isEmpty()) {
                    return CrudSupport.longVal(rows.get(0).get("id"));
                }
            }
        } else if ("2".equals(type)) {
            if (creator != null) {
                List<Map<String, Object>> orgs = jdbc.queryForList("select o.parent_id from jsh_orga_user_rel r "
                        + "join jsh_organization o on o.id = r.orga_id where r.user_id = ? and ifnull(r.delete_flag,'0') <> '1' limit 1",
                        creator);
                if (!orgs.isEmpty() && orgs.get(0).get("parent_id") != null) {
                    List<Map<String, Object>> rows = jdbc.queryForList("select u.id from jsh_orga_user_rel r "
                                    + "join jsh_user u on u.id = r.user_id where r.orga_id = ? "
                                    + "and (ifnull(u.leader_flag,'0') = '1' or u.ismanager = 0) "
                                    + "and ifnull(r.delete_flag,'0') <> '1' and ifnull(u.delete_flag,'0') <> '1' limit 1",
                            orgs.get(0).get("parent_id"));
                    if (!rows.isEmpty()) {
                        return CrudSupport.longVal(rows.get(0).get("id"));
                    }
                }
            }
        }
        // 兜底：同租户内任意负责人
        List<Map<String, Object>> rows = jdbc.queryForList("select id from jsh_user where ifnull(delete_flag,'0') <> '1' "
                        + "and (ifnull(leader_flag,'0') = '1' or ismanager = 0) and tenant_id = ? order by id limit 1",
                tenantId == null ? 0L : tenantId);
        return rows.isEmpty() ? null : CrudSupport.longVal(rows.get(0).get("id"));
    }

    private void createTask(JdbcTemplate jdbc, Long tenantId, Long instanceId, int level, Long approver, Object title) {
        Map<String, Object> task = new LinkedHashMap<String, Object>();
        task.put("instance_id", instanceId);
        task.put("level", level);
        task.put("approver_id", approver);
        task.put("status", "0");
        CrudSupport.insert(jdbc, "jsh_af_task", task, tenantId);
        notify(jdbc, approver, "您有新的待办审核", String.valueOf(title) + "（第 " + level + " 级）待您审核");
    }

    private void notify(JdbcTemplate jdbc, Long userId, String title, String content) {
        if (userId == null) {
            return;
        }
        try {
            jdbc.update("insert into jsh_msg (msg_title, msg_content, create_time, type, user_id, status, tenant_id, delete_Flag) "
                            + "values (?, ?, ?, '审核', ?, '1', (select tenant_id from jsh_user where id = ?), '0')",
                    title, content, new java.util.Date(), userId, userId);
        } catch (Exception ignore) {
            // 消息提醒失败不影响主流程
        }
    }

}

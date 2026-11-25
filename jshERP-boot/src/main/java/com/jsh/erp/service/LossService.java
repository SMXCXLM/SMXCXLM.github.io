package com.jsh.erp.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.datasource.entities.DepotItem;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.LossMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 报损管理（报损出库 + 申请人→CFO→CEO 两级审批）
 * 设计要点：报损单独立存储，CEO 终审通过时才生成 出库/其它 单并重算库存，避免 force_approval_flag=0 下保存即扣库存。
 */
@Service
public class LossService {

    private static final Logger logger = LoggerFactory.getLogger(LossService.class);

    /** 状态：0 待CFO审核 1 待CEO审核 2 已通过 3 已驳回 */
    public static final String STATUS_WAIT_CFO = "0";
    public static final String STATUS_WAIT_CEO = "1";
    public static final String STATUS_PASSED = "2";
    public static final String STATUS_REJECTED = "3";

    @Resource
    private LossMapper lossMapper;

    @Resource
    private UserService userService;

    @Resource
    private DepotItemService depotItemService;

    /* ============================ 查询 ============================ */

    public Map<String, Object> queryList(String status, String keyword, Integer mine,
                                         Integer currentPage, Integer pageSize) throws Exception {
        User user = userService.getCurrentUser();
        Long tenantId = user.getTenantId();
        int rows = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        int page = currentPage == null || currentPage <= 0 ? 1 : currentPage;
        int offset = (page - 1) * rows;
        List<Map<String, Object>> list = lossMapper.selectBills(tenantId, user.getId(), mine, status, keyword, offset, rows);
        int total = lossMapper.countBills(tenantId, user.getId(), mine, status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("rows", list);
        data.put("total", total);
        return data;
    }

    /** 待我审核的报损单 */
    public Map<String, Object> queryPendingToMe() throws Exception {
        User user = userService.getCurrentUser();
        Long tenantId = user.getTenantId();
        Map<String, Object> flow = lossMapper.selectFlow(tenantId);
        List<Map<String, Object>> levels = flow == null ? new ArrayList<>()
                : lossMapper.selectFlowLevels(toLong(flow.get("flowId")));
        List<String> mySteps = new ArrayList<>();
        for (Map<String, Object> level : levels) {
            if (toLong(level.get("approverId")) != null && toLong(level.get("approverId")).equals(user.getId())) {
                int lv = toInt(level.get("level"));
                mySteps.add(lv == 1 ? STATUS_WAIT_CFO : STATUS_WAIT_CEO);
            }
        }
        List<Map<String, Object>> all = new ArrayList<>();
        int total = 0;
        for (String step : mySteps) {
            List<Map<String, Object>> part = lossMapper.selectBills(tenantId, user.getId(), 0, step, "", 0, 200);
            all.addAll(part);
            total += lossMapper.countBills(tenantId, user.getId(), 0, step, "");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("rows", all);
        data.put("total", total);
        return data;
    }

    public Map<String, Object> getInfo(Long id) throws Exception {
        User user = userService.getCurrentUser();
        Map<String, Object> bill = lossMapper.selectBill(id);
        if (bill == null) {
            return null;
        }
        if (!user.getTenantId().equals(toLong(bill.get("tenantId")))) {
            return null;
        }
        Map<String, Object> flow = lossMapper.selectFlow(user.getTenantId());
        List<Map<String, Object>> levels = flow == null ? new ArrayList<>()
                : lossMapper.selectFlowLevels(toLong(flow.get("flowId")));
        Map<String, Object> data = new HashMap<>();
        data.put("bill", bill);
        data.put("items", lossMapper.selectItems(id));
        data.put("logs", lossMapper.selectLogs(id));
        data.put("levels", levels);
        data.put("currentUserId", user.getId());
        String status = toStr(bill.get("status"));
        Integer myLevel = null;
        for (Map<String, Object> level : levels) {
            Long approverId = toLong(level.get("approverId"));
            if (approverId != null && approverId.equals(user.getId())) {
                myLevel = toInt(level.get("level"));
            }
        }
        data.put("myApprovalLevel", myLevel);
        boolean canApprove = (STATUS_WAIT_CFO.equals(status) && myLevel != null && myLevel == 1)
                || (STATUS_WAIT_CEO.equals(status) && myLevel != null && myLevel == 2);
        data.put("canApprove", canApprove);
        return data;
    }

    /* ============================ 保存 ============================ */

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void save(JSONObject obj) throws Exception {
        User user = userService.getCurrentUser();
        Long tenantId = user.getTenantId();
        Long depotId = toLong(obj.get("depotId"));
        if (depotId == null) {
            throw new RuntimeException("请选择报损仓库");
        }
        String reason = toStr(obj.get("reason"));
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("请填写报损原因");
        }
        JSONArray items = obj.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            // 兼容前端把明细放在 rows 里
            items = obj.getJSONArray("rows");
        }
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("请添加报损商品明细");
        }
        String depotName = lossMapper.selectDepotName(depotId);
        if (depotName == null) {
            throw new RuntimeException("报损仓库不存在或已删除");
        }

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<JSONObject> validItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            Long materialId = toLong(item.get("materialId"));
            if (materialId == null) {
                Long extendId = toLong(item.get("materialExtendId"));
                if (extendId != null) {
                    materialId = lossMapper.selectMaterialIdByExtendId(extendId);
                    item.put("materialId", materialId);
                }
            }
            BigDecimal quantity = toDecimal(item.get("quantity"));
            if (materialId == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal unitPrice = toDecimal(item.get("unitPrice"));
            if (unitPrice == null) {
                unitPrice = BigDecimal.ZERO;
            }
            BigDecimal amount = toDecimal(item.get("amount"));
            if (amount == null) {
                amount = quantity.multiply(unitPrice).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            totalQty = totalQty.add(quantity);
            totalAmount = totalAmount.add(amount);
            validItems.add(item);
        }
        if (validItems.isEmpty()) {
            throw new RuntimeException("报损明细数量和金额必须大于 0");
        }

        Map<String, Object> map = new HashMap<>();
        map.put("depotId", depotId);
        map.put("depotName", depotName);
        map.put("reason", reason.trim());
        map.put("remark", toStr(obj.get("remark")));
        map.put("totalQty", totalQty);
        map.put("totalAmount", totalAmount);
        map.put("status", STATUS_WAIT_CFO);
        map.put("tenantId", tenantId);

        Long id = toLong(obj.get("id"));
        boolean isNew = true;
        if (id != null) {
            Map<String, Object> old = lossMapper.selectBill(id);
            if (old == null) {
                throw new RuntimeException("报损单不存在");
            }
            if (!tenantId.equals(toLong(old.get("tenantId")))) {
                throw new RuntimeException("无权操作该报损单");
            }
            String oldStatus = toStr(old.get("status"));
            if (!STATUS_WAIT_CFO.equals(oldStatus) && !STATUS_REJECTED.equals(oldStatus)) {
                throw new RuntimeException("当前状态不允许修改（仅待 CFO 审核或已驳回可改）");
            }
            if (!user.getId().equals(toLong(old.get("applicantId")))) {
                throw new RuntimeException("只能修改自己提交的报损单");
            }
            map.put("id", id);
            lossMapper.updateBillBase(map);
            lossMapper.deleteItems(id);
            isNew = false;
        } else {
            map.put("billNumber", buildBillNumber());
            map.put("applicantId", user.getId());
            map.put("applicantName", user.getUsername());
            lossMapper.insertBill(map);
            id = toLong(map.get("id"));
        }

        for (JSONObject item : validItems) {
            Map<String, Object> row = new HashMap<>();
            Long materialId = toLong(item.get("materialId"));
            row.put("billId", id);
            row.put("materialId", materialId);
            Long extendId = toLong(item.get("materialExtendId"));
            if (extendId == null) {
                extendId = lossMapper.selectDefaultExtendId(materialId);
            }
            row.put("materialExtendId", extendId);
            row.put("materialName", toStr(item.get("materialName")));
            row.put("materialModel", toStr(item.get("materialModel")));
            row.put("unit", toStr(item.get("unit")) == null ? "" : toStr(item.get("unit")));
            BigDecimal quantity = toDecimal(item.get("quantity"));
            BigDecimal unitPrice = toDecimal(item.get("unitPrice"));
            if (unitPrice == null) {
                unitPrice = BigDecimal.ZERO;
            }
            BigDecimal amount = toDecimal(item.get("amount"));
            if (amount == null) {
                amount = quantity.multiply(unitPrice).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            row.put("quantity", quantity);
            row.put("basicNumber", quantity);
            row.put("unitPrice", unitPrice);
            row.put("amount", amount);
            row.put("stockNumber", toDecimal(item.get("stockNumber")));
            row.put("remark", toStr(item.get("remark")));
            row.put("tenantId", tenantId);
            lossMapper.insertItem(row);
        }

        Map<String, Object> log = new HashMap<>();
        log.put("billId", id);
        log.put("step", isNew ? "提交" : "重新提交");
        log.put("action", "submit");
        log.put("operatorId", user.getId());
        log.put("operatorName", user.getUsername());
        log.put("comment", reason.trim());
        log.put("tenantId", tenantId);
        lossMapper.insertLog(log);
    }

    /* ============================ 审批 ============================ */

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void approve(JSONObject obj) throws Exception {
        User user = userService.getCurrentUser();
        Long tenantId = user.getTenantId();
        Long id = toLong(obj.get("id"));
        if (id == null) {
            throw new RuntimeException("报损单 id 不能为空");
        }
        Map<String, Object> bill = lossMapper.selectBill(id);
        if (bill == null || !tenantId.equals(toLong(bill.get("tenantId")))) {
            throw new RuntimeException("报损单不存在或无权操作");
        }
        String status = toStr(bill.get("status"));
        if (!STATUS_WAIT_CFO.equals(status) && !STATUS_WAIT_CEO.equals(status)) {
            throw new RuntimeException("该报损单已处理完毕，不能重复审批");
        }
        int level = STATUS_WAIT_CFO.equals(status) ? 1 : 2;
        Map<String, Object> flow = lossMapper.selectFlow(tenantId);
        if (flow == null) {
            throw new RuntimeException("未配置报损出库审批流");
        }
        List<Map<String, Object>> levels = lossMapper.selectFlowLevels(toLong(flow.get("flowId")));
        Map<String, Object> myLevelCfg = null;
        for (Map<String, Object> cfg : levels) {
            if (toInt(cfg.get("level")) == level) {
                myLevelCfg = cfg;
            }
        }
        if (myLevelCfg == null) {
            throw new RuntimeException("审批流第 " + level + " 级未配置审核人");
        }
        Long approverId = toLong(myLevelCfg.get("approverId"));
        if (approverId == null || !approverId.equals(user.getId())) {
            throw new RuntimeException("第 " + level + " 级审核人为 " + toStr(myLevelCfg.get("approverName")) + "，当前账号无权审批");
        }

        String action = toStr(obj.get("action"));
        String comment = toStr(obj.get("comment"));
        if (comment == null) {
            comment = "";
        }
        if ("reject".equals(action)) {
            lossMapper.updateReject(id);
            writeLog(id, level == 1 ? "CFO审核" : "CEO终审", "reject", user, comment, tenantId);
            return;
        }
        if (!"pass".equals(action)) {
            throw new RuntimeException("未知审批动作：" + action);
        }

        if (level == 1) {
            lossMapper.updateCfoPass(id, user.getId(), user.getUsername(), comment);
            writeLog(id, "CFO审核", "pass", user, comment, tenantId);
        } else {
            // CEO 终审通过：生成 出库/其它 单并扣减库存
            List<Map<String, Object>> items = lossMapper.selectItems(id);
            if (items.isEmpty()) {
                throw new RuntimeException("报损单没有明细，无法生成出库单");
            }
            String headNumber = buildDepotHeadNumber();
            Map<String, Object> head = new HashMap<>();
            head.put("number", headNumber);
            head.put("creator", toLong(bill.get("applicantId")));
            head.put("remark", "报损出库 " + toStr(bill.get("billNumber")) + "：" + toStr(bill.get("reason")));
            head.put("totalAmount", toDecimal(bill.get("totalAmount")));
            head.put("tenantId", tenantId);
            lossMapper.insertDepotHead(head);
            Long headId = toLong(head.get("id"));

            for (Map<String, Object> item : items) {
                Map<String, Object> row = new HashMap<>();
                row.put("headerId", headId);
                row.put("materialId", toLong(item.get("materialId")));
                row.put("materialExtendId", toLong(item.get("materialExtendId")));
                row.put("materialUnit", toStr(item.get("unit")) == null ? "" : toStr(item.get("unit")));
                row.put("operNumber", toDecimal(item.get("quantity")));
                row.put("basicNumber", toDecimal(item.get("basicNumber")));
                row.put("unitPrice", toDecimal(item.get("unitPrice")));
                row.put("allPrice", toDecimal(item.get("amount")));
                row.put("remark", "报损出库：" + (toStr(bill.get("billNumber"))));
                row.put("depotId", toLong(bill.get("depotId")));
                row.put("tenantId", tenantId);
                lossMapper.insertDepotItem(row);

                // 重算当前库存（终审后才真正扣减）
                DepotItem depotItem = new DepotItem();
                depotItem.setMaterialId(toLong(item.get("materialId")));
                depotItem.setDepotId(toLong(bill.get("depotId")));
                depotItem.setTenantId(tenantId);
                depotItemService.updateCurrentStock(depotItem);
            }

            lossMapper.updateCeoPass(id, user.getId(), user.getUsername(), comment, headId, headNumber);
            writeLog(id, "CEO终审", "pass", user, comment, tenantId);
            logger.info("报损单 {} 终审通过，已生成出库单 {}", bill.get("billNumber"), headNumber);
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void delete(Long id) throws Exception {
        User user = userService.getCurrentUser();
        Map<String, Object> bill = lossMapper.selectBill(id);
        if (bill == null || !user.getTenantId().equals(toLong(bill.get("tenantId")))) {
            throw new RuntimeException("报损单不存在或无权操作");
        }
        String status = toStr(bill.get("status"));
        if (STATUS_PASSED.equals(status)) {
            throw new RuntimeException("已通过的单据不能删除");
        }
        if (!user.getId().equals(toLong(bill.get("applicantId")))) {
            throw new RuntimeException("只能删除自己提交的报损单");
        }
        lossMapper.deleteBill(id);
    }

    /* ============================ 工具方法 ============================ */

    private void writeLog(Long billId, String step, String action, User user, String comment, Long tenantId) {
        Map<String, Object> log = new HashMap<>();
        log.put("billId", billId);
        log.put("step", step);
        log.put("action", action);
        log.put("operatorId", user.getId());
        log.put("operatorName", user.getUsername());
        log.put("comment", comment);
        log.put("tenantId", tenantId);
        lossMapper.insertLog(log);
    }

    private String buildBillNumber() {
        String no = "BS" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return no + String.format("%02d", new Random().nextInt(100));
    }

    private String buildDepotHeadNumber() {
        for (int i = 0; i < 5; i++) {
            String no = "BS" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                    + String.format("%02d", new Random().nextInt(100));
            if (lossMapper.countDepotHeadByNumber(no) == 0) {
                return no;
            }
        }
        return "BS" + System.currentTimeMillis();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).longValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object value) {
        Long l = toLong(value);
        return l == null ? null : l.intValue();
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toStr(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return "null".equalsIgnoreCase(s) ? null : s;
    }
}


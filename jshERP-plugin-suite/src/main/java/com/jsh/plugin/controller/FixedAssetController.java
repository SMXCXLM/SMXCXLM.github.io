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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 固定资产：类别设置（折旧期限/残值率）、资产台账、年限平均法折旧、使用人历史。
 */
@RestController
@RequestMapping("/fixedAsset")
@Api(tags = {"固定资产插件"})
public class FixedAssetController extends BasePluginController {

    @GetMapping("/asset/list")
    @ApiOperation(value = "资产台账")
    public BaseResponseInfo assetList(@RequestParam(value = "keyword", required = false) String keyword,
                                      @RequestParam(value = "categoryId", required = false) Long categoryId,
                                      @RequestParam(value = "status", required = false) String status,
                                      @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                      @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                      HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select a.*, c.name as categoryName, u.username as currentUserName from jsh_fa_asset a "
                    + "left join jsh_fa_category c on c.id = a.category_id left join jsh_user u on u.id = a.current_user_id "
                    + "where ifnull(a.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and a.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(keyword)) {
                base += " and (a.asset_no like ? or a.name like ? or a.remark like ?) ";
                String like = PluginUtils.like(keyword);
                for (int i = 0; i < 3; i++) {
                    a.add(like);
                }
            }
            if (categoryId != null) {
                base += " and a.category_id = ? ";
                a.add(categoryId);
            }
            if (!PluginUtils.isEmpty(status)) {
                base += " and a.status = ? ";
                a.add(status.trim());
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t", base + " order by a.id desc",
                    a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/asset/save")
    @ApiOperation(value = "保存资产")
    public BaseResponseInfo assetSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long id = CrudSupport.longVal(body.get("id"));
            // 未填折旧期限/残值率时，按类别带出
            Long categoryId = CrudSupport.longVal(body.get("category_id"));
            if (categoryId != null && (body.get("useful_life") == null || body.get("residual_rate") == null)) {
                List<Map<String, Object>> cats = jdbc.queryForList("select * from jsh_fa_category where id = ?", categoryId);
                if (!cats.isEmpty()) {
                    if (body.get("useful_life") == null) {
                        body.put("useful_life", cats.get(0).get("useful_life"));
                    }
                    if (body.get("residual_rate") == null) {
                        body.put("residual_rate", cats.get(0).get("residual_rate"));
                    }
                }
            }
            if (id == null) {
                if (PluginUtils.isEmpty(String.valueOf(body.get("asset_no")))) {
                    body.put("asset_no", "ZC" + System.currentTimeMillis());
                }
                CrudSupport.insert(jdbc, "jsh_fa_asset", body, tenantId);
                Long assetId = CrudSupport.longVal(body.get("id"));
                Long userId = CrudSupport.longVal(body.get("current_user_id"));
                if (userId != null) {
                    changeUserLog(jdbc, tenantId, assetId, userId, String.valueOf(body.get("current_user_name")),
                            body.get("buy_time"), null);
                }
            } else {
                CrudSupport.update(jdbc, "jsh_fa_asset", id, body);
            }
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/asset/delete")
    @ApiOperation(value = "删除资产")
    public BaseResponseInfo assetDelete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_fa_asset", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/asset/changeUser")
    @ApiOperation(value = "变更使用人（记录历史）")
    public BaseResponseInfo changeUser(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long assetId = CrudSupport.longVal(body.get("asset_id"));
            Long userId = CrudSupport.longVal(body.get("user_id"));
            if (assetId == null || userId == null) {
                return PluginUtils.fail("缺少资产或使用人");
            }
            String userName = body.get("user_name") == null ? "" : String.valueOf(body.get("user_name"));
            if (PluginUtils.isEmpty(userName)) {
                List<Map<String, Object>> users = jdbc.queryForList("select username from jsh_user where id = ?", userId);
                userName = users.isEmpty() ? "" : String.valueOf(users.get(0).get("username"));
            }
            // 结束上一条使用记录
            jdbc.update("update jsh_fa_user_log set end_time = ? where asset_id = ? and end_time is null "
                    + "and ifnull(delete_flag,'0') <> '1'", new java.util.Date(), assetId);
            changeUserLog(jdbc, tenantId, assetId, userId, userName, new java.util.Date(), null);
            jdbc.update("update jsh_fa_asset set current_user_id = ?, status = '在用' where id = ?", userId, assetId);
            return PluginUtils.ok("使用人已变更");
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/asset/users")
    @ApiOperation(value = "资产历史使用人")
    public BaseResponseInfo users(@RequestParam("assetId") Long assetId) {
        try {
            return PluginUtils.ok(jdbc().queryForList("select l.*, u.username as userName from jsh_fa_user_log l "
                    + "left join jsh_user u on u.id = l.user_id where l.asset_id = ? "
                    + "and ifnull(l.delete_flag,'0') <> '1' order by l.id desc", assetId));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/asset/depreciation")
    @ApiOperation(value = "年限平均法折旧表（按期间）")
    public BaseResponseInfo depreciation(@RequestParam("period") String period,
                                         @RequestParam(value = "categoryId", required = false) Long categoryId,
                                         HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select a.*, c.name as categoryName from jsh_fa_asset a "
                    + "left join jsh_fa_category c on c.id = a.category_id "
                    + "where ifnull(a.delete_flag,'0') <> '1' and ifnull(a.status,'') <> '报废' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and a.tenant_id = ? ";
                a.add(tenantId);
            }
            if (categoryId != null) {
                sql += " and a.category_id = ? ";
                a.add(categoryId);
            }
            List<Map<String, Object>> assets = jdbc.queryForList(sql + " order by a.id", a.toArray());
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM");
            java.util.Calendar current = java.util.Calendar.getInstance();
            current.setTime(fmt.parse(period));
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            BigDecimal totalDep = BigDecimal.ZERO;
            BigDecimal totalNet = BigDecimal.ZERO;
            for (Map<String, Object> asset : assets) {
                BigDecimal price = PluginUtils.decimal(asset.get("price"));
                BigDecimal residualRate = PluginUtils.decimal(asset.get("residual_rate"));
                int life = PluginUtils.intValue(String.valueOf(asset.get("useful_life")), 0);
                if (life <= 0 || price.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                java.util.Date buyTime = asset.get("buy_time") instanceof java.util.Date
                        ? (java.util.Date) asset.get("buy_time") : null;
                int months = 0;
                if (buyTime != null) {
                    java.util.Calendar buy = java.util.Calendar.getInstance();
                    buy.setTime(buyTime);
                    months = (current.get(java.util.Calendar.YEAR) - buy.get(java.util.Calendar.YEAR)) * 12
                            + (current.get(java.util.Calendar.MONTH) - buy.get(java.util.Calendar.MONTH));
                    // 当月购置次月起计提
                    months = months < 1 ? 0 : months;
                }
                months = Math.min(months, life);
                BigDecimal monthly = price.subtract(price.multiply(residualRate)
                                .divide(new BigDecimal("100"), 6, BigDecimal.ROUND_HALF_UP))
                        .divide(new BigDecimal(life), 2, BigDecimal.ROUND_HALF_UP);
                BigDecimal accumulated = monthly.multiply(new BigDecimal(months)).setScale(2, BigDecimal.ROUND_HALF_UP);
                BigDecimal net = price.subtract(accumulated).setScale(2, BigDecimal.ROUND_HALF_UP);
                Map<String, Object> row = new LinkedHashMap<String, Object>(asset);
                row.put("monthlyDepreciation", monthly);
                row.put("usedMonths", months);
                row.put("accumulatedDepreciation", accumulated);
                row.put("netValue", net);
                row.put("residualValue", price.multiply(residualRate)
                        .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP));
                rows.add(row);
                totalDep = totalDep.add(accumulated);
                totalNet = totalNet.add(net);
            }
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("rows", rows);
            data.put("total", rows.size());
            data.put("totalDepreciation", totalDep);
            data.put("totalNetValue", totalNet);
            data.put("period", period);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    private void changeUserLog(JdbcTemplate jdbc, Long tenantId, Long assetId, Long userId, String userName,
                               Object startTime, Object endTime) {
        Map<String, Object> log = new LinkedHashMap<String, Object>();
        log.put("asset_id", assetId);
        log.put("user_id", userId);
        log.put("user_name", userName);
        log.put("start_time", startTime == null ? new java.util.Date() : startTime);
        log.put("end_time", endTime);
        CrudSupport.insert(jdbc, "jsh_fa_user_log", log, tenantId);
    }

}

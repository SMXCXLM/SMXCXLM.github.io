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
 * 总账管理：账套、科目、凭证、结账、账簿（明细账/总账/科目余额/科目汇总/项目账）、报表（利润表/现金流量表/资产负债表）。
 */
@RestController
@RequestMapping("/ledger")
@Api(tags = {"总账管理插件"})
public class LedgerController extends BasePluginController {

    // ---------------- 账套 ----------------

    @GetMapping("/set/list")
    @ApiOperation(value = "账套列表")
    public BaseResponseInfo setList(HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            String sql = "select * from jsh_lg_account_set where ifnull(delete_flag,'0') <> '1'";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = " + tenantId;
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by id desc"));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/set/save")
    @ApiOperation(value = "保存账套")
    public BaseResponseInfo setSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long id = CrudSupport.longVal(body.get("id"));
            if (PluginUtils.isEmpty(String.valueOf(body.get("current_period")))) {
                body.put("current_period", PluginUtils.isEmpty(String.valueOf(body.get("start_period")))
                        ? new java.text.SimpleDateFormat("yyyy-MM").format(new java.util.Date())
                        : body.get("start_period"));
            }
            if (id == null) {
                CrudSupport.insert(jdbc, "jsh_lg_account_set", body, tenantId);
            } else {
                CrudSupport.update(jdbc, "jsh_lg_account_set", id, body);
            }
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/set/delete")
    @ApiOperation(value = "删除账套")
    public BaseResponseInfo setDelete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_lg_account_set", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 科目 ----------------

    @GetMapping("/subject/list")
    @ApiOperation(value = "科目列表")
    public BaseResponseInfo subjectList(@RequestParam(value = "setId", required = false) Long setId,
                                        @RequestParam(value = "keyword", required = false) String keyword,
                                        HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select * from jsh_lg_subject where ifnull(delete_flag,'0') <> '1' "
                    + "and ifnull(enabled,'1') <> '0' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            if (setId != null) {
                sql += " and set_id = ? ";
                a.add(setId);
            }
            if (!PluginUtils.isEmpty(keyword)) {
                sql += " and (code like ? or name like ?) ";
                String like = PluginUtils.like(keyword);
                a.add(like);
                a.add(like);
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by code", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/subject/save")
    @ApiOperation(value = "保存科目")
    public BaseResponseInfo subjectSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                CrudSupport.insert(jdbc, "jsh_lg_subject", body, tenant(request));
            } else {
                CrudSupport.update(jdbc, "jsh_lg_subject", id, body);
            }
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/subject/delete")
    @ApiOperation(value = "删除科目")
    public BaseResponseInfo subjectDelete(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            CrudSupport.softDelete(jdbc(), "jsh_lg_subject", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/subject/init")
    @ApiOperation(value = "按企业会计准则预置常用科目")
    public BaseResponseInfo subjectInit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long setId = CrudSupport.longVal(body.get("setId"));
            if (setId == null) {
                return PluginUtils.fail("缺少账套id");
            }
            Long exists = jdbc.queryForObject("select count(*) from jsh_lg_subject where set_id = ? "
                    + "and ifnull(delete_flag,'0') <> '1'", Long.class, setId);
            if (exists != null && exists > 0) {
                return PluginUtils.ok("该账套已存在科目，未重复初始化");
            }
            String[][] defaults = {
                    {"1001", "库存现金", "资产", "借"}, {"1002", "银行存款", "资产", "借"},
                    {"1122", "应收账款", "资产", "借"}, {"1123", "预付账款", "资产", "借"},
                    {"1405", "库存商品", "资产", "借"}, {"1403", "原材料", "资产", "借"},
                    {"1601", "固定资产", "资产", "借"}, {"1602", "累计折旧", "资产", "贷"},
                    {"2202", "应付账款", "负债", "贷"}, {"2211", "应付职工薪酬", "负债", "贷"},
                    {"2221", "应交税费", "负债", "贷"}, {"4001", "实收资本", "权益", "贷"},
                    {"4103", "本年利润", "权益", "贷"}, {"4104", "利润分配", "权益", "贷"},
                    {"5001", "生产成本", "成本", "借"}, {"5101", "制造费用", "成本", "借"},
                    {"6001", "主营业务收入", "损益", "贷"}, {"6051", "其他业务收入", "损益", "贷"},
                    {"6301", "营业外收入", "损益", "贷"}, {"6401", "主营业务成本", "损益", "借"},
                    {"6402", "其他业务成本", "损益", "借"}, {"6403", "税金及附加", "损益", "借"},
                    {"6601", "销售费用", "损益", "借"}, {"6602", "管理费用", "损益", "借"},
                    {"6603", "财务费用", "损益", "借"}, {"6711", "营业外支出", "损益", "借"},
                    {"6801", "所得税费用", "损益", "借"}
            };
            for (String[] s : defaults) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("set_id", setId);
                row.put("code", s[0]);
                row.put("name", s[1]);
                row.put("category", s[2]);
                row.put("direction", s[3]);
                row.put("level", s[0].length() <= 4 ? 1 : 2);
                row.put("is_detail", "1");
                row.put("enabled", "1");
                CrudSupport.insert(jdbc, "jsh_lg_subject", row, tenantId);
            }
            return PluginUtils.ok("已预置 " + defaults.length + " 个科目");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 凭证 ----------------

    @GetMapping("/voucher/list")
    @ApiOperation(value = "凭证列表")
    public BaseResponseInfo voucherList(@RequestParam(value = "setId", required = false) Long setId,
                                        @RequestParam(value = "period", required = false) String period,
                                        @RequestParam(value = "keyword", required = false) String keyword,
                                        @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                        @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
                                        HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select v.*, u.username as creatorName, "
                    + "(select round(sum(e.debit),2) from jsh_lg_voucher_entry e where e.voucher_id = v.id and ifnull(e.delete_flag,'0') <> '1') as debitSum, "
                    + "(select count(*) from jsh_lg_voucher_entry e where e.voucher_id = v.id and ifnull(e.delete_flag,'0') <> '1') as entryCount "
                    + "from jsh_lg_voucher v left join jsh_user u on u.id = v.creator "
                    + "where ifnull(v.delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                base += " and v.tenant_id = ? ";
                a.add(tenantId);
            }
            if (setId != null) {
                base += " and v.set_id = ? ";
                a.add(setId);
            }
            if (!PluginUtils.isEmpty(period)) {
                base += " and v.period = ? ";
                a.add(period.trim());
            }
            if (!PluginUtils.isEmpty(keyword)) {
                base += " and (v.remark like ? or exists (select 1 from jsh_lg_voucher_entry e where e.voucher_id = v.id and (e.summary like ? or e.subject_name like ?))) ";
                String like = PluginUtils.like(keyword);
                a.add(like);
                a.add(like);
                a.add(like);
            }
            return PluginUtils.ok(page("select count(*) from (" + base + ") t",
                    base + " order by v.period desc, v.voucher_no desc", a, currentPage, pageSize));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/voucher/get")
    @ApiOperation(value = "凭证详情")
    public BaseResponseInfo voucherGet(@RequestParam("id") Long id) {
        try {
            JdbcTemplate jdbc = jdbc();
            List<Map<String, Object>> head = jdbc.queryForList("select * from jsh_lg_voucher where id = ?", id);
            List<Map<String, Object>> entries = jdbc.queryForList("select * from jsh_lg_voucher_entry "
                    + "where voucher_id = ? and ifnull(delete_flag,'0') <> '1' order by id", id);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("voucher", head.isEmpty() ? null : head.get(0));
            data.put("entries", entries);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/voucher/save")
    @ApiOperation(value = "保存凭证（借贷必须平衡）")
    @SuppressWarnings("unchecked")
    public BaseResponseInfo voucherSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long setId = CrudSupport.longVal(body.get("set_id"));
            if (setId == null) {
                return PluginUtils.fail("缺少账套");
            }
            List<Object> rawEntries = body.get("entries") instanceof List
                    ? (List<Object>) body.get("entries") : new ArrayList<Object>();
            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;
            for (Object o : rawEntries) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<String, Object> e = (Map<String, Object>) o;
                debit = debit.add(PluginUtils.decimal(e.get("debit")));
                credit = credit.add(PluginUtils.decimal(e.get("credit")));
            }
            if (debit.compareTo(credit) != 0) {
                return PluginUtils.fail("借贷不平衡：借方 " + debit + "，贷方 " + credit);
            }
            if (debit.compareTo(BigDecimal.ZERO) == 0) {
                return PluginUtils.fail("凭证金额不能为 0");
            }
            Long id = CrudSupport.longVal(body.get("id"));
            if (PluginUtils.isEmpty(String.valueOf(body.get("period")))) {
                body.put("period", new java.text.SimpleDateFormat("yyyy-MM")
                        .format(body.get("voucher_date") == null ? new java.util.Date() : body.get("voucher_date")));
            }
            if (id == null) {
                if (PluginUtils.isEmpty(String.valueOf(body.get("voucher_word")))) {
                    body.put("voucher_word", "记");
                }
                Integer maxNo = jdbc.queryForObject("select ifnull(max(voucher_no),0) from jsh_lg_voucher "
                        + "where set_id = ? and period = ? and ifnull(delete_flag,'0') <> '1'",
                        Integer.class, setId, body.get("period"));
                body.put("voucher_no", maxNo == null ? 1 : maxNo + 1);
                CrudSupport.insert(jdbc, "jsh_lg_voucher", body, tenantId);
                id = CrudSupport.longVal(body.get("id"));
            } else {
                CrudSupport.update(jdbc, "jsh_lg_voucher", id, body);
                jdbc.update("update jsh_lg_voucher_entry set delete_flag = '1' where voucher_id = ?", id);
            }
            for (Object o : rawEntries) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<String, Object> e = new LinkedHashMap<String, Object>((Map<String, Object>) o);
                e.remove("id");
                e.put("voucher_id", id);
                e.put("set_id", setId);
                if (e.get("debit") == null) {
                    e.put("debit", BigDecimal.ZERO);
                }
                if (e.get("credit") == null) {
                    e.put("credit", BigDecimal.ZERO);
                }
                CrudSupport.insert(jdbc, "jsh_lg_voucher_entry", e, tenantId);
            }
            body.put("id", id);
            return PluginUtils.ok(body);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/voucher/audit")
    @ApiOperation(value = "审核/反审核凭证")
    public BaseResponseInfo voucherAudit(@RequestBody Map<String, Object> body) {
        try {
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            String status = "1".equals(String.valueOf(body.get("audit_status"))) ? "1" : "0";
            jdbc().update("update jsh_lg_voucher set audit_status = ? where id = ?", status, id);
            return PluginUtils.ok(status);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/voucher/delete")
    @ApiOperation(value = "删除凭证")
    public BaseResponseInfo voucherDelete(@RequestBody Map<String, Object> body) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long id = CrudSupport.longVal(body.get("id"));
            if (id == null) {
                return PluginUtils.fail("缺少 id");
            }
            List<Map<String, Object>> rows = jdbc.queryForList("select audit_status from jsh_lg_voucher where id = ?", id);
            if (!rows.isEmpty() && "1".equals(String.valueOf(rows.get(0).get("audit_status")))) {
                return PluginUtils.fail("凭证已审核，请先反审核");
            }
            CrudSupport.softDelete(jdbc, "jsh_lg_voucher", id);
            jdbc.update("update jsh_lg_voucher_entry set delete_flag = '1' where voucher_id = ?", id);
            return PluginUtils.ok("删除成功");
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 结账 ----------------

    @GetMapping("/period/list")
    @ApiOperation(value = "期间列表")
    public BaseResponseInfo periodList(@RequestParam(value = "setId", required = false) Long setId,
                                       HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select * from jsh_lg_period where ifnull(delete_flag,'0') <> '1' ";
            if (tenantId != null && tenantId != 0L) {
                sql += " and tenant_id = ? ";
                a.add(tenantId);
            }
            if (setId != null) {
                sql += " and set_id = ? ";
                a.add(setId);
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by period desc", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/period/close")
    @ApiOperation(value = "期间结账")
    public BaseResponseInfo periodClose(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            Long setId = CrudSupport.longVal(body.get("set_id"));
            String period = PluginUtils.trim(String.valueOf(body.get("period")));
            if (setId == null || PluginUtils.isEmpty(period)) {
                return PluginUtils.fail("缺少账套或期间");
            }
            Long unaudited = jdbc.queryForObject("select count(*) from jsh_lg_voucher where set_id = ? and period = ? "
                    + "and ifnull(delete_flag,'0') <> '1' and ifnull(audit_status,'0') <> '1'", Long.class, setId, period);
            if (unaudited != null && unaudited > 0) {
                return PluginUtils.fail("本期还有 " + unaudited + " 张未审核凭证，不能结账");
            }
            List<Map<String, Object>> rows = jdbc.queryForList("select * from jsh_lg_period where set_id = ? and period = ? "
                    + "and ifnull(delete_flag,'0') <> '1'", setId, period);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("set_id", setId);
            row.put("period", period);
            row.put("status", "1");
            row.put("close_time", new java.util.Date());
            row.put("closer", CrudSupport.longVal(body.get("closer")));
            if (rows.isEmpty()) {
                CrudSupport.insert(jdbc, "jsh_lg_period", row, tenantId);
            } else {
                CrudSupport.update(jdbc, "jsh_lg_period", CrudSupport.longVal(rows.get(0).get("id")), row);
            }
            String next = nextPeriod(period);
            jdbc.update("update jsh_lg_account_set set current_period = ? where id = ?", next, setId);
            return PluginUtils.ok("已结账，当前期间推进到 " + next);
        } catch (Exception e) {
            return error(e);
        }
    }

    @PostMapping("/period/open")
    @ApiOperation(value = "反结账")
    public BaseResponseInfo periodOpen(@RequestBody Map<String, Object> body) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long setId = CrudSupport.longVal(body.get("set_id"));
            String period = PluginUtils.trim(String.valueOf(body.get("period")));
            jdbc.update("update jsh_lg_period set status = '0' where set_id = ? and period = ?", setId, period);
            jdbc.update("update jsh_lg_account_set set current_period = ? where id = ?", period, setId);
            return PluginUtils.ok("已反结账至 " + period);
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 账簿 ----------------

    @GetMapping("/book/detail")
    @ApiOperation(value = "明细账")
    public BaseResponseInfo bookDetail(@RequestParam("setId") Long setId,
                                       @RequestParam(value = "subjectCode", required = false) String subjectCode,
                                       @RequestParam(value = "beginPeriod", required = false) String beginPeriod,
                                       @RequestParam(value = "endPeriod", required = false) String endPeriod,
                                       @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                       @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
                                       HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String base = "select e.id, v.voucher_date as voucherDate, v.voucher_word as voucherWord, "
                    + "v.voucher_no as voucherNo, v.audit_status as auditStatus, e.summary, e.subject_code as subjectCode, "
                    + "e.subject_name as subjectName, e.debit, e.credit, e.project, e.aux_info as auxInfo "
                    + "from jsh_lg_voucher_entry e join jsh_lg_voucher v on v.id = e.voucher_id "
                    + "where ifnull(e.delete_flag,'0') <> '1' and ifnull(v.delete_flag,'0') <> '1' and e.set_id = ? ";
            a.add(setId);
            if (tenantId != null && tenantId != 0L) {
                base += " and e.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(subjectCode)) {
                base += " and (e.subject_code = ? or e.subject_code like ?) ";
                a.add(subjectCode.trim());
                a.add(subjectCode.trim() + "%");
            }
            if (!PluginUtils.isEmpty(beginPeriod)) {
                base += " and v.period >= ? ";
                a.add(beginPeriod.trim());
            }
            if (!PluginUtils.isEmpty(endPeriod)) {
                base += " and v.period <= ? ";
                a.add(endPeriod.trim());
            }
            Map<String, Object> data = page("select count(*) from (" + base + ") t",
                    base + " order by v.voucher_date, v.voucher_no, e.id", a, currentPage, pageSize);
            // 期初余额（截止 beginPeriod 之前）
            BigDecimal opening = BigDecimal.ZERO;
            if (!PluginUtils.isEmpty(beginPeriod) && !PluginUtils.isEmpty(subjectCode)) {
                List<Object> oa = new ArrayList<Object>();
                String sql = "select ifnull(sum(e.debit),0) d, ifnull(sum(e.credit),0) c from jsh_lg_voucher_entry e "
                        + "join jsh_lg_voucher v on v.id = e.voucher_id where e.set_id = ? and v.period < ? "
                        + "and (e.subject_code = ? or e.subject_code like ?) "
                        + "and ifnull(e.delete_flag,'0') <> '1' and ifnull(v.delete_flag,'0') <> '1'";
                oa.add(setId);
                oa.add(beginPeriod.trim());
                oa.add(subjectCode.trim());
                oa.add(subjectCode.trim() + "%");
                if (tenantId != null && tenantId != 0L) {
                    sql += " and e.tenant_id = ? ";
                    oa.add(tenantId);
                }
                List<Map<String, Object>> rows = jdbc().queryForList(sql, oa.toArray());
                if (!rows.isEmpty()) {
                    opening = PluginUtils.decimal(rows.get(0).get("d")).subtract(PluginUtils.decimal(rows.get(0).get("c")));
                }
            }
            data.put("opening", opening);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/book/general")
    @ApiOperation(value = "总账")
    public BaseResponseInfo bookGeneral(@RequestParam("setId") Long setId,
                                       @RequestParam(value = "beginPeriod", required = false) String beginPeriod,
                                       @RequestParam(value = "endPeriod", required = false) String endPeriod,
                                       @RequestParam(value = "keyword", required = false) String keyword,
                                       @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                       @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
                                       HttpServletRequest request) {
        try {
            return PluginUtils.ok(subjectBalanceInternal(setId, beginPeriod, endPeriod, keyword,
                    currentPage, pageSize, tenant(request), true));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/book/subjectBalance")
    @ApiOperation(value = "科目余额表")
    public BaseResponseInfo bookSubjectBalance(@RequestParam("setId") Long setId,
                                              @RequestParam(value = "beginPeriod", required = false) String beginPeriod,
                                              @RequestParam(value = "endPeriod", required = false) String endPeriod,
                                              @RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
                                              @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
                                              HttpServletRequest request) {
        try {
            return PluginUtils.ok(subjectBalanceInternal(setId, beginPeriod, endPeriod, keyword,
                    currentPage, pageSize, tenant(request), true));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/book/subjectSummary")
    @ApiOperation(value = "科目汇总表")
    public BaseResponseInfo bookSubjectSummary(@RequestParam("setId") Long setId,
                                              @RequestParam(value = "beginPeriod", required = false) String beginPeriod,
                                              @RequestParam(value = "endPeriod", required = false) String endPeriod,
                                              @RequestParam(value = "keyword", required = false) String keyword,
                                              HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select e.subject_code as subjectCode, max(e.subject_name) as subjectName, "
                    + "round(sum(e.debit),2) as debit, round(sum(e.credit),2) as credit, count(*) as entryCount "
                    + "from jsh_lg_voucher_entry e join jsh_lg_voucher v on v.id = e.voucher_id "
                    + "where e.set_id = ? and ifnull(e.delete_flag,'0') <> '1' and ifnull(v.delete_flag,'0') <> '1' ";
            a.add(setId);
            if (tenantId != null && tenantId != 0L) {
                sql += " and e.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(beginPeriod)) {
                sql += " and v.period >= ? ";
                a.add(beginPeriod.trim());
            }
            if (!PluginUtils.isEmpty(endPeriod)) {
                sql += " and v.period <= ? ";
                a.add(endPeriod.trim());
            }
            if (!PluginUtils.isEmpty(keyword)) {
                sql += " and (e.subject_code like ? or e.subject_name like ?) ";
                String like = PluginUtils.like(keyword);
                a.add(like);
                a.add(like);
            }
            List<Map<String, Object>> rows = jdbc().queryForList(sql
                    + " group by e.subject_code order by e.subject_code", a.toArray());
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            for (Map<String, Object> row : rows) {
                totalDebit = totalDebit.add(PluginUtils.decimal(row.get("debit")));
                totalCredit = totalCredit.add(PluginUtils.decimal(row.get("credit")));
            }
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("rows", rows);
            data.put("total", rows.size());
            data.put("totalDebit", totalDebit);
            data.put("totalCredit", totalCredit);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/book/projectDetail")
    @ApiOperation(value = "项目明细账")
    public BaseResponseInfo bookProjectDetail(@RequestParam("setId") Long setId,
                                             @RequestParam(value = "project", required = false) String project,
                                             @RequestParam(value = "beginPeriod", required = false) String beginPeriod,
                                             @RequestParam(value = "endPeriod", required = false) String endPeriod,
                                             HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select e.project, e.subject_code as subjectCode, e.subject_name as subjectName, e.summary, "
                    + "v.voucher_date as voucherDate, v.voucher_no as voucherNo, e.debit, e.credit "
                    + "from jsh_lg_voucher_entry e join jsh_lg_voucher v on v.id = e.voucher_id "
                    + "where e.set_id = ? and ifnull(e.delete_flag,'0') <> '1' and ifnull(v.delete_flag,'0') <> '1' "
                    + "and ifnull(e.project,'') <> '' ";
            a.add(setId);
            if (tenantId != null && tenantId != 0L) {
                sql += " and e.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(project)) {
                sql += " and e.project like ? ";
                a.add(PluginUtils.like(project));
            }
            if (!PluginUtils.isEmpty(beginPeriod)) {
                sql += " and v.period >= ? ";
                a.add(beginPeriod.trim());
            }
            if (!PluginUtils.isEmpty(endPeriod)) {
                sql += " and v.period <= ? ";
                a.add(endPeriod.trim());
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " order by v.voucher_date, e.id", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/book/projectBalance")
    @ApiOperation(value = "项目余额表")
    public BaseResponseInfo bookProjectBalance(@RequestParam("setId") Long setId,
                                              @RequestParam(value = "beginPeriod", required = false) String beginPeriod,
                                              @RequestParam(value = "endPeriod", required = false) String endPeriod,
                                              HttpServletRequest request) {
        try {
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select e.project, round(sum(e.debit),2) as debit, round(sum(e.credit),2) as credit, "
                    + "round(sum(e.debit) - sum(e.credit),2) as balance, count(*) as entryCount "
                    + "from jsh_lg_voucher_entry e join jsh_lg_voucher v on v.id = e.voucher_id "
                    + "where e.set_id = ? and ifnull(e.delete_flag,'0') <> '1' and ifnull(v.delete_flag,'0') <> '1' "
                    + "and ifnull(e.project,'') <> '' ";
            a.add(setId);
            if (tenantId != null && tenantId != 0L) {
                sql += " and e.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(beginPeriod)) {
                sql += " and v.period >= ? ";
                a.add(beginPeriod.trim());
            }
            if (!PluginUtils.isEmpty(endPeriod)) {
                sql += " and v.period <= ? ";
                a.add(endPeriod.trim());
            }
            return PluginUtils.ok(jdbc().queryForList(sql + " group by e.project order by e.project", a.toArray()));
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 报表 ----------------

    @GetMapping("/report/profit")
    @ApiOperation(value = "利润表")
    public BaseResponseInfo reportProfit(@RequestParam("setId") Long setId,
                                         @RequestParam(value = "beginPeriod", required = false) String beginPeriod,
                                         @RequestParam(value = "endPeriod", required = false) String endPeriod,
                                         HttpServletRequest request) {
        try {
            Map<String, BigDecimal> sums = subjectSums(setId, beginPeriod, endPeriod, tenant(request));
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal otherRevenue = BigDecimal.ZERO;
            BigDecimal cost = BigDecimal.ZERO;
            BigDecimal otherCost = BigDecimal.ZERO;
            BigDecimal tax = BigDecimal.ZERO;
            BigDecimal selling = BigDecimal.ZERO;
            BigDecimal admin = BigDecimal.ZERO;
            BigDecimal finance = BigDecimal.ZERO;
            BigDecimal nonOperatingIncome = BigDecimal.ZERO;
            BigDecimal nonOperatingExpense = BigDecimal.ZERO;
            BigDecimal incomeTax = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> e : sums.entrySet()) {
                String code = e.getKey();
                BigDecimal amount = e.getValue();
                if (code.startsWith("6001")) {
                    revenue = revenue.add(amount);
                } else if (code.startsWith("6051")) {
                    otherRevenue = otherRevenue.add(amount);
                } else if (code.startsWith("6401")) {
                    cost = cost.add(amount.negate());
                } else if (code.startsWith("6402")) {
                    otherCost = otherCost.add(amount.negate());
                } else if (code.startsWith("6403")) {
                    tax = tax.add(amount.negate());
                } else if (code.startsWith("6601")) {
                    selling = selling.add(amount.negate());
                } else if (code.startsWith("6602")) {
                    admin = admin.add(amount.negate());
                } else if (code.startsWith("6603")) {
                    finance = finance.add(amount.negate());
                } else if (code.startsWith("6301")) {
                    nonOperatingIncome = nonOperatingIncome.add(amount);
                } else if (code.startsWith("6711")) {
                    nonOperatingExpense = nonOperatingExpense.add(amount.negate());
                } else if (code.startsWith("6801")) {
                    incomeTax = incomeTax.add(amount.negate());
                }
            }
            BigDecimal operatingProfit = revenue.add(otherRevenue).subtract(cost).subtract(otherCost)
                    .subtract(tax).subtract(selling).subtract(admin).subtract(finance);
            BigDecimal totalProfit = operatingProfit.add(nonOperatingIncome).subtract(nonOperatingExpense);
            BigDecimal netProfit = totalProfit.subtract(incomeTax);

            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            rows.add(line("一、营业收入", revenue));
            rows.add(line("减：营业成本", cost));
            rows.add(line("　　税金及附加", tax));
            rows.add(line("　　销售费用", selling));
            rows.add(line("　　管理费用", admin));
            rows.add(line("　　财务费用", finance));
            rows.add(line("加：其他业务收入", otherRevenue));
            rows.add(line("减：其他业务成本", otherCost));
            rows.add(line("二、营业利润", operatingProfit));
            rows.add(line("加：营业外收入", nonOperatingIncome));
            rows.add(line("减：营业外支出", nonOperatingExpense));
            rows.add(line("三、利润总额", totalProfit));
            rows.add(line("减：所得税费用", incomeTax));
            rows.add(line("四、净利润", netProfit));
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("rows", rows);
            data.put("operatingProfit", operatingProfit);
            data.put("totalProfit", totalProfit);
            data.put("netProfit", netProfit);
            data.put("revenue", revenue);
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/report/balance")
    @ApiOperation(value = "资产负债表")
    public BaseResponseInfo reportBalance(@RequestParam("setId") Long setId,
                                         @RequestParam(value = "period", required = false) String period,
                                         HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            List<Object> a = args();
            String sql = "select s.code, s.name, s.category, s.direction, "
                    + "round(ifnull(sum(e.debit),0) - ifnull(sum(e.credit),0),2) as dc "
                    + "from jsh_lg_subject s left join jsh_lg_voucher_entry e on e.subject_code = s.code and e.set_id = s.set_id "
                    + "and ifnull(e.delete_flag,'0') <> '1' "
                    + "left join jsh_lg_voucher v on v.id = e.voucher_id "
                    + "where s.set_id = ? and ifnull(s.delete_flag,'0') <> '1' ";
            a.add(setId);
            if (tenantId != null && tenantId != 0L) {
                sql += " and s.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(period)) {
                sql += " and (v.period is null or v.period <= ?) ";
                a.add(period.trim());
            }
            sql += " group by s.code, s.name, s.category, s.direction order by s.code";
            List<Map<String, Object>> subjects = jdbc.queryForList(sql, a.toArray());
            List<Map<String, Object>> assets = new ArrayList<Map<String, Object>>();
            List<Map<String, Object>> liabilities = new ArrayList<Map<String, Object>>();
            List<Map<String, Object>> equity = new ArrayList<Map<String, Object>>();
            BigDecimal assetTotal = BigDecimal.ZERO;
            BigDecimal liabilityTotal = BigDecimal.ZERO;
            BigDecimal equityTotal = BigDecimal.ZERO;
            for (Map<String, Object> s : subjects) {
                BigDecimal dc = PluginUtils.decimal(s.get("dc"));
                String category = String.valueOf(s.get("category"));
                BigDecimal amount;
                if ("资产".equals(category)) {
                    amount = dc;
                } else {
                    amount = dc.negate();
                }
                if (amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                Map<String, Object> row = line(String.valueOf(s.get("code")) + " " + s.get("name"), amount);
                if ("资产".equals(category)) {
                    assets.add(row);
                    assetTotal = assetTotal.add(amount);
                } else if ("负债".equals(category)) {
                    liabilities.add(row);
                    liabilityTotal = liabilityTotal.add(amount);
                } else if ("权益".equals(category)) {
                    equity.add(row);
                    equityTotal = equityTotal.add(amount);
                }
            }
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("assets", assets);
            data.put("liabilities", liabilities);
            data.put("equity", equity);
            data.put("assetTotal", assetTotal);
            data.put("liabilityTotal", liabilityTotal);
            data.put("equityTotal", equityTotal);
            data.put("balanced", assetTotal.compareTo(liabilityTotal.add(equityTotal)) == 0);
            data.put("diff", assetTotal.subtract(liabilityTotal.add(equityTotal)));
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    @GetMapping("/report/cashflow")
    @ApiOperation(value = "现金流量表（按现金银行科目发生额）")
    public BaseResponseInfo reportCashflow(@RequestParam("setId") Long setId,
                                          @RequestParam(value = "beginPeriod", required = false) String beginPeriod,
                                          @RequestParam(value = "endPeriod", required = false) String endPeriod,
                                          HttpServletRequest request) {
        try {
            JdbcTemplate jdbc = jdbc();
            Long tenantId = tenant(request);
            List<Object> a = args();
            String cashCond = " (e.subject_code like '1001%' or e.subject_code like '1002%' or e.subject_code like '1012%') ";
            String base = "select ifnull(sum(e.debit),0) as inflow, ifnull(sum(e.credit),0) as outflow "
                    + "from jsh_lg_voucher_entry e join jsh_lg_voucher v on v.id = e.voucher_id "
                    + "where e.set_id = ? and ifnull(e.delete_flag,'0') <> '1' and ifnull(v.delete_flag,'0') <> '1' and " + cashCond;
            a.add(setId);
            if (tenantId != null && tenantId != 0L) {
                base += " and e.tenant_id = ? ";
                a.add(tenantId);
            }
            if (!PluginUtils.isEmpty(beginPeriod)) {
                base += " and v.period >= ? ";
                a.add(beginPeriod.trim());
            }
            if (!PluginUtils.isEmpty(endPeriod)) {
                base += " and v.period <= ? ";
                a.add(endPeriod.trim());
            }
            List<Map<String, Object>> sums = jdbc.queryForList(base, a.toArray());
            BigDecimal inflow = sums.isEmpty() ? BigDecimal.ZERO : PluginUtils.decimal(sums.get(0).get("inflow"));
            BigDecimal outflow = sums.isEmpty() ? BigDecimal.ZERO : PluginUtils.decimal(sums.get(0).get("outflow"));

            List<Object> oa = args();
            String openSql = "select ifnull(sum(e.debit),0) - ifnull(sum(e.credit),0) as opening "
                    + "from jsh_lg_voucher_entry e join jsh_lg_voucher v on v.id = e.voucher_id "
                    + "where e.set_id = ? and ifnull(e.delete_flag,'0') <> '1' and ifnull(v.delete_flag,'0') <> '1' and " + cashCond;
            oa.add(setId);
            if (tenantId != null && tenantId != 0L) {
                openSql += " and e.tenant_id = ? ";
                oa.add(tenantId);
            }
            if (!PluginUtils.isEmpty(beginPeriod)) {
                openSql += " and v.period < ? ";
                oa.add(beginPeriod.trim());
            }
            List<Map<String, Object>> openRows = jdbc.queryForList(openSql, oa.toArray());
            BigDecimal opening = openRows.isEmpty() ? BigDecimal.ZERO : PluginUtils.decimal(openRows.get(0).get("opening"));

            // 按对方科目归集现金流入/流出
            List<Object> da = args();
            String detailSql = "select coalesce(nullif(other.subject_name,''),'其他') as item, "
                    + "round(sum(case when " + cashCond.replace("e.", "c.") + " then c.debit else 0 end),2) as inflow, "
                    + "round(sum(case when " + cashCond.replace("e.", "c.") + " then c.credit else 0 end),2) as outflow "
                    + "from jsh_lg_voucher_entry c join jsh_lg_voucher v on v.id = c.voucher_id "
                    + "left join jsh_lg_voucher_entry other on other.voucher_id = c.voucher_id and other.id <> c.id "
                    + "and other.subject_code not like '1001%' and other.subject_code not like '1002%' and other.subject_code not like '1012%' "
                    + "where c.set_id = ? and ifnull(c.delete_flag,'0') <> '1' and ifnull(v.delete_flag,'0') <> '1' and " + cashCond;
            da.add(setId);
            if (tenantId != null && tenantId != 0L) {
                detailSql += " and c.tenant_id = ? ";
                da.add(tenantId);
            }
            if (!PluginUtils.isEmpty(beginPeriod)) {
                detailSql += " and v.period >= ? ";
                da.add(beginPeriod.trim());
            }
            if (!PluginUtils.isEmpty(endPeriod)) {
                detailSql += " and v.period <= ? ";
                da.add(endPeriod.trim());
            }
            detailSql += " group by coalesce(nullif(other.subject_name,''),'其他') order by item";

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("opening", opening);
            data.put("inflow", inflow);
            data.put("outflow", outflow);
            data.put("closing", opening.add(inflow).subtract(outflow));
            data.put("details", jdbc.queryForList(detailSql, da.toArray()));
            return PluginUtils.ok(data);
        } catch (Exception e) {
            return error(e);
        }
    }

    // ---------------- 内部方法 ----------------

    private Map<String, Object> subjectBalanceInternal(Long setId, String beginPeriod, String endPeriod, String keyword,
                                                       Integer currentPage, Integer pageSize, Long tenantId,
                                                       boolean withOpening) {
        JdbcTemplate jdbc = jdbc();
        List<Object> a = args();
        String sql = "select s.code as subjectCode, s.name as subjectName, s.category, s.direction, "
                + "round(ifnull(sum(case when v.period < ? then e.debit else 0 end),0),2) as openingDebit, "
                + "round(ifnull(sum(case when v.period < ? then e.credit else 0 end),0),2) as openingCredit, "
                + "round(ifnull(sum(case when v.period >= ? then e.debit else 0 end),0),2) as debit, "
                + "round(ifnull(sum(case when v.period >= ? then e.credit else 0 end),0),2) as credit "
                + "from jsh_lg_subject s left join jsh_lg_voucher_entry e on e.subject_code = s.code and e.set_id = s.set_id "
                + "and ifnull(e.delete_flag,'0') <> '1' left join jsh_lg_voucher v on v.id = e.voucher_id "
                + "where s.set_id = ? and ifnull(s.delete_flag,'0') <> '1' ";
        String b = PluginUtils.isEmpty(beginPeriod) ? "0000-00" : beginPeriod.trim();
        a.add(b);
        a.add(b);
        a.add(b);
        a.add(b);
        a.add(setId);
        if (tenantId != null && tenantId != 0L) {
            sql += " and s.tenant_id = ? ";
            a.add(tenantId);
        }
        if (!PluginUtils.isEmpty(endPeriod)) {
            sql += " and (v.period is null or v.period <= ?) ";
            a.add(endPeriod.trim());
        }
        if (!PluginUtils.isEmpty(keyword)) {
            sql += " and (s.code like ? or s.name like ?) ";
            String like = PluginUtils.like(keyword);
            a.add(like);
            a.add(like);
        }
        sql += " group by s.code, s.name, s.category, s.direction order by s.code";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, a.toArray());
        for (Map<String, Object> row : rows) {
            String direction = String.valueOf(row.get("direction"));
            BigDecimal opening = PluginUtils.decimal(row.get("openingDebit"))
                    .subtract(PluginUtils.decimal(row.get("openingCredit")));
            if ("贷".equals(direction)) {
                opening = opening.negate();
            }
            BigDecimal debit = PluginUtils.decimal(row.get("debit"));
            BigDecimal credit = PluginUtils.decimal(row.get("credit"));
            BigDecimal closing = opening.add(debit).subtract(credit);
            if ("贷".equals(direction)) {
                closing = closing.negate();
            }
            row.put("opening", opening.setScale(2, BigDecimal.ROUND_HALF_UP));
            row.put("closing", closing.setScale(2, BigDecimal.ROUND_HALF_UP));
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", rows);
        data.put("total", rows.size());
        return data;
    }

    /** 期间内各科目净发生额（收入类为正，成本费用类通过 negate 在报表中处理） */
    private Map<String, BigDecimal> subjectSums(Long setId, String beginPeriod, String endPeriod, Long tenantId) {
        JdbcTemplate jdbc = jdbc();
        List<Object> a = args();
        String sql = "select e.subject_code as code, round(ifnull(sum(e.credit),0) - ifnull(sum(e.debit),0),2) as amount "
                + "from jsh_lg_voucher_entry e join jsh_lg_voucher v on v.id = e.voucher_id "
                + "where e.set_id = ? and ifnull(e.delete_flag,'0') <> '1' and ifnull(v.delete_flag,'0') <> '1' ";
        a.add(setId);
        if (tenantId != null && tenantId != 0L) {
            sql += " and e.tenant_id = ? ";
            a.add(tenantId);
        }
        if (!PluginUtils.isEmpty(beginPeriod)) {
            sql += " and v.period >= ? ";
            a.add(beginPeriod.trim());
        }
        if (!PluginUtils.isEmpty(endPeriod)) {
            sql += " and v.period <= ? ";
            a.add(endPeriod.trim());
        }
        sql += " group by e.subject_code";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, a.toArray());
        Map<String, BigDecimal> map = new LinkedHashMap<String, BigDecimal>();
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("code")), PluginUtils.decimal(row.get("amount")));
        }
        return map;
    }

    private Map<String, Object> line(String name, BigDecimal amount) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("item", name);
        row.put("amount", amount.setScale(2, BigDecimal.ROUND_HALF_UP));
        return row;
    }

    private String nextPeriod(String period) {
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM");
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTime(fmt.parse(period));
            c.add(java.util.Calendar.MONTH, 1);
            return fmt.format(c.getTime());
        } catch (Exception e) {
            return period;
        }
    }

}

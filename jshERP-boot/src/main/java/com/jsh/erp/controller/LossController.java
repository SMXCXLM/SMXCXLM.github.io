package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.service.LossService;
import com.jsh.erp.utils.BaseResponseInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 报损管理：报损出库申请 + 申请人 → CFO → CEO 审批
 */
@RestController
@RequestMapping(value = "/loss")
@Api(tags = {"报损管理"})
public class LossController {

    private static final Logger logger = LoggerFactory.getLogger(LossController.class);

    @Resource
    private LossService lossService;

    @GetMapping(value = "/list")
    @ApiOperation(value = "报损单列表")
    public BaseResponseInfo list(@RequestParam(value = "status", required = false) String status,
                                 @RequestParam(value = "keyword", required = false) String keyword,
                                 @RequestParam(value = "mine", required = false) Integer mine,
                                 @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
                                 @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize,
                                 HttpServletRequest request) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            Map<String, Object> data = lossService.queryList(status, keyword, mine, currentPage, pageSize);
            info.code = 200;
            info.data = data;
        } catch (Exception e) {
            logger.error("报损单列表查询失败", e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @GetMapping(value = "/pending")
    @ApiOperation(value = "待我审核的报损单")
    public BaseResponseInfo pending(HttpServletRequest request) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            info.code = 200;
            info.data = lossService.queryPendingToMe();
        } catch (Exception e) {
            logger.error("待审核报损单查询失败", e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @GetMapping(value = "/info")
    @ApiOperation(value = "报损单详情")
    public BaseResponseInfo info(@RequestParam(value = "id") Long id, HttpServletRequest request) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            Map<String, Object> data = lossService.getInfo(id);
            if (data == null) {
                info.code = 500;
                info.data = "报损单不存在";
            } else {
                info.code = 200;
                info.data = data;
            }
        } catch (Exception e) {
            logger.error("报损单详情查询失败", e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @PostMapping(value = "/save")
    @ApiOperation(value = "保存/提交报损单")
    public BaseResponseInfo save(@RequestBody JSONObject obj, HttpServletRequest request) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            lossService.save(obj);
            info.code = 200;
            info.data = "保存成功";
        } catch (Exception e) {
            logger.error("报损单保存失败", e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @PostMapping(value = "/approve")
    @ApiOperation(value = "报损单审批（pass/reject）")
    public BaseResponseInfo approve(@RequestBody JSONObject obj, HttpServletRequest request) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            lossService.approve(obj);
            info.code = 200;
            info.data = "操作成功";
        } catch (Exception e) {
            logger.error("报损单审批失败", e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @DeleteMapping(value = "/delete")
    @ApiOperation(value = "删除报损单")
    public BaseResponseInfo delete(@RequestParam(value = "id") Long id, HttpServletRequest request) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            lossService.delete(id);
            info.code = 200;
            info.data = "删除成功";
        } catch (Exception e) {
            logger.error("报损单删除失败", e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }
}

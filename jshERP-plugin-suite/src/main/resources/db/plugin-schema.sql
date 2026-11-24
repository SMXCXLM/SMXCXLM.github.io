-- ============================================================
-- 思雨食品 ERP v3.6 增强插件套件 数据库脚本
-- 所有表均带 tenant_id，删除标记统一 delete_flag
-- 由插件启动时自动执行，可重复运行
-- ============================================================

-- ---------- 生产管理 ----------
CREATE TABLE IF NOT EXISTS `jsh_pd_bom` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `bom_no` varchar(50) DEFAULT NULL COMMENT 'BOM编号',
  `bom_name` varchar(100) DEFAULT NULL COMMENT 'BOM名称',
  `material_id` bigint DEFAULT NULL COMMENT '成品商品id',
  `material_extend_id` bigint DEFAULT NULL COMMENT '成品多属性id',
  `version` varchar(30) DEFAULT NULL COMMENT '版本号',
  `output_qty` decimal(24,6) DEFAULT 1 COMMENT '标准产出数量',
  `unit` varchar(30) DEFAULT NULL COMMENT '单位',
  `enabled` char(1) DEFAULT '1' COMMENT '启用 0否 1是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_bom_material` (`material_id`),
  KEY `idx_bom_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='BOM主表';

CREATE TABLE IF NOT EXISTS `jsh_pd_bom_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `bom_id` bigint DEFAULT NULL COMMENT 'BOM id',
  `material_id` bigint DEFAULT NULL COMMENT '物料商品id',
  `material_extend_id` bigint DEFAULT NULL COMMENT '物料多属性id',
  `unit` varchar(30) DEFAULT NULL COMMENT '单位',
  `qty` decimal(24,6) DEFAULT NULL COMMENT '单位用量',
  `loss_rate` decimal(24,6) DEFAULT 0 COMMENT '损耗率(%)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_bom_item_bom` (`bom_id`),
  KEY `idx_bom_item_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='BOM物料明细';

CREATE TABLE IF NOT EXISTS `jsh_pd_bom_process` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `bom_id` bigint DEFAULT NULL COMMENT 'BOM id',
  `seq` int DEFAULT NULL COMMENT '工序顺序',
  `process_name` varchar(100) DEFAULT NULL COMMENT '工序名称',
  `work_center` varchar(100) DEFAULT NULL COMMENT '工作中心/设备',
  `standard_hours` decimal(24,6) DEFAULT NULL COMMENT '标准工时(小时)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_bom_process_bom` (`bom_id`),
  KEY `idx_bom_process_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='BOM工序明细';

CREATE TABLE IF NOT EXISTS `jsh_pd_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_no` varchar(50) DEFAULT NULL COMMENT '生产任务号',
  `bom_id` bigint DEFAULT NULL COMMENT 'BOM id',
  `material_id` bigint DEFAULT NULL COMMENT '成品商品id',
  `material_extend_id` bigint DEFAULT NULL COMMENT '成品多属性id',
  `plan_qty` decimal(24,6) DEFAULT NULL COMMENT '计划数量',
  `finished_qty` decimal(24,6) DEFAULT 0 COMMENT '已完工数量',
  `unit` varchar(30) DEFAULT NULL COMMENT '单位',
  `start_date` datetime DEFAULT NULL COMMENT '计划开工',
  `end_date` datetime DEFAULT NULL COMMENT '计划完工',
  `workshop` varchar(100) DEFAULT NULL COMMENT '车间/班组',
  `status` varchar(2) DEFAULT '0' COMMENT '0待生产 1生产中 2已完工 3已关闭',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_pd_task_tenant` (`tenant_id`),
  KEY `idx_pd_task_no` (`task_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='生产任务';

CREATE TABLE IF NOT EXISTS `jsh_pd_task_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint DEFAULT NULL COMMENT '生产任务id',
  `type` varchar(10) DEFAULT NULL COMMENT '领料|退料|用料',
  `material_id` bigint DEFAULT NULL COMMENT '物料商品id',
  `material_extend_id` bigint DEFAULT NULL COMMENT '物料多属性id',
  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',
  `qty` decimal(24,6) DEFAULT NULL COMMENT '数量',
  `unit` varchar(30) DEFAULT NULL COMMENT '单位',
  `oper_time` datetime DEFAULT NULL COMMENT '业务时间',
  `operator` bigint DEFAULT NULL COMMENT '经办人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_pd_tm_task` (`task_id`),
  KEY `idx_pd_tm_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='生产领退料与用料';

CREATE TABLE IF NOT EXISTS `jsh_pd_process_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint DEFAULT NULL COMMENT '生产任务id',
  `process_id` bigint DEFAULT NULL COMMENT '工序id',
  `process_name` varchar(100) DEFAULT NULL COMMENT '工序名称',
  `report_qty` decimal(24,6) DEFAULT NULL COMMENT '汇报数量',
  `qualified_qty` decimal(24,6) DEFAULT NULL COMMENT '合格数量',
  `unqualified_qty` decimal(24,6) DEFAULT NULL COMMENT '不合格数量',
  `worker` varchar(50) DEFAULT NULL COMMENT '报工人',
  `report_time` datetime DEFAULT NULL COMMENT '汇报时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_pd_pr_task` (`task_id`),
  KEY `idx_pd_pr_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='工序汇报';

CREATE TABLE IF NOT EXISTS `jsh_pd_inspection` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint DEFAULT NULL COMMENT '生产任务id',
  `inspect_no` varchar(50) DEFAULT NULL COMMENT '验收单号',
  `inspect_qty` decimal(24,6) DEFAULT NULL COMMENT '送检数量',
  `qualified_qty` decimal(24,6) DEFAULT NULL COMMENT '合格数量',
  `unqualified_qty` decimal(24,6) DEFAULT NULL COMMENT '不合格数量',
  `inspector` varchar(50) DEFAULT NULL COMMENT '验收人',
  `inspect_time` datetime DEFAULT NULL COMMENT '验收时间',
  `result` varchar(10) DEFAULT NULL COMMENT '合格|不合格|让步接收',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_pd_ins_task` (`task_id`),
  KEY `idx_pd_ins_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='生产验收记录';

CREATE TABLE IF NOT EXISTS `jsh_pd_instock` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint DEFAULT NULL COMMENT '生产任务id',
  `number` varchar(50) DEFAULT NULL COMMENT '入库单号',
  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',
  `material_id` bigint DEFAULT NULL COMMENT '商品id',
  `material_extend_id` bigint DEFAULT NULL COMMENT '多属性id',
  `qty` decimal(24,6) DEFAULT NULL COMMENT '入库数量',
  `unit` varchar(30) DEFAULT NULL COMMENT '单位',
  `unit_price` decimal(24,6) DEFAULT NULL COMMENT '成本单价',
  `oper_time` datetime DEFAULT NULL COMMENT '入库时间',
  `depot_head_id` bigint DEFAULT NULL COMMENT '生成的出入库单据id',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_pd_in_task` (`task_id`),
  KEY `idx_pd_in_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='生产完工入库';

-- ---------- 总账管理 ----------
CREATE TABLE IF NOT EXISTS `jsh_lg_account_set` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `set_name` varchar(100) DEFAULT NULL COMMENT '账套名称',
  `company` varchar(200) DEFAULT NULL COMMENT '核算单位',
  `start_period` varchar(10) DEFAULT NULL COMMENT '启用期间 yyyy-MM',
  `currency` varchar(10) DEFAULT '人民币' COMMENT '币种',
  `current_period` varchar(10) DEFAULT NULL COMMENT '当前期间 yyyy-MM',
  `status` char(1) DEFAULT '1' COMMENT '1启用 0停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_lg_set_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账套';

CREATE TABLE IF NOT EXISTS `jsh_lg_subject` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `set_id` bigint DEFAULT NULL COMMENT '账套id',
  `code` varchar(30) DEFAULT NULL COMMENT '科目编码',
  `name` varchar(100) DEFAULT NULL COMMENT '科目名称',
  `category` varchar(20) DEFAULT NULL COMMENT '资产|负债|权益|成本|损益',
  `direction` char(1) DEFAULT NULL COMMENT '余额方向 借/贷',
  `parent_code` varchar(30) DEFAULT NULL COMMENT '上级科目编码',
  `level` int DEFAULT 1 COMMENT '级次',
  `is_detail` char(1) DEFAULT '1' COMMENT '是否明细科目',
  `enabled` char(1) DEFAULT '1' COMMENT '启用',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_lg_sub_set` (`set_id`,`code`),
  KEY `idx_lg_sub_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='会计科目';

CREATE TABLE IF NOT EXISTS `jsh_lg_voucher` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `set_id` bigint DEFAULT NULL COMMENT '账套id',
  `voucher_word` varchar(20) DEFAULT '记' COMMENT '凭证字',
  `voucher_no` int DEFAULT NULL COMMENT '凭证号',
  `voucher_date` datetime DEFAULT NULL COMMENT '凭证日期',
  `period` varchar(10) DEFAULT NULL COMMENT '会计期间 yyyy-MM',
  `attachment_num` int DEFAULT 0 COMMENT '附件数',
  `creator` bigint DEFAULT NULL COMMENT '制单人',
  `audit_status` char(1) DEFAULT '0' COMMENT '0未审核 1已审核',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_lg_vou_set` (`set_id`,`period`),
  KEY `idx_lg_vou_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='记账凭证';

CREATE TABLE IF NOT EXISTS `jsh_lg_voucher_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `voucher_id` bigint DEFAULT NULL COMMENT '凭证id',
  `set_id` bigint DEFAULT NULL COMMENT '账套id',
  `summary` varchar(200) DEFAULT NULL COMMENT '摘要',
  `subject_code` varchar(30) DEFAULT NULL COMMENT '科目编码',
  `subject_name` varchar(100) DEFAULT NULL COMMENT '科目名称',
  `debit` decimal(24,6) DEFAULT 0 COMMENT '借方金额',
  `credit` decimal(24,6) DEFAULT 0 COMMENT '贷方金额',
  `aux_info` varchar(200) DEFAULT NULL COMMENT '辅助核算',
  `project` varchar(100) DEFAULT NULL COMMENT '项目',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_lg_ent_vou` (`voucher_id`),
  KEY `idx_lg_ent_sub` (`set_id`,`subject_code`),
  KEY `idx_lg_ent_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='凭证分录';

CREATE TABLE IF NOT EXISTS `jsh_lg_period` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `set_id` bigint DEFAULT NULL COMMENT '账套id',
  `period` varchar(10) DEFAULT NULL COMMENT '期间 yyyy-MM',
  `status` char(1) DEFAULT '0' COMMENT '0未结账 1已结账',
  `close_time` datetime DEFAULT NULL COMMENT '结账时间',
  `closer` bigint DEFAULT NULL COMMENT '结账人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_lg_per_set` (`set_id`,`period`),
  KEY `idx_lg_per_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='期间结账';

-- ---------- 盘点业务 ----------
CREATE TABLE IF NOT EXISTS `jsh_sc_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `check_no` varchar(50) DEFAULT NULL COMMENT '盘点单号',
  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',
  `check_type` varchar(20) DEFAULT '全盘' COMMENT '全盘|抽盘',
  `check_date` datetime DEFAULT NULL COMMENT '盘点日期',
  `status` varchar(2) DEFAULT '0' COMMENT '0录入中 1待复盘 2复盘完成 3已审核',
  `creator` bigint DEFAULT NULL COMMENT '制单人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_sc_ord_tenant` (`tenant_id`),
  KEY `idx_sc_ord_no` (`check_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='盘点单';

CREATE TABLE IF NOT EXISTS `jsh_sc_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `check_id` bigint DEFAULT NULL COMMENT '盘点单id',
  `material_id` bigint DEFAULT NULL COMMENT '商品id',
  `material_extend_id` bigint DEFAULT NULL COMMENT '多属性id',
  `sku` varchar(50) DEFAULT NULL COMMENT '多属性',
  `batch_number` varchar(100) DEFAULT NULL COMMENT '批号',
  `unit` varchar(30) DEFAULT NULL COMMENT '单位',
  `book_qty` decimal(24,6) DEFAULT 0 COMMENT '账面数量',
  `first_qty` decimal(24,6) DEFAULT NULL COMMENT '初盘数量',
  `first_diff` decimal(24,6) DEFAULT 0 COMMENT '初盘差异',
  `second_qty` decimal(24,6) DEFAULT NULL COMMENT '复盘数量',
  `second_diff` decimal(24,6) DEFAULT NULL COMMENT '复盘差异',
  `final_qty` decimal(24,6) DEFAULT NULL COMMENT '最终数量',
  `need_second` char(1) DEFAULT '0' COMMENT '是否需要复盘',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_sc_itm_ord` (`check_id`),
  KEY `idx_sc_itm_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='盘点明细';

-- ---------- 多级审核 ----------
CREATE TABLE IF NOT EXISTS `jsh_af_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `flow_name` varchar(100) DEFAULT NULL COMMENT '流程名称',
  `bill_type` varchar(50) DEFAULT NULL COMMENT '单据类型',
  `max_level` int DEFAULT 1 COMMENT '审核级数(1-5)',
  `enabled` char(1) DEFAULT '1' COMMENT '启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_af_flow_tenant` (`tenant_id`),
  KEY `idx_af_flow_type` (`bill_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='审核流程';

CREATE TABLE IF NOT EXISTS `jsh_af_flow_level` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `flow_id` bigint DEFAULT NULL COMMENT '流程id',
  `level` int DEFAULT NULL COMMENT '级次',
  `approver_type` varchar(2) DEFAULT NULL COMMENT '1部门负责人 2上级部门负责人 3指定人',
  `approver_id` bigint DEFAULT NULL COMMENT '指定人id(类型3)',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_af_lvl_flow` (`flow_id`),
  KEY `idx_af_lvl_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='审核流程级次';

CREATE TABLE IF NOT EXISTS `jsh_af_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `flow_id` bigint DEFAULT NULL COMMENT '流程id',
  `bill_type` varchar(50) DEFAULT NULL COMMENT '单据类型',
  `bill_id` bigint DEFAULT NULL COMMENT '单据id',
  `bill_number` varchar(50) DEFAULT NULL COMMENT '单据号',
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `status` varchar(2) DEFAULT '0' COMMENT '0审核中 1已通过 2已撤回 3已删除 4已驳回',
  `current_level` int DEFAULT 1 COMMENT '当前级次',
  `creator` bigint DEFAULT NULL COMMENT '发起人',
  `create_time` datetime DEFAULT NULL COMMENT '发起时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_af_ins_tenant` (`tenant_id`),
  KEY `idx_af_ins_bill` (`bill_type`,`bill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='审核实例';

CREATE TABLE IF NOT EXISTS `jsh_af_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `instance_id` bigint DEFAULT NULL COMMENT '实例id',
  `level` int DEFAULT NULL COMMENT '级次',
  `approver_id` bigint DEFAULT NULL COMMENT '审核人',
  `status` varchar(2) DEFAULT '0' COMMENT '0待办 1已办',
  `result` varchar(2) DEFAULT NULL COMMENT '1同意 2驳回',
  `comment` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_af_task_ins` (`instance_id`),
  KEY `idx_af_task_apv` (`approver_id`,`status`),
  KEY `idx_af_task_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='审核任务(待办已办)';

-- ---------- 三联打印 ----------
CREATE TABLE IF NOT EXISTS `jsh_print_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_name` varchar(100) DEFAULT NULL COMMENT '模板名称',
  `bill_type` varchar(50) DEFAULT NULL COMMENT '单据类型',
  `is_default` char(1) DEFAULT '0' COMMENT '是否默认',
  `page_size` varchar(30) DEFAULT '241x140' COMMENT '纸张规格',
  `header_json` longtext COMMENT '表头/公司信息/自定义排版',
  `detail_json` longtext COMMENT '明细列配置',
  `icon` varchar(1000) DEFAULT NULL COMMENT '图标',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_print_tpl_tenant` (`tenant_id`),
  KEY `idx_print_tpl_type` (`bill_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='三联打印模板';

-- ---------- 车辆管理 ----------
CREATE TABLE IF NOT EXISTS `jsh_vehicle` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plate_no` varchar(30) DEFAULT NULL COMMENT '车牌号',
  `brand` varchar(60) DEFAULT NULL COMMENT '车辆品牌',
  `model` varchar(60) DEFAULT NULL COMMENT '车型',
  `driver` varchar(50) DEFAULT NULL COMMENT '驾驶员',
  `driver_phone` varchar(30) DEFAULT NULL COMMENT '驾驶员电话',
  `engine_no` varchar(60) DEFAULT NULL COMMENT '发动机号',
  `vin` varchar(60) DEFAULT NULL COMMENT '车架号',
  `buy_date` datetime DEFAULT NULL COMMENT '购置日期',
  `status` varchar(20) DEFAULT '在用' COMMENT '在用|维修|停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_vehicle_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='车辆管理';

-- ---------- 客户跟进 ----------
CREATE TABLE IF NOT EXISTS `jsh_customer_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_id` bigint DEFAULT NULL COMMENT '客户id',
  `customer_name` varchar(100) DEFAULT NULL COMMENT '客户名称',
  `follow_time` datetime DEFAULT NULL COMMENT '跟进时间',
  `content` varchar(1000) DEFAULT NULL COMMENT '跟进内容',
  `is_continue` char(1) DEFAULT '1' COMMENT '是否继续跟进 1是 0否',
  `next_time` datetime DEFAULT NULL COMMENT '下次跟进时间',
  `follower` bigint DEFAULT NULL COMMENT '跟进人',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_cf_customer` (`customer_id`),
  KEY `idx_cf_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='客户跟进记录';

-- ---------- 固定资产 ----------
CREATE TABLE IF NOT EXISTS `jsh_fa_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) DEFAULT NULL COMMENT '资产类别',
  `useful_life` int DEFAULT NULL COMMENT '折旧期限(月)',
  `residual_rate` decimal(24,6) DEFAULT 5 COMMENT '残值率(%)',
  `method` varchar(30) DEFAULT '年限平均法' COMMENT '折旧方法',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_fa_cat_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='固定资产类别';

CREATE TABLE IF NOT EXISTS `jsh_fa_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `asset_no` varchar(50) DEFAULT NULL COMMENT '资产编号',
  `name` varchar(100) DEFAULT NULL COMMENT '资产名称',
  `category_id` bigint DEFAULT NULL COMMENT '类别id',
  `price` decimal(24,6) DEFAULT NULL COMMENT '资产原值',
  `residual_rate` decimal(24,6) DEFAULT 5 COMMENT '残值率(%)',
  `useful_life` int DEFAULT NULL COMMENT '折旧期限(月)',
  `buy_time` datetime DEFAULT NULL COMMENT '购置时间',
  `current_user_id` bigint DEFAULT NULL COMMENT '当前使用人',
  `depot_id` bigint DEFAULT NULL COMMENT '存放仓库/位置',
  `status` varchar(20) DEFAULT '在用' COMMENT '在用|闲置|报废',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_fa_asset_tenant` (`tenant_id`),
  KEY `idx_fa_asset_no` (`asset_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='固定资产';

CREATE TABLE IF NOT EXISTS `jsh_fa_user_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `asset_id` bigint DEFAULT NULL COMMENT '资产id',
  `user_id` bigint DEFAULT NULL COMMENT '使用人',
  `user_name` varchar(50) DEFAULT NULL COMMENT '使用人姓名',
  `start_time` datetime DEFAULT NULL COMMENT '开始使用',
  `end_time` datetime DEFAULT NULL COMMENT '结束使用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_fa_log_asset` (`asset_id`),
  KEY `idx_fa_log_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='资产使用人历史';

-- ---------- 业绩提成 ----------
CREATE TABLE IF NOT EXISTS `jsh_commission_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_name` varchar(100) DEFAULT NULL COMMENT '规则名称',
  `type` char(1) DEFAULT '1' COMMENT '1统一数字 2阶梯提成',
  `value` decimal(24,6) DEFAULT NULL COMMENT '统一提成值(元/单或%)',
  `value_type` char(1) DEFAULT '1' COMMENT '1按金额比例 2按件数定额',
  `start_date` datetime DEFAULT NULL COMMENT '生效开始',
  `end_date` datetime DEFAULT NULL COMMENT '生效结束',
  `enabled` char(1) DEFAULT '1' COMMENT '启用',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_cm_rule_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='提成规则';

CREATE TABLE IF NOT EXISTS `jsh_commission_ladder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_id` bigint DEFAULT NULL COMMENT '规则id',
  `min_amount` decimal(24,6) DEFAULT NULL COMMENT '区间下限',
  `max_amount` decimal(24,6) DEFAULT NULL COMMENT '区间上限',
  `rate` decimal(24,6) DEFAULT NULL COMMENT '提成比例(%)',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_cm_lad_rule` (`rule_id`),
  KEY `idx_cm_lad_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='阶梯提成区间';

CREATE TABLE IF NOT EXISTS `jsh_commission_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `bill_id` bigint DEFAULT NULL COMMENT '单据id',
  `bill_number` varchar(50) DEFAULT NULL COMMENT '单据号',
  `sales_man_id` bigint DEFAULT NULL COMMENT '业务员id',
  `sales_man_name` varchar(50) DEFAULT NULL COMMENT '业务员',
  `bill_amount` decimal(24,6) DEFAULT NULL COMMENT '单据金额',
  `rate` decimal(24,6) DEFAULT NULL COMMENT '提成比例(%)',
  `commission` decimal(24,6) DEFAULT NULL COMMENT '提成金额',
  `period` varchar(10) DEFAULT NULL COMMENT '期间 yyyy-MM',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_cm_rec_bill` (`bill_id`),
  KEY `idx_cm_rec_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='提成计算记录';

-- ---------- 条码打印 ----------
CREATE TABLE IF NOT EXISTS `jsh_barcode_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_name` varchar(100) DEFAULT NULL COMMENT '模板名称',
  `width` decimal(10,2) DEFAULT 40.00 COMMENT '标签宽(mm)',
  `height` decimal(10,2) DEFAULT 30.00 COMMENT '标签高(mm)',
  `columns` int DEFAULT 3 COMMENT '每行列数',
  `barcode_height` int DEFAULT 30 COMMENT '条码高度(px)',
  `show_title` char(1) DEFAULT '1' COMMENT '显示标题',
  `title_text` varchar(100) DEFAULT '商品条码' COMMENT '标题文字',
  `show_name` char(1) DEFAULT '1' COMMENT '显示商品名称',
  `show_standard` char(1) DEFAULT '1' COMMENT '显示规格',
  `show_unit` char(1) DEFAULT '0' COMMENT '显示单位',
  `show_price` char(1) DEFAULT '1' COMMENT '显示价格',
  `price_type` varchar(20) DEFAULT 'commodity' COMMENT '价格取数：commodity零售/wholesale批发/low最低',
  `show_barcode` char(1) DEFAULT '1' COMMENT '显示条码',
  `show_shop` char(1) DEFAULT '0' COMMENT '显示店铺名称',
  `shop_name` varchar(100) DEFAULT NULL COMMENT '店铺名称',
  `is_default` char(1) DEFAULT '0' COMMENT '默认模板',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_bc_tpl_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='条码打印模板';

-- ---------- 发票管理 ----------
CREATE TABLE IF NOT EXISTS `jsh_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `invoice_type` varchar(10) DEFAULT NULL COMMENT 'purchase采购/sales销售',
  `bill_id` bigint DEFAULT NULL COMMENT '业务单据id',
  `bill_number` varchar(50) DEFAULT NULL COMMENT '业务单据号',
  `invoice_no` varchar(50) DEFAULT NULL COMMENT '发票号码',
  `invoice_date` date DEFAULT NULL COMMENT '开票日期',
  `amount` decimal(24,6) DEFAULT 0 COMMENT '开票金额',
  `tax_amount` decimal(24,6) DEFAULT 0 COMMENT '税额',
  `title` varchar(200) DEFAULT NULL COMMENT '发票抬头',
  `tax_num` varchar(50) DEFAULT NULL COMMENT '税号',
  `organ_name` varchar(255) DEFAULT NULL COMMENT '往来单位',
  `status` varchar(10) DEFAULT '已开票' COMMENT '已开票/已作废',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_inv_bill` (`bill_id`),
  KEY `idx_inv_type` (`invoice_type`),
  KEY `idx_inv_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='发票登记';

-- ---------- 零售收银 ----------
CREATE TABLE IF NOT EXISTS `jsh_pos_hold` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `hold_no` varchar(50) DEFAULT NULL COMMENT '挂单号',
  `member_id` bigint DEFAULT NULL COMMENT '会员id',
  `member_name` varchar(100) DEFAULT NULL COMMENT '会员名称',
  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',
  `items_json` longtext COMMENT '购物车明细JSON',
  `total_amount` decimal(24,6) DEFAULT 0 COMMENT '合计金额',
  `discount_amount` decimal(24,6) DEFAULT 0 COMMENT '优惠金额',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` bigint DEFAULT NULL COMMENT '收银员',
  `create_time` datetime DEFAULT NULL COMMENT '挂单时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `delete_flag` char(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_pos_hold_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='零售挂单';

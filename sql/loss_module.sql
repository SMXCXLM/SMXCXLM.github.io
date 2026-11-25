-- 思雨食品 ERP：报损管理 / 报损出库（申请人 → CFO → CEO 两级审批，CEO 终审后才扣库存）
-- 说明：系统存在自动租户拦截器，INSERT 语句无需显式写 tenant_id（会被自动追加）。
SET NAMES utf8mb4;

-- 1) 报损单主表
CREATE TABLE IF NOT EXISTS jsh_loss_bill (
  id BIGINT(20) NOT NULL AUTO_INCREMENT,
  bill_number VARCHAR(50) NOT NULL,
  depot_id BIGINT(20) NOT NULL,
  depot_name VARCHAR(100) DEFAULT NULL,
  reason VARCHAR(500) DEFAULT NULL,
  status VARCHAR(1) NOT NULL DEFAULT '0' COMMENT '0待CFO 1待CEO 2已通过 3已驳回',
  applicant_id BIGINT(20) DEFAULT NULL,
  applicant_name VARCHAR(100) DEFAULT NULL,
  apply_time DATETIME DEFAULT NULL,
  total_qty DECIMAL(24,6) DEFAULT 0,
  total_amount DECIMAL(24,6) DEFAULT 0,
  remark VARCHAR(500) DEFAULT NULL,
  cfo_id BIGINT(20) DEFAULT NULL,
  cfo_name VARCHAR(100) DEFAULT NULL,
  cfo_time DATETIME DEFAULT NULL,
  cfo_comment VARCHAR(500) DEFAULT NULL,
  ceo_id BIGINT(20) DEFAULT NULL,
  ceo_name VARCHAR(100) DEFAULT NULL,
  ceo_time DATETIME DEFAULT NULL,
  ceo_comment VARCHAR(500) DEFAULT NULL,
  depot_head_id BIGINT(20) DEFAULT NULL,
  depot_head_number VARCHAR(50) DEFAULT NULL,
  tenant_id BIGINT(20) DEFAULT NULL,
  delete_flag VARCHAR(1) DEFAULT '0',
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_loss_bill_number (bill_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) 报损明细
CREATE TABLE IF NOT EXISTS jsh_loss_item (
  id BIGINT(20) NOT NULL AUTO_INCREMENT,
  bill_id BIGINT(20) NOT NULL,
  material_id BIGINT(20) NOT NULL,
  material_extend_id BIGINT(20) DEFAULT NULL,
  material_name VARCHAR(200) DEFAULT NULL,
  material_model VARCHAR(100) DEFAULT NULL,
  unit VARCHAR(50) DEFAULT NULL,
  quantity DECIMAL(24,6) DEFAULT 0,
  basic_number DECIMAL(24,6) DEFAULT 0,
  unit_price DECIMAL(24,6) DEFAULT 0,
  amount DECIMAL(24,6) DEFAULT 0,
  stock_number DECIMAL(24,6) DEFAULT NULL,
  remark VARCHAR(200) DEFAULT NULL,
  tenant_id BIGINT(20) DEFAULT NULL,
  delete_flag VARCHAR(1) DEFAULT '0',
  PRIMARY KEY (id),
  KEY idx_loss_item_bill (bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) 审批日志
CREATE TABLE IF NOT EXISTS jsh_loss_log (
  id BIGINT(20) NOT NULL AUTO_INCREMENT,
  bill_id BIGINT(20) NOT NULL,
  step VARCHAR(50) DEFAULT NULL,
  action VARCHAR(30) DEFAULT NULL,
  operator_id BIGINT(20) DEFAULT NULL,
  operator_name VARCHAR(100) DEFAULT NULL,
  comment VARCHAR(500) DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  tenant_id BIGINT(20) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_loss_log_bill (bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) 审批流配置：L1 = CFO(cl)、L2 = CEO(zjs 146)
DELETE FROM jsh_af_flow_level WHERE flow_id IN (SELECT id FROM jsh_af_flow WHERE bill_type='报损出库');
DELETE FROM jsh_af_flow WHERE bill_type='报损出库';
INSERT INTO jsh_af_flow (flow_name, bill_type, max_level, enabled, tenant_id, delete_flag)
VALUES ('报损出库审批流', '报损出库', 2, '1', 63, '0');
SET @flow_id = (SELECT id FROM jsh_af_flow WHERE bill_type='报损出库' AND delete_flag='0' ORDER BY id DESC LIMIT 1);
INSERT INTO jsh_af_flow_level (flow_id, level, approver_type, approver_id, tenant_id, delete_flag)
VALUES (@flow_id, 1, '2', (SELECT id FROM jsh_user WHERE login_name='cl' AND tenant_id=63 LIMIT 1), 63, '0'),
       (@flow_id, 2, '2', 146, 63, '0');

-- 5) 菜单：报损管理(0901) → 报损出库(090101)。审核已并入报损出库页面，不再单独建菜单。
INSERT INTO jsh_function (number, name, parent_number, url, component, state, sort, enabled, type, icon, delete_flag)
SELECT '0901','报损管理','0','/loss','/layouts/TabLayout','','0425','','电脑版','exception','0'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT number FROM jsh_function) t WHERE t.number='0901');
INSERT INTO jsh_function (number, name, parent_number, url, component, state, sort, enabled, type, icon, delete_flag)
SELECT '090101','报损出库','0901','/bill/loss_out','/bill/LossOutList','','042501','','电脑版','profile','0'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT number FROM jsh_function) t WHERE t.number='090101');

-- 6) 角色授权（管理员4 / 租户10 / 销售代表17 / CEO21 / CFO24）
UPDATE jsh_user_business
SET value = CONCAT(value, '[285][286]')
WHERE type='RoleFunctions'
  AND value NOT LIKE '%[285]%'
  AND key_id IN (4,10,17,21,24);

-- 7) CFO 账号：登录名 cl / 密码 123456（存储为 md5），角色 CFO
INSERT INTO jsh_role (name, type, price_limit, value, description, enabled, sort, tenant_id, delete_flag)
SELECT 'CFO','全部数据','0','','财务审核角色（报损等单据 CFO 环节）',b'1','0020',63,'0'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT name FROM jsh_role) t WHERE t.name='CFO');
SET @role_id = (SELECT id FROM jsh_role WHERE name='CFO' AND tenant_id=63 ORDER BY id DESC LIMIT 1);
INSERT INTO jsh_user (username, login_name, password, leader_flag, position, department, email, phonenum,
 ismanager, isystem, status, description, remark, tenant_id, delete_flag)
SELECT '陈露','cl','e10adc3949ba59abbe56e057f20f883e','0','CFO','财务部',NULL,NULL,1,0,0,'CFO 审核账号','报损单 CFO 审核',63,'0'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT login_name FROM jsh_user) t WHERE t.login_name='cl');
SET @uid = (SELECT id FROM jsh_user WHERE login_name='cl' AND tenant_id=63 ORDER BY id DESC LIMIT 1);
INSERT INTO jsh_user_business (type, key_id, value, btn_str, tenant_id, delete_flag)
SELECT 'UserRole', @uid, CONCAT('[',@role_id,']'), NULL, 63, '0'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT CONCAT(type,key_id) k FROM jsh_user_business) t WHERE t.k = CONCAT('UserRole',@uid));
INSERT INTO jsh_user_business (type, key_id, value, btn_str, tenant_id, delete_flag)
SELECT 'RoleFunctions', @role_id, '[285][286]', '[1]', 63, '0'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT CONCAT(type,key_id) k FROM jsh_user_business) t WHERE t.k = CONCAT('RoleFunctions',@role_id));

-- 思雨食品 ERP 插件菜单角色授权（可重复执行）
-- 作用：把“插件中心”及全部插件子菜单授权给平台管理员（角色 4）和租户管理员（角色 10）。
-- 执行顺序：jsh_erp.sql -> plugin-schema.sql -> plugin-menu.sql -> 本脚本。

SET @plugin_menu_ids := (
  SELECT GROUP_CONCAT(CONCAT('[', id, ']') ORDER BY id SEPARATOR '')
  FROM jsh_function
  WHERE LEFT(number, 2) = '09' AND delete_flag <> '1'
);

UPDATE jsh_user_business
SET value = CONCAT(value, @plugin_menu_ids)
WHERE type = 'RoleFunctions'
  AND key_id IN ('4', '10')
  AND value IS NOT NULL
  AND value NOT LIKE '%[262]%'
  AND @plugin_menu_ids IS NOT NULL;

-- 说明：
-- 1. 角色 4 是平台管理员，角色 10 是租户管理员；销售经理/销售代表等角色默认不授予插件中心，需要时在“系统管理 -> 角色管理”中勾选。
-- 2. 脚本用 [262] 作为“已授权”标记，重复执行不会重复追加；如果后续新增了插件菜单，可把本脚本中的 262 改成新的首个插件菜单 ID 再执行。
-- 3. 执行后可核对：SELECT id, number, name, url FROM jsh_function WHERE LEFT(number, 2) = '09' ORDER BY number;

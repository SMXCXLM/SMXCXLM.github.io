SET NAMES utf8mb4;

UPDATE jsh_platform_config SET platform_value = '思雨食品 ERP' WHERE platform_key = 'platform_name';
UPDATE jsh_platform_config SET platform_value = '' WHERE platform_key = 'platform_url';
# 关闭登录页“注册租户”入口，避免公开注册新租户
UPDATE jsh_platform_config SET platform_value = '0' WHERE platform_key = 'register_flag';
UPDATE jsh_system_config
   SET company_name = '石门思雨食品有限公司',
       company_contacts = '',
       company_address = '',
       company_tel = ''
 WHERE id = 11;

SELECT platform_key, platform_value FROM jsh_platform_config WHERE platform_key IN ('platform_name','platform_url');
SELECT id, company_name, company_contacts, company_address, company_tel FROM jsh_system_config;

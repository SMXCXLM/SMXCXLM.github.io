-- 管伊佳 ERP 插件中心菜单初始化（jshERP v3.6）
-- 使用说明：在目标库执行本脚本，再执行同目录 plugin-role-grant.sql 自动授权给平台管理员/租户管理员；其他角色可到“系统管理 → 角色管理”勾选。
-- 注意：权限同时校验 jsh_user_business 中 RoleFunctions，未勾选的角色看不到菜单。

insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '0900','插件中心','0','/pluginCenter','/layouts/TabLayout',b'1','0500',b'1','电脑版',null,'appstore','0'
from dual where not exists (select 1 from jsh_function where url='/pluginCenter' and delete_flag<>'1');

insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090001','生产管理','0900','/plugin/prod','/plugin/index',b'1','0501',b'1','电脑版',null,'build','0' from dual where not exists (select 1 from jsh_function where url='/plugin/prod' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090002','总账管理','0900','/plugin/ledger','/plugin/index',b'1','0502',b'1','电脑版',null,'account-book','0' from dual where not exists (select 1 from jsh_function where url='/plugin/ledger' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090003','盘点管理','0900','/plugin/check','/plugin/index',b'1','0503',b'1','电脑版',null,'audit','0' from dual where not exists (select 1 from jsh_function where url='/plugin/check' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090004','多级审核','0900','/plugin/audit','/plugin/index',b'1','0504',b'1','电脑版',null,'solution','0' from dual where not exists (select 1 from jsh_function where url='/plugin/audit' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090005','多属性库存','0900','/plugin/multi','/plugin/index',b'1','0505',b'1','电脑版',null,'appstore','0' from dual where not exists (select 1 from jsh_function where url='/plugin/multi' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090006','批次库存','0900','/plugin/batch','/plugin/index',b'1','0506',b'1','电脑版',null,'tags','0' from dual where not exists (select 1 from jsh_function where url='/plugin/batch' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090007','序列号统计','0900','/plugin/serial','/plugin/index',b'1','0507',b'1','电脑版',null,'barcode','0' from dual where not exists (select 1 from jsh_function where url='/plugin/serial' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090008','负库存统计','0900','/plugin/negative','/plugin/index',b'1','0508',b'1','电脑版',null,'warning','0' from dual where not exists (select 1 from jsh_function where url='/plugin/negative' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090009','利润报表','0900','/plugin/profit','/plugin/index',b'1','0509',b'1','电脑版',null,'line-chart','0' from dual where not exists (select 1 from jsh_function where url='/plugin/profit' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090010','销售利润/占比/排行','0900','/plugin/sale','/plugin/index',b'1','0510',b'1','电脑版',null,'bar-chart','0' from dual where not exists (select 1 from jsh_function where url='/plugin/sale' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090011','单据导出','0900','/plugin/export','/plugin/index',b'1','0511',b'1','电脑版',null,'export','0' from dual where not exists (select 1 from jsh_function where url='/plugin/export' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090012','三联打印','0900','/plugin/print','/plugin/index',b'1','0512',b'1','电脑版',null,'printer','0' from dual where not exists (select 1 from jsh_function where url='/plugin/print' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090013','车辆管理','0900','/plugin/vehicle','/plugin/index',b'1','0513',b'1','电脑版',null,'car','0' from dual where not exists (select 1 from jsh_function where url='/plugin/vehicle' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090014','客户跟进','0900','/plugin/follow','/plugin/index',b'1','0514',b'1','电脑版',null,'message','0' from dual where not exists (select 1 from jsh_function where url='/plugin/follow' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090015','固定资产','0900','/plugin/asset','/plugin/index',b'1','0515',b'1','电脑版',null,'bank','0' from dual where not exists (select 1 from jsh_function where url='/plugin/asset' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090016','业绩提成','0900','/plugin/commission','/plugin/index',b'1','0516',b'1','电脑版',null,'red-envelope','0' from dual where not exists (select 1 from jsh_function where url='/plugin/commission' and delete_flag<>'1');

update jsh_function set name='销售分析' where url='/plugin/sale' and delete_flag<>'1';

insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090017','销售利润','0900','/plugin/salesprofit','/plugin/index',b'1','0517',b'1','电脑版',null,'fund','0' from dual where not exists (select 1 from jsh_function where url='/plugin/salesprofit' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090018','收支项明细','0900','/plugin/income','/plugin/index',b'1','0518',b'1','电脑版',null,'account-book','0' from dual where not exists (select 1 from jsh_function where url='/plugin/income' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090019','收付款明细','0900','/plugin/payment','/plugin/index',b'1','0519',b'1','电脑版',null,'pay-circle','0' from dual where not exists (select 1 from jsh_function where url='/plugin/payment' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090020','条码打印','0900','/plugin/barcode','/plugin/index',b'1','0520',b'1','电脑版',null,'barcode','0' from dual where not exists (select 1 from jsh_function where url='/plugin/barcode' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090021','发票管理','0900','/plugin/invoice','/plugin/index',b'1','0521',b'1','电脑版',null,'file-text','0' from dual where not exists (select 1 from jsh_function where url='/plugin/invoice' and delete_flag<>'1');
insert into jsh_function(number,name,parent_number,url,component,state,sort,enabled,type,push_btn,icon,delete_flag)
select '090022','零售收银','0900','/plugin/pos','/plugin/index',b'1','0522',b'1','电脑版',null,'shopping-cart','0' from dual where not exists (select 1 from jsh_function where url='/plugin/pos' and delete_flag<>'1');

-- 查看新菜单 ID（供角色授权核对）
select id,number,name,url from jsh_function where number like '09%' and delete_flag<>'1' order by number;

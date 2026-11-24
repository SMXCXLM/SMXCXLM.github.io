import { getAction, postAction } from '@/api/manage'

const BASE = '/api/plugin/jsh-plugin-suite'

/** 插件统一 GET */
export function pluginGet(path, params) {
  return getAction(BASE + path, params)
}

/** 插件统一 POST */
export function pluginPost(path, data) {
  return postAction(BASE + path, data)
}

// 生产管理
export const production = {
  bomList: (p) => pluginGet('/production/bom/list', p),
  bomGet: (p) => pluginGet('/production/bom/get', p),
  bomSave: (p) => pluginPost('/production/bom/save', p),
  bomDelete: (p) => pluginPost('/production/bom/delete', p),
  taskList: (p) => pluginGet('/production/task/list', p),
  taskGet: (p) => pluginGet('/production/task/get', p),
  taskSave: (p) => pluginPost('/production/task/save', p),
  taskDelete: (p) => pluginPost('/production/task/delete', p),
  materialList: (p) => pluginGet('/production/material/list', p),
  materialSave: (p) => pluginPost('/production/material/save', p),
  materialDelete: (p) => pluginPost('/production/material/delete', p),
  reportList: (p) => pluginGet('/production/process/report/list', p),
  reportSave: (p) => pluginPost('/production/process/report/save', p),
  reportDelete: (p) => pluginPost('/production/process/report/delete', p),
  inspectionList: (p) => pluginGet('/production/inspection/list', p),
  inspectionSave: (p) => pluginPost('/production/inspection/save', p),
  inspectionDelete: (p) => pluginPost('/production/inspection/delete', p),
  instockList: (p) => pluginGet('/production/instock/list', p),
  instockSave: (p) => pluginPost('/production/instock/save', p),
  instockDelete: (p) => pluginPost('/production/instock/delete', p)
}

// 总账
export const ledger = {
  setList: (p) => pluginGet('/ledger/set/list', p),
  setSave: (p) => pluginPost('/ledger/set/save', p),
  setDelete: (p) => pluginPost('/ledger/set/delete', p),
  subjectList: (p) => pluginGet('/ledger/subject/list', p),
  subjectSave: (p) => pluginPost('/ledger/subject/save', p),
  subjectDelete: (p) => pluginPost('/ledger/subject/delete', p),
  subjectInit: (p) => pluginPost('/ledger/subject/init', p),
  voucherList: (p) => pluginGet('/ledger/voucher/list', p),
  voucherGet: (p) => pluginGet('/ledger/voucher/get', p),
  voucherSave: (p) => pluginPost('/ledger/voucher/save', p),
  voucherAudit: (p) => pluginPost('/ledger/voucher/audit', p),
  voucherDelete: (p) => pluginPost('/ledger/voucher/delete', p),
  periodList: (p) => pluginGet('/ledger/period/list', p),
  periodClose: (p) => pluginPost('/ledger/period/close', p),
  periodOpen: (p) => pluginPost('/ledger/period/open', p),
  bookDetail: (p) => pluginGet('/ledger/book/detail', p),
  bookGeneral: (p) => pluginGet('/ledger/book/general', p),
  subjectBalance: (p) => pluginGet('/ledger/book/subjectBalance', p),
  subjectSummary: (p) => pluginGet('/ledger/book/subjectSummary', p),
  projectDetail: (p) => pluginGet('/ledger/book/projectDetail', p),
  projectBalance: (p) => pluginGet('/ledger/book/projectBalance', p),
  reportProfit: (p) => pluginGet('/ledger/report/profit', p),
  reportCash: (p) => pluginGet('/ledger/report/cashflow', p),
  reportBalance: (p) => pluginGet('/ledger/report/balance', p)
}

// 盘点
export const stockCheck = {
  list: (p) => pluginGet('/stockCheck/order/list', p),
  get: (p) => pluginGet('/stockCheck/order/get', p),
  create: (p) => pluginPost('/stockCheck/order/create', p),
  first: (p) => pluginPost('/stockCheck/order/first', p),
  second: (p) => pluginPost('/stockCheck/order/second', p),
  finish: (p) => pluginPost('/stockCheck/order/finish', p),
  del: (p) => pluginPost('/stockCheck/order/delete', p),
  addItem: (p) => pluginPost('/stockCheck/order/addItem', p)
}

// 审核
export const audit = {
  flowList: (p) => pluginGet('/auditFlow/flow/list', p),
  flowGet: (p) => pluginGet('/auditFlow/flow/get', p),
  flowSave: (p) => pluginPost('/auditFlow/flow/save', p),
  flowDelete: (p) => pluginPost('/auditFlow/flow/delete', p),
  start: (p) => pluginPost('/auditFlow/instance/start', p),
  approve: (p) => pluginPost('/auditFlow/instance/approve', p),
  withdraw: (p) => pluginPost('/auditFlow/instance/withdraw', p),
  del: (p) => pluginPost('/auditFlow/instance/delete', p),
  history: (p) => pluginGet('/auditFlow/instance/history', p),
  todo: (p) => pluginGet('/auditFlow/task/todo', p),
  done: (p) => pluginGet('/auditFlow/task/done', p),
  mine: (p) => pluginGet('/auditFlow/instance/mine', p)
}

// 打印
export const printTpl = {
  types: (p) => pluginGet('/printTemplate/types', p),
  list: (p) => pluginGet('/printTemplate/list', p),
  save: (p) => pluginPost('/printTemplate/save', p),
  del: (p) => pluginPost('/printTemplate/delete', p),
  data: (p) => pluginGet('/printTemplate/data', p)
}

// 固定资产
export const fixedAsset = {
  list: (p) => pluginGet('/fixedAsset/asset/list', p),
  save: (p) => pluginPost('/fixedAsset/asset/save', p),
  del: (p) => pluginPost('/fixedAsset/asset/delete', p),
  depreciation: (p) => pluginGet('/fixedAsset/asset/depreciation', p),
  users: (p) => pluginGet('/fixedAsset/asset/users', p),
  changeUser: (p) => pluginPost('/fixedAsset/asset/changeUser', p)
}

// 提成
export const commission = {
  ruleList: (p) => pluginGet('/commission/rule/list', p),
  ruleGet: (p) => pluginGet('/commission/rule/get', p),
  ruleSave: (p) => pluginPost('/commission/rule/save', p),
  ruleDelete: (p) => pluginPost('/commission/rule/delete', p),
  calc: (p) => pluginGet('/commission/calc', p),
  save: (p) => pluginPost('/commission/save', p),
  records: (p) => pluginGet('/commission/record/list', p)
}

// 统计
export const stats = {
  multiAttr: (p) => pluginGet('/stockStat/multiAttr', p),
  batch: (p) => pluginGet('/stockStat/batch', p),
  serial: (p) => pluginGet('/stockStat/serial', p),
  negative: (p) => pluginGet('/stockStat/negative', p),
  profitReport: (p) => pluginGet('/profitStat/profitReport', p),
  saleProfit: (p) => pluginGet('/profitStat/saleProfit', p),
  saleShare: (p) => pluginGet('/profitStat/saleShare', p),
  saleRank: (p) => pluginGet('/profitStat/saleRank', p),
  saleProfitDetail: (p) => pluginGet('/profitStat/saleProfitDetail', p),
  exportTypes: (p) => pluginGet('/billExport/types', p),
  exportList: (p) => pluginGet('/billExport/list', p),
  exportDetail: (p) => pluginGet('/billExport/detail', p)
}

// 通用档案
export const crud = {
  list: (p) => pluginGet('/crud/list', p),
  get: (p) => pluginGet('/crud/get', p),
  save: (p) => pluginPost('/crud/save', p),
  del: (p) => pluginPost('/crud/delete', p),
  options: (p) => pluginGet('/crud/options', p)
}

// 销售利润模板
export const salesProfit = {
  bills: (p) => pluginGet('/salesProfit/bills', p),
  items: (p) => pluginGet('/salesProfit/items', p),
  detail: (p) => pluginGet('/salesProfit/detail', p)
}

// 收支项 / 收付款明细
export const finance = {
  income: (p) => pluginGet('/finance/income', p),
  payment: (p) => pluginGet('/finance/payment', p),
  items: (p) => pluginGet('/finance/items', p)
}

// 条码打印
export const barcode = {
  products: (p) => pluginGet('/barcode/products', p),
  all: (p) => pluginGet('/barcode/products/all', p),
  templates: (p) => pluginGet('/barcode/template/list', p),
  defaultTemplate: (p) => pluginGet('/barcode/template/default', p),
  saveTemplate: (p) => pluginPost('/barcode/template/save', p),
  deleteTemplate: (p) => pluginPost('/barcode/template/delete', p)
}

// 发票管理
export const invoice = {
  follow: (p) => pluginGet('/invoice/follow', p),
  records: (p) => pluginGet('/invoice/records', p),
  register: (p) => pluginPost('/invoice/register', p),
  cancel: (p) => pluginPost('/invoice/cancel', p),
  del: (p) => pluginPost('/invoice/delete', p),
  stats: (p) => pluginGet('/invoice/stats', p)
}

// 零售收银
export const pos = {
  init: (p) => pluginGet('/pos/init', p),
  search: (p) => pluginGet('/pos/search', p),
  memberSearch: (p) => pluginGet('/pos/member/search', p),
  checkout: (p) => pluginPost('/pos/checkout', p),
  hold: (p) => pluginPost('/pos/hold', p),
  holds: (p) => pluginGet('/pos/holds', p),
  holdGet: (p) => pluginGet('/pos/hold/get', p),
  holdDelete: (p) => pluginPost('/pos/hold/delete', p),
  receipt: (p) => pluginGet('/pos/receipt', p)
}

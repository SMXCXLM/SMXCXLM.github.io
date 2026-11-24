<template>
  <div>
    <a-radio-group v-model="mode" button-style="solid" @change="switchMode" style="margin-bottom:12px">
      <a-radio-button value="income">收支项明细</a-radio-button>
      <a-radio-button value="payment">收付款明细</a-radio-button>
    </a-radio-group>

    <a-form layout="inline" style="margin-bottom:10px">
      <a-form-item label="开始">
        <a-date-picker v-model="q.beginTime" valueFormat="YYYY-MM-DD" style="width:130px"/>
      </a-form-item>
      <a-form-item label="结束">
        <a-date-picker v-model="q.endTime" valueFormat="YYYY-MM-DD" style="width:130px"/>
      </a-form-item>
      <a-form-item label="单据号">
        <a-input v-model="q.billNo" placeholder="财务单据号" style="width:150px" allowClear/>
      </a-form-item>
      <a-form-item v-if="mode === 'income'" label="收支项目">
        <a-input v-model="q.itemName" placeholder="收支项目" style="width:130px" allowClear/>
      </a-form-item>
      <a-form-item v-if="mode === 'payment'" label="源单号">
        <a-input v-model="q.sourceBillNumber" placeholder="销售/采购单号" style="width:150px" allowClear/>
      </a-form-item>
      <a-form-item label="类型">
        <a-select v-model="q.type" style="width:100px" allowClear placeholder="全部">
          <a-select-option v-for="t in typeOptions" :key="t" :value="t">{{t}}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="search">查询</a-button>
      </a-form-item>
      <a-form-item>
        <a-button @click="reset">重置</a-button>
        <a-button style="margin-left:8px" @click="doExport">导出 Excel</a-button>
        <a-button style="margin-left:8px" @click="doPrint">打印</a-button>
      </a-form-item>
    </a-form>

    <a-alert v-if="summary" type="info" show-icon style="margin-bottom:10px" :message="summaryText"/>

    <a-table size="middle" bordered rowKey="itemId" :columns="columns" :dataSource="rows" :loading="loading"
             :pagination="pagination" @change="onPageChange" :scroll="{x:1600}"/>
  </div>
</template>

<script>
  import { finance } from '@/api/plugin'
  import { exportExcel, printReport } from './reportUtil'

  const EMPTY = () => ({ beginTime: null, endTime: null, billNo: '', itemName: '', sourceBillNumber: '', type: null })

  export default {
    name: 'PluginFinanceDetail',
    props: { tab: { type: String, default: null } },
    data() {
      return {
        mode: this.tab === 'payment' ? 'payment' : 'income',
        q: EMPTY(),
        rows: [],
        summary: null,
        loading: false,
        pagination: { current: 1, pageSize: 20, total: 0, showSizeChanger: true }
      }
    },
    computed: {
      typeOptions() {
        return this.mode === 'payment' ? ['收款', '付款'] : ['收入', '支出']
      },
      columns() {
        if (this.mode === 'payment') {
          return [
            { title: '收付款单号', dataIndex: 'billNo', width: 160 },
            { title: '类型', dataIndex: 'billType', width: 70 },
            { title: '单据时间', dataIndex: 'billTime', width: 150 },
            { title: '往来单位', dataIndex: 'organName', width: 140 },
            { title: '金额', dataIndex: 'amount', width: 100 },
            { title: '应收/付', dataIndex: 'needDebt', width: 100 },
            { title: '已收/付', dataIndex: 'finishDebt', width: 100 },
            { title: '结算账户', dataIndex: 'accountName', width: 110 },
            { title: '源单号', dataIndex: 'sourceBillNumber', width: 160 },
            { title: '源单类型', dataIndex: 'sourceSubType', width: 90 },
            { title: '源单金额', dataIndex: 'sourceAmount', width: 100 },
            { title: '源单时间', dataIndex: 'sourceTime', width: 150 },
            { title: '备注', dataIndex: 'billRemark', width: 150 }
          ]
        }
        return [
          { title: '财务单据号', dataIndex: 'billNo', width: 160 },
          { title: '类型', dataIndex: 'billType', width: 70 },
          { title: '单据时间', dataIndex: 'billTime', width: 150 },
          { title: '往来单位', dataIndex: 'organName', width: 140 },
          { title: '收支项目', dataIndex: 'itemName', width: 130 },
          { title: '金额', dataIndex: 'amount', width: 100 },
          { title: '应收/付', dataIndex: 'needDebt', width: 100 },
          { title: '已收/付', dataIndex: 'finishDebt', width: 100 },
          { title: '结算账户', dataIndex: 'accountName', width: 110 },
          { title: '单据金额', dataIndex: 'totalPrice', width: 100 },
          { title: '备注', dataIndex: 'billRemark', width: 160 }
        ]
      },
      summaryText() {
        const s = this.summary || {}
        if (this.mode === 'payment') {
          return `单据数 ${s.billCount || 0}，收款合计 ${s.receiptAmount || 0} 元，付款合计 ${s.paymentAmount || 0} 元，净额 ${s.balance || 0} 元`
        }
        return `单据数 ${s.billCount || 0}，收入合计 ${s.incomeAmount || 0} 元，支出合计 ${s.expenseAmount || 0} 元，净额 ${s.balance || 0} 元`
      }
    },
    watch: {
      tab(v) {
        if (v === 'income' || v === 'payment') {
          this.mode = v
          this.reset()
        }
      }
    },
    created() {
      this.load()
    },
    methods: {
      load() {
        this.loading = true
        const fn = this.mode === 'payment' ? finance.payment : finance.income
        const params = Object.assign({ currentPage: this.pagination.current, pageSize: this.pagination.pageSize }, this.q)
        fn(params).then(r => {
          const d = r.data || {}
          this.rows = d.rows || []
          this.summary = d.summary || null
          this.pagination.total = d.total || 0
        }).finally(() => { this.loading = false })
      },
      switchMode() {
        this.q = EMPTY()
        this.rows = []
        this.summary = null
        this.pagination.current = 1
        this.load()
      },
      search() {
        this.pagination.current = 1
        this.load()
      },
      reset() {
        this.q = EMPTY()
        this.pagination.current = 1
        this.load()
      },
      onPageChange(p) {
        this.pagination.current = p.current
        this.pagination.pageSize = p.pageSize
        this.load()
      },
      doExport() {
        exportExcel((this.mode === 'income' ? '收支项明细' : '收付款明细') + '.xlsx', this.columns, this.rows)
      },
      doPrint() {
        printReport(this.mode === 'income' ? '收支项明细表' : '收付款明细表', this.columns, this.rows, {
          subtitle: (this.q.beginTime || '') + ' 至 ' + (this.q.endTime || ''),
          summary: this.summaryText
        })
      }
    }
  }
</script>

<style scoped>
</style>

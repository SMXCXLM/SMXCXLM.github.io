<template>
  <div>
    <a-radio-group v-model="mode" button-style="solid" @change="switchMode" style="margin-bottom:12px">
      <a-radio-button value="bills">单据利润</a-radio-button>
      <a-radio-button value="items">商品明细利润</a-radio-button>
    </a-radio-group>

    <a-form layout="inline" style="margin-bottom:10px">
      <a-form-item label="开始">
        <a-date-picker v-model="q.beginTime" valueFormat="YYYY-MM-DD" style="width:130px"/>
      </a-form-item>
      <a-form-item label="结束">
        <a-date-picker v-model="q.endTime" valueFormat="YYYY-MM-DD" style="width:130px"/>
      </a-form-item>
      <a-form-item label="单据编号">
        <a-input v-model="q.billNumber" placeholder="单据编号" style="width:150px" allowClear/>
      </a-form-item>
      <a-form-item label="条码">
        <a-input v-model="q.barCode" placeholder="商品条码" style="width:130px" allowClear/>
      </a-form-item>
      <a-form-item label="名称">
        <a-input v-model="q.materialName" placeholder="商品名称" style="width:130px" allowClear/>
      </a-form-item>
      <a-form-item label="规格">
        <a-input v-model="q.standard" placeholder="规格" style="width:110px" allowClear/>
      </a-form-item>
      <a-form-item label="类型">
        <a-select v-model="q.subType" style="width:110px" allowClear placeholder="全部">
          <a-select-option value="销售">销售</a-select-option>
          <a-select-option value="零售">零售</a-select-option>
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

    <a-alert v-if="summary" type="info" show-icon style="margin-bottom:10px"
             :message="summaryText"/>

    <a-table size="middle" bordered :rowKey="mode === 'bills' ? 'billId' : 'itemId'" :columns="columns"
             :dataSource="rows" :loading="loading" :pagination="pagination" @change="onPageChange"
             :scroll="{x:1400}">
      <span v-if="mode === 'bills'" slot="detail" slot-scope="text, record">
        <a @click="showDetail(record)">查看明细</a>
      </span>
    </a-table>

    <a-modal title="单据商品利润明细" :visible="detailVisible" @cancel="detailVisible=false" footer="" width="960px">
      <a-alert type="success" show-icon style="margin-bottom:8px"
               :message="`单据 ${detailBill.billNumber || ''}：收入 ${detailSummary.amount || 0} 元，成本 ${detailSummary.cost || 0} 元，利润 ${detailSummary.profit || 0} 元`"/>
      <a-table size="small" bordered rowKey="itemId" :columns="detailColumns" :dataSource="detailRows" :pagination="false"/>
    </a-modal>
  </div>
</template>

<script>
  import { salesProfit } from '@/api/plugin'
  import { exportExcel, printReport } from './reportUtil'

  const EMPTY = () => ({
    beginTime: null, endTime: null, billNumber: '', barCode: '', materialName: '', standard: '', subType: null
  })

  export default {
    name: 'PluginSalesProfit',
    data() {
      return {
        mode: 'bills',
        q: EMPTY(),
        rows: [],
        summary: null,
        loading: false,
        pagination: { current: 1, pageSize: 20, total: 0, showSizeChanger: true },
        detailVisible: false,
        detailRows: [],
        detailSummary: {},
        detailBill: {}
      }
    },
    computed: {
      columns() {
        if (this.mode === 'items') {
          return [
            { title: '单据编号', dataIndex: 'billNumber', width: 160 },
            { title: '类型', dataIndex: 'subType', width: 70 },
            { title: '业务时间', dataIndex: 'operTime', width: 150 },
            { title: '客户', dataIndex: 'organName', width: 130 },
            { title: '商品名称', dataIndex: 'materialName', width: 140 },
            { title: '规格', dataIndex: 'standard', width: 90 },
            { title: '型号', dataIndex: 'model', width: 90 },
            { title: '条码', dataIndex: 'barCode', width: 110 },
            { title: '单位', dataIndex: 'unit', width: 60 },
            { title: '数量', dataIndex: 'qty', width: 80 },
            { title: '收入', dataIndex: 'amount', width: 90 },
            { title: '成本', dataIndex: 'cost', width: 90 },
            { title: '利润', dataIndex: 'profit', width: 90 },
            { title: '利润率%', dataIndex: 'profitRate', width: 90 }
          ]
        }
        return [
          { title: '单据编号', dataIndex: 'billNumber', width: 170 },
          { title: '类型', dataIndex: 'subType', width: 70 },
          { title: '业务时间', dataIndex: 'operTime', width: 150 },
          { title: '客户', dataIndex: 'organName', width: 140 },
          { title: '明细数', dataIndex: 'itemCount', width: 80 },
          { title: '数量', dataIndex: 'qty', width: 80 },
          { title: '收入', dataIndex: 'amount', width: 100 },
          { title: '成本', dataIndex: 'cost', width: 100 },
          { title: '利润', dataIndex: 'profit', width: 100 },
          { title: '利润率%', dataIndex: 'profitRate', width: 90 },
          { title: '操作', dataIndex: 'detail', width: 90, scopedSlots: { customRender: 'detail' } }
        ]
      },
      detailColumns() {
        return [
          { title: '商品名称', dataIndex: 'materialName' },
          { title: '规格', dataIndex: 'standard' },
          { title: '型号', dataIndex: 'model' },
          { title: '条码', dataIndex: 'barCode' },
          { title: '单位', dataIndex: 'unit' },
          { title: '数量', dataIndex: 'qty' },
          { title: '收入', dataIndex: 'amount' },
          { title: '成本', dataIndex: 'cost' },
          { title: '利润', dataIndex: 'profit' }
        ]
      },
      summaryText() {
        const s = this.summary || {}
        const bill = s.billCount == null ? '' : `单据数 ${s.billCount}，`
        return `${bill}数量 ${s.qty || 0}，收入 ${s.amount || 0} 元，成本 ${s.cost || 0} 元，利润 ${s.profit || 0} 元，利润率 ${s.profitRate || 0}%`
      }
    },
    created() {
      this.load()
    },
    methods: {
      params() {
        return Object.assign({ currentPage: this.pagination.current, pageSize: this.pagination.pageSize }, this.q)
      },
      load() {
        this.loading = true
        const fn = this.mode === 'items' ? salesProfit.items : salesProfit.bills
        fn(this.params()).then(r => {
          const d = r.data || {}
          this.rows = d.rows || []
          this.summary = d.summary || null
          this.pagination.total = d.total || 0
        }).finally(() => { this.loading = false })
      },
      switchMode() {
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
      showDetail(record) {
        salesProfit.detail({ billId: record.billId }).then(r => {
          this.detailRows = (r.data || {}).rows || []
          this.detailSummary = (r.data || {}).summary || {}
          this.detailBill = record
          this.detailVisible = true
        })
      },
      doExport() {
        exportExcel('销售利润-' + (this.mode === 'items' ? '商品明细' : '单据汇总') + '.xlsx', this.columns, this.rows)
      },
      doPrint() {
        printReport('销售利润' + (this.mode === 'items' ? '商品明细' : '单据汇总'), this.columns, this.rows, {
          subtitle: this.dateText(),
          summary: this.summaryText
        })
      },
      dateText() {
        if (!this.q.beginTime && !this.q.endTime) return '全部时间段'
        return (this.q.beginTime || '') + ' 至 ' + (this.q.endTime || '')
      }
    }
  }
</script>

<style scoped>
</style>

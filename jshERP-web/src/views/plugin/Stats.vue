<template>
  <div>
    <a-radio-group v-model="mode" button-style="solid" @change="switchMode" style="margin-bottom:12px">
      <a-radio-button value="multi">多属性库存</a-radio-button>
      <a-radio-button value="batch">批次库存</a-radio-button>
      <a-radio-button value="serial">序列号统计</a-radio-button>
      <a-radio-button value="negative">负库存</a-radio-button>
      <a-radio-button value="profit">利润报表</a-radio-button>
      <a-radio-button value="sale">销售分析</a-radio-button>
      <a-radio-button value="export">单据导出</a-radio-button>
    </a-radio-group>

    <a-form layout="inline" style="margin-bottom:10px">
      <a-form-item v-if="isStock || mode==='profit' || mode==='sale' || mode==='export'" label="开始">
        <a-date-picker v-model="q.beginTime" valueFormat="YYYY-MM-DD" style="width:130px"/>
      </a-form-item>
      <a-form-item v-if="isStock || mode==='profit' || mode==='sale' || mode==='export'" label="结束">
        <a-date-picker v-model="q.endTime" valueFormat="YYYY-MM-DD" style="width:130px"/>
      </a-form-item>
      <a-form-item v-if="mode!=='export'" label="仓库">
        <a-select v-model="q.depotId" style="width:140px" allowClear>
          <a-select-option v-for="d in options.depot" :key="d.value" :value="d.value">{{d.label}}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="mode!=='export'" label="商品">
        <a-input v-model="q.materialParam" placeholder="名称/条码/规格" style="width:160px" allowClear/>
      </a-form-item>
      <a-form-item v-if="mode==='serial'" label="序列号">
        <a-input v-model="q.serialNumber" style="width:150px" allowClear/>
      </a-form-item>
      <a-form-item v-if="mode==='sale'">
        <a-select v-model="saleView" style="width:130px" @change="load">
          <a-select-option value="profit">销售利润</a-select-option>
          <a-select-option value="share">销售占比</a-select-option>
          <a-select-option value="rank">销售排行</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="mode==='export'">
        <a-select v-model="q.subType" style="width:150px" placeholder="单据类型" allowClear>
          <a-select-option v-for="t in exportTypes" :key="t.value" :value="t.value">{{t.label}}</a-select-option>
        </a-select>
        <a-input v-model="q.number" placeholder="单据号" style="width:150px;margin-left:8px" allowClear/>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="load">查询</a-button>
      </a-form-item>
      <a-form-item v-if="mode==='export'">
        <a-button @click="doExport('list')">导出列表</a-button>
        <a-button style="margin-left:8px" @click="doExport('detail')">导出明细</a-button>
      </a-form-item>
    </a-form>

    <a-alert v-if="mode==='profit' && summary" type="info" show-icon style="margin-bottom:10px"
             :message="`收入 ${summary.amount||0} 元，成本 ${summary.cost||0} 元，利润 ${summary.profit||0} 元，利润率 ${summary.profitRate||0}%`"/>

    <a-table size="middle" bordered rowKey="id" :columns="columns" :dataSource="rows" :loading="loading"
             :pagination="pagination" @change="onPageChange" :scroll="{x:1200}">
      <span slot="saleDetail" slot-scope="text, record"><a @click="showSaleDetail(record)">查看明细</a></span>
    </a-table>

    <a-modal title="销售利润明细" :visible="detailVisible" @cancel="detailVisible=false" footer="" width="900px">
      <a-alert type="success" show-icon style="margin-bottom:8px"
               :message="`单据 ${detailBill.billNumber||''}：收入 ${detailSummary.amount||0}，成本 ${detailSummary.cost||0}，利润 ${detailSummary.profit||0}`"/>
      <a-table size="small" bordered rowKey="itemId" :columns="detailColumns" :dataSource="detailRows" :pagination="false"/>
    </a-modal>
  </div>
</template>

<script>
  import { stats, crud } from '@/api/plugin'
  import { axios } from '@/utils/request'

  export default {
    name: 'PluginStats',
    props: { tab: { type: String, default: null } },
    data() {
      return {
        mode: this.tab || 'multi',
        saleView: 'profit',
        q: { beginTime: null, endTime: null, depotId: null, materialParam: '', serialNumber: '', subType: null, number: '' },
        options: { depot: [] },
        exportTypes: [],
        rows: [],
        summary: null,
        loading: false,
        pagination: { current: 1, pageSize: 20, total: 0, showSizeChanger: true },
        detailVisible: false,
        detailRows: [],
        detailSummary: {},
        detailBill: {},
        detailColumns: [
          { title: '商品', dataIndex: 'materialName' },
          { title: '规格', dataIndex: 'standard' },
          { title: '条码', dataIndex: 'barCode' },
          { title: '数量', dataIndex: 'qty' },
          { title: '金额', dataIndex: 'amount' },
          { title: '成本', dataIndex: 'cost' },
          { title: '利润', dataIndex: 'profit' }
        ]
      }
    },
    computed: {
      isStock() {
        return ['multi', 'batch', 'serial', 'negative'].includes(this.mode)
      },
      columns() {
        if (this.mode === 'multi') {
          return [
            { title: '仓库', dataIndex: 'depotName' },
            { title: '商品', dataIndex: 'materialName' },
            { title: '规格', dataIndex: 'standard' },
            { title: '型号', dataIndex: 'model' },
            { title: '条码', dataIndex: 'barCode' },
            { title: '多属性', dataIndex: 'sku' },
            { title: '单位', dataIndex: 'unit' },
            { title: '库存数量', dataIndex: 'qty' },
            { title: '库存金额', dataIndex: 'amount' }
          ]
        }
        if (this.mode === 'batch') {
          return [
            { title: '仓库', dataIndex: 'depotName' },
            { title: '商品', dataIndex: 'materialName' },
            { title: '规格', dataIndex: 'standard' },
            { title: '条码', dataIndex: 'barCode' },
            { title: '批号', dataIndex: 'batchNumber' },
            { title: '有效期', dataIndex: 'expirationDate' },
            { title: '单位', dataIndex: 'unit' },
            { title: '数量', dataIndex: 'qty' }
          ]
        }
        if (this.mode === 'serial') {
          return [
            { title: '序列号', dataIndex: 'serialNumber' },
            { title: '商品', dataIndex: 'materialName' },
            { title: '规格', dataIndex: 'standard' },
            { title: '型号', dataIndex: 'model' },
            { title: '条码', dataIndex: 'barCode' },
            { title: '仓库', dataIndex: 'depotName' },
            { title: '是否已出库', dataIndex: 'isSell', customRender: t => (t === '1' ? '已出库' : '在库') }
          ]
        }
        if (this.mode === 'negative') {
          return [
            { title: '仓库', dataIndex: 'depotName' },
            { title: '商品', dataIndex: 'materialName' },
            { title: '规格', dataIndex: 'standard' },
            { title: '条码', dataIndex: 'barCode' },
            { title: '库存数量', dataIndex: 'qty' }
          ]
        }
        if (this.mode === 'profit') {
          return [
            { title: '单据号', dataIndex: 'billNumber' },
            { title: '类型', dataIndex: 'subType' },
            { title: '时间', dataIndex: 'operTime' },
            { title: '商品', dataIndex: 'materialName' },
            { title: '数量', dataIndex: 'qty' },
            { title: '收入', dataIndex: 'amount' },
            { title: '成本', dataIndex: 'cost' },
            { title: '利润', dataIndex: 'profit' },
            { title: '利润率%', dataIndex: 'profitRate' }
          ]
        }
        if (this.mode === 'sale') {
          if (this.saleView === 'profit') {
            return [
              { title: '单据号', dataIndex: 'billNumber' },
              { title: '类型', dataIndex: 'subType' },
              { title: '时间', dataIndex: 'operTime' },
              { title: '明细数', dataIndex: 'itemCount' },
              { title: '收入', dataIndex: 'amount' },
              { title: '成本', dataIndex: 'cost' },
              { title: '利润', dataIndex: 'profit' },
              { title: '操作', dataIndex: 'op', width: 90, scopedSlots: { customRender: 'saleDetail' } }
            ]
          }
          return [
            { title: '排名', dataIndex: 'rank', width: 70 },
            { title: '商品', dataIndex: 'materialName' },
            { title: '规格', dataIndex: 'standard' },
            { title: '条码', dataIndex: 'barCode' },
            { title: '销量', dataIndex: 'qty' },
            { title: '金额', dataIndex: 'amount' },
            { title: '占比%', dataIndex: 'share' }
          ]
        }
        return [
          { title: '单据号', dataIndex: 'number' },
          { title: '单据类型', dataIndex: 'subType' },
          { title: '业务时间', dataIndex: 'operTime' },
          { title: '金额', dataIndex: 'totalPrice' },
          { title: '结算方式', dataIndex: 'payType' },
          { title: '状态', dataIndex: 'status' },
          { title: '业务员', dataIndex: 'salesMan' },
          { title: '明细数', dataIndex: 'itemCount' }
        ]
      }
    },
    watch: {
      tab(v) {
        if (v) {
          this.mode = v === 'sale' ? 'sale' : v
          this.load()
        }
      }
    },
    created() {
      crud.options({ type: 'depot' }).then(r => { this.options.depot = r.data || [] })
      stats.exportTypes().then(r => { this.exportTypes = r.data || [] })
      this.load()
    },
    methods: {
      switchMode() {
        this.pagination.current = 1
        this.rows = []
        this.summary = null
        this.load()
      },
      load() {
        if (this.mode === 'sale' && this.saleView !== 'profit') {
          this.loadSales()
          return
        }
        this.loading = true
        const p = Object.assign({ currentPage: this.pagination.current, pageSize: this.pagination.pageSize }, this.q)
        let fn
        if (this.mode === 'multi') fn = stats.multiAttr
        else if (this.mode === 'batch') fn = stats.batch
        else if (this.mode === 'serial') fn = stats.serial
        else if (this.mode === 'negative') fn = stats.negative
        else if (this.mode === 'profit') fn = stats.profitReport
        else if (this.mode === 'sale') fn = stats.saleProfit
        else fn = stats.exportList
        fn(p).then(r => {
          const d = r.data || {}
          this.rows = d.rows || []
          this.pagination.total = d.total || 0
          this.summary = d.summary || null
        }).finally(() => { this.loading = false })
      },
      loadSales() {
        this.loading = true
        const p = Object.assign({}, this.q, { top: 50 })
        const fn = this.saleView === 'share' ? stats.saleShare : stats.saleRank
        fn(p).then(r => {
          const d = r.data || {}
          this.rows = d.rows || []
          this.pagination.total = d.total || 0
        }).finally(() => { this.loading = false })
      },
      onPageChange(p) {
        this.pagination.current = p.current
        this.pagination.pageSize = p.pageSize
        this.load()
      },
      showSaleDetail(record) {
        stats.saleProfitDetail({ billId: record.billId }).then(r => {
          this.detailRows = (r.data || {}).rows || []
          this.detailSummary = (r.data || {}).summary || {}
          this.detailBill = record
          this.detailVisible = true
        })
      },
      doExport(type) {
        const params = Object.assign({ exportType: type }, this.q)
        axios({
          url: '/api/plugin/jsh-plugin-suite/billExport/export',
          method: 'get',
          params,
          responseType: 'blob'
        }).then(blob => {
          const url = window.URL.createObjectURL(new Blob([blob]))
          const link = document.createElement('a')
          link.href = url
          link.download = (type === 'detail' ? '单据明细' : '单据列表') + '.xls'
          document.body.appendChild(link)
          link.click()
          document.body.removeChild(link)
          window.URL.revokeObjectURL(url)
        })
      }
    },
  }
</script>

<style scoped>
</style>

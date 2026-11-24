<template>
  <div>
    <a-radio-group v-model="mode" button-style="solid" @change="switchMode" style="margin-bottom:12px">
      <a-radio-button value="purchaseFollow">采购发票跟进</a-radio-button>
      <a-radio-button value="salesFollow">销售发票跟进</a-radio-button>
      <a-radio-button value="purchaseRecords">采购开票登记</a-radio-button>
      <a-radio-button value="salesRecords">销售开票登记</a-radio-button>
      <a-radio-button value="purchaseStats">采购开票统计</a-radio-button>
      <a-radio-button value="salesStats">销售开票统计</a-radio-button>
    </a-radio-group>

    <a-form layout="inline" style="margin-bottom:10px">
      <a-form-item label="开始">
        <a-date-picker v-model="q.beginTime" valueFormat="YYYY-MM-DD" style="width:130px"/>
      </a-form-item>
      <a-form-item label="结束">
        <a-date-picker v-model="q.endTime" valueFormat="YYYY-MM-DD" style="width:130px"/>
      </a-form-item>
      <a-form-item v-if="isFollow || isRecords" label="关键字">
        <a-input v-model="q.keyword" :placeholder="isFollow ? '单据号/往来单位' : '发票号/单据号/抬头'" style="width:190px" allowClear/>
      </a-form-item>
      <a-form-item v-if="isFollow" label="开票状态">
        <a-select v-model="q.status" style="width:120px" allowClear placeholder="全部">
          <a-select-option value="未开票">未开票</a-select-option>
          <a-select-option value="部分开票">部分开票</a-select-option>
          <a-select-option value="已开票">已开票</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="isRecords" label="状态">
        <a-select v-model="q.status" style="width:110px" allowClear placeholder="全部">
          <a-select-option value="已开票">已开票</a-select-option>
          <a-select-option value="已作废">已作废</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="isStats" label="统计口径">
        <a-select v-model="q.groupBy" style="width:120px">
          <a-select-option value="organ">按往来单位</a-select-option>
          <a-select-option value="month">按月份</a-select-option>
          <a-select-option value="type">按发票类型</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="search">查询</a-button>
      </a-form-item>
      <a-form-item v-if="isRecords">
        <a-button @click="goFollow">去跟进单据登记</a-button>
      </a-form-item>
    </a-form>

    <a-alert v-if="summary" type="info" show-icon style="margin-bottom:10px" :message="summaryText"/>

    <a-table size="middle" bordered :rowKey="isStats ? 'rowKey' : 'id'" :columns="columns" :dataSource="rows"
             :loading="loading" :pagination="isStats ? false : pagination" @change="onPageChange" :scroll="{x:1500}">
      <span slot="invoiceStatus" slot-scope="text">
        <a-tag :color="text === '已开票' ? 'green' : (text === '部分开票' ? 'orange' : 'red')">{{text}}</a-tag>
      </span>
      <span slot="status" slot-scope="text">
        <a-tag :color="text === '已开票' ? 'green' : 'gray'">{{text}}</a-tag>
      </span>
      <span slot="typeText" slot-scope="text">{{text === 'purchase' ? '采购' : '销售'}}</span>
      <span slot="op" slot-scope="text, record">
        <a v-if="isFollow" @click="openRegister(record)">开票登记</a>
        <span v-if="isRecords">
          <a v-if="record.status === '已开票'" @click="cancel(record)">作废</a>
          <a-divider v-if="record.status === '已开票'" type="vertical"/>
          <a-popconfirm title="确认删除这条开票记录？" @confirm="remove(record)"><a>删除</a></a-popconfirm>
        </span>
      </span>
    </a-table>

    <a-modal :title="registerTitle" :visible="regVisible" @cancel="regVisible=false" @ok="submitRegister" width="760px">
      <a-form :label-col="{span: 7}" :wrapper-col="{span: 15}">
        <a-row :gutter="12">
          <a-col :md="12">
            <a-form-item label="业务单据">
              <a-input v-model="form.billNumber" disabled/>
            </a-form-item>
            <a-form-item label="往来单位">
              <a-input v-model="form.organName" disabled/>
            </a-form-item>
            <a-form-item label="发票号码">
              <a-input v-model="form.invoiceNo" placeholder="留空自动生成"/>
            </a-form-item>
            <a-form-item label="开票日期">
              <a-date-picker v-model="form.invoiceDate" valueFormat="YYYY-MM-DD" style="width:100%"/>
            </a-form-item>
            <a-form-item label="开票金额">
              <a-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%"/>
            </a-form-item>
            <a-form-item label="税额">
              <a-input-number v-model="form.taxAmount" :min="0" :precision="2" style="width:100%"/>
            </a-form-item>
          </a-col>
          <a-col :md="12">
            <a-form-item label="发票抬头">
              <a-input v-model="form.title"/>
            </a-form-item>
            <a-form-item label="税号">
              <a-input v-model="form.taxNum"/>
            </a-form-item>
            <a-form-item label="备注">
              <a-textarea v-model="form.remark" :rows="4"/>
            </a-form-item>
            <a-form-item label="未开票金额">
              <span style="font-size:16px;color:#fa541c">{{form.uninvoicedAmount || 0}} 元</span>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
  import { invoice } from '@/api/plugin'

  const EMPTY = () => ({ beginTime: null, endTime: null, keyword: '', status: null, groupBy: 'organ' })

  export default {
    name: 'PluginInvoice',
    data() {
      return {
        mode: 'purchaseFollow',
        q: EMPTY(),
        rows: [],
        summary: null,
        loading: false,
        pagination: { current: 1, pageSize: 20, total: 0, showSizeChanger: true },
        regVisible: false,
        form: {}
      }
    },
    computed: {
      isFollow() {
        return this.mode === 'purchaseFollow' || this.mode === 'salesFollow'
      },
      isRecords() {
        return this.mode === 'purchaseRecords' || this.mode === 'salesRecords'
      },
      isStats() {
        return this.mode === 'purchaseStats' || this.mode === 'salesStats'
      },
      invoiceType() {
        return this.mode.indexOf('sales') === 0 ? 'sales' : 'purchase'
      },
      registerTitle() {
        return (this.invoiceType === 'purchase' ? '采购' : '销售') + '开票登记'
      },
      columns() {
        if (this.isFollow) {
          return [
            { title: '业务单据号', dataIndex: 'billNumber', width: 170 },
            { title: '单据类型', dataIndex: 'subType', width: 80 },
            { title: '业务时间', dataIndex: 'operTime', width: 150 },
            { title: '往来单位', dataIndex: 'organName', width: 150 },
            { title: '单据金额', dataIndex: 'billAmount', width: 110 },
            { title: '已开票', dataIndex: 'invoicedAmount', width: 110 },
            { title: '未开票', dataIndex: 'uninvoicedAmount', width: 110 },
            { title: '发票数', dataIndex: 'invoiceCount', width: 80 },
            { title: '开票状态', dataIndex: 'invoiceStatus', width: 100, scopedSlots: { customRender: 'invoiceStatus' } },
            { title: '操作', dataIndex: 'op', width: 90, scopedSlots: { customRender: 'op' } }
          ]
        }
        if (this.isRecords) {
          return [
            { title: '发票号码', dataIndex: 'invoiceNo', width: 150 },
            { title: '类型', dataIndex: 'invoiceType', width: 70, scopedSlots: { customRender: 'typeText' } },
            { title: '业务单据号', dataIndex: 'billNumber', width: 160 },
            { title: '往来单位', dataIndex: 'organName', width: 150 },
            { title: '开票日期', dataIndex: 'invoiceDate', width: 110 },
            { title: '开票金额', dataIndex: 'amount', width: 110 },
            { title: '税额', dataIndex: 'taxAmount', width: 100 },
            { title: '发票抬头', dataIndex: 'title', width: 160 },
            { title: '状态', dataIndex: 'status', width: 90, scopedSlots: { customRender: 'status' } },
            { title: '备注', dataIndex: 'remark', width: 150 },
            { title: '操作', dataIndex: 'op', width: 110, scopedSlots: { customRender: 'op' } }
          ]
        }
        return [
          { title: '统计项', dataIndex: 'groupName', width: 220 },
          { title: '发票张数', dataIndex: 'invoiceCount', width: 110 },
          { title: '开票金额', dataIndex: 'amount', width: 140 },
          { title: '税额', dataIndex: 'taxAmount', width: 120 }
        ]
      },
      summaryText() {
        const s = this.summary || {}
        if (this.isFollow) {
          return `单据数 ${s.billCount || 0}，单据金额 ${s.billAmount || 0} 元，已开票 ${s.invoicedAmount || 0} 元，未开票 ${s.uninvoicedAmount || 0} 元，未开票单据 ${s.uninvociedCount || 0} 张`
        }
        return `发票张数 ${s.invoiceCount || 0}，开票金额 ${s.amount || 0} 元，税额 ${s.taxAmount || 0} 元`
      }
    },
    created() {
      this.load()
    },
    methods: {
      load() {
        this.loading = true
        const p = Object.assign({ currentPage: this.pagination.current, pageSize: this.pagination.pageSize, invoiceType: this.invoiceType }, this.q)
        let fn
        if (this.isFollow) fn = invoice.follow
        else if (this.isRecords) fn = invoice.records
        else fn = invoice.stats
        fn(p).then(r => {
          const d = r.data || {}
          let rows = d.rows || []
          if (this.isStats) {
            rows = rows.map((row, idx) => Object.assign({}, row, {
              rowKey: idx,
              groupName: row.organName || row.period || (row.invoiceType === 'purchase' ? '采购' : '销售')
            }))
          }
          this.rows = rows
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
      onPageChange(p) {
        this.pagination.current = p.current
        this.pagination.pageSize = p.pageSize
        this.load()
      },
      goFollow() {
        this.mode = this.invoiceType === 'purchase' ? 'purchaseFollow' : 'salesFollow'
        this.switchMode()
      },
      openRegister(record) {
        this.form = {
          invoiceType: this.invoiceType,
          billId: record.billId,
          billNumber: record.billNumber,
          organName: record.organName,
          invoiceNo: '',
          invoiceDate: null,
          amount: Number(record.uninvoicedAmount || record.billAmount || 0),
          taxAmount: 0,
          title: record.organName,
          taxNum: '',
          remark: '',
          uninvoicedAmount: record.uninvoicedAmount
        }
        this.regVisible = true
      },
      submitRegister() {
        if (!this.form.amount || Number(this.form.amount) <= 0) {
          this.$message.warning('请填写开票金额')
          return
        }
        invoice.register(this.form).then(() => {
          this.$message.success('开票登记成功')
          this.regVisible = false
          this.load()
        })
      },
      cancel(record) {
        invoice.cancel({ id: record.id }).then(() => {
          this.$message.success('已作废')
          this.load()
        })
      },
      remove(record) {
        invoice.del({ id: record.id }).then(() => {
          this.$message.success('已删除')
          this.load()
        })
      }
    }
  }
</script>

<style scoped>
</style>

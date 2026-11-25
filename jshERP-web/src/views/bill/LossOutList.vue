<template>
  <a-card :bordered="false" style="min-height: calc(100vh - 120px)">
    <a-row :gutter="16" style="margin-bottom: 12px" type="flex" align="middle">
      <a-col :md="5" :sm="24">
        <a-radio-group v-model="mode" buttonStyle="solid" @change="handleModeChange">
          <a-radio-button value="mine">报损出库</a-radio-button>
          <a-radio-button value="pending">待我审核</a-radio-button>
        </a-radio-group>
      </a-col>
      <a-col :md="7" :sm="24">
        <a-input-search v-model="queryParams.keyword" placeholder="单据编号 / 报损原因 / 申请人" allowClear
                        @search="handleSearch" enterButton="查询" />
      </a-col>
      <a-col :md="5" :sm="24" v-if="mode === 'mine'">
        <a-select v-model="queryParams.status" allowClear placeholder="全部状态" style="width: 100%"
                  @change="handleSearch">
          <a-select-option value="0">待 CFO 审核</a-select-option>
          <a-select-option value="1">待 CEO 终审</a-select-option>
          <a-select-option value="2">已通过</a-select-option>
          <a-select-option value="3">已驳回</a-select-option>
        </a-select>
      </a-col>
      <a-col :md="7" :sm="24" style="text-align: right">
        <a-button v-if="mode === 'mine'" type="primary" icon="plus" @click="handleAdd">新增报损出库</a-button>
        <a-button icon="reload" style="margin-left: 8px" @click="loadData">刷新</a-button>
      </a-col>
    </a-row>

    <a-alert v-if="mode === 'pending'" type="info" show-icon style="margin-bottom: 12px"
             message="审批规则：申请人提交 → CFO 审核 → CEO 终审；库存仅在 CEO 终审通过后扣减并生成「出库/其它」单。" />

    <a-table :columns="columns" :dataSource="dataSource" :loading="loading" :pagination="pagination"
             rowKey="id" size="middle" @change="handleTableChange">
      <template slot="status" slot-scope="text">
        <a-tag :color="statusColor(text)">{{ statusText(text) }}</a-tag>
      </template>
      <template slot="amount" slot-scope="text">
        {{ text === null || text === undefined ? '' : Number(text).toFixed(2) }}
      </template>
      <template slot="action" slot-scope="text, record">
        <template v-if="mode === 'pending'">
          <a @click="openDetail(record)">审核</a>
        </template>
        <template v-else>
          <a @click="handleView(record)">查看</a>
          <a-divider type="vertical" v-if="canEdit(record)" />
          <a v-if="canEdit(record)" @click="handleEdit(record)">编辑</a>
          <a-divider type="vertical" v-if="canDelete(record)" />
          <a-popconfirm v-if="canDelete(record)" title="确认删除该报损单？" @confirm="handleDelete(record)">
            <a style="color:#ff4d4f">删除</a>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑 -->
    <a-modal :title="formTitle" :visible="formVisible" :width="1180" :confirmLoading="saving"
             :maskClosable="false" @cancel="closeForm" okText="提交审核" cancelText="取消" @ok="handleSubmit">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :md="8" :sm="24">
            <a-form-item label="报损仓库" required>
              <a-select v-model="form.depotId" placeholder="请选择报损仓库" style="width: 100%"
                        :disabled="!!form.id" showSearch optionFilterProp="children">
                <a-select-option v-for="d in depotList" :key="d.id" :value="d.id">{{ d.name }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :md="16" :sm="24">
            <a-form-item label="报损原因" required>
              <a-input v-model="form.reason" placeholder="如：运输破损、临期变质、虫害污染等" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注">
          <a-textarea v-model="form.remark" :rows="2" placeholder="选填" />
        </a-form-item>

        <a-row style="margin-bottom: 8px">
          <a-col :span="12"><b>报损明细</b></a-col>
          <a-col :span="12" style="text-align: right">
            <a-button type="dashed" size="small" icon="plus" @click="openMaterialPicker">添加商品</a-button>
          </a-col>
        </a-row>
        <a-table :columns="itemColumns" :dataSource="form.items" rowKey="key" size="small" :pagination="false"
                 :scroll="{ y: 320 }">
          <template slot="qty" slot-scope="text, record">
            <a-input-number v-model="record.quantity" :min="0" size="small" style="width: 100%"
                            @change="calcAmount(record)" />
          </template>
          <template slot="price" slot-scope="text, record">
            <a-input-number v-model="record.unitPrice" :min="0" size="small" style="width: 100%"
                            @change="calcAmount(record)" />
          </template>
          <template slot="amount" slot-scope="text, record">
            <span>{{ calcAmount(record) }}</span>
          </template>
          <template slot="remark" slot-scope="text, record">
            <a-input v-model="record.remark" size="small" />
          </template>
          <template slot="action" slot-scope="text, record">
            <a style="color:#ff4d4f" @click="removeItem(record)">删除</a>
          </template>
        </a-table>
        <div style="margin-top: 8px; text-align: right">
          合计数量：<b>{{ totalQty }}</b>　合计金额：<b>{{ totalAmount }}</b>
        </div>
      </a-form>
    </a-modal>

    <!-- 选择商品 -->
    <a-modal title="选择报损商品" :visible="pickerVisible" :width="960" @cancel="pickerVisible = false" :footer="null">
      <a-input-search v-model="pickerKeyword" placeholder="商品名称 / 条码 / 规格" style="margin-bottom: 10px"
                      @search="searchMaterial" enterButton="搜索" />
      <a-table :columns="pickerColumns" :dataSource="pickerRows" rowKey="id" size="small" :loading="pickerLoading"
               :pagination="{ current: pickerPage, pageSize: 8, total: pickerTotal }"
               @change="handlePickerTableChange">
        <template slot="stock" slot-scope="text">
          {{ text === null || text === undefined ? '' : text }}
        </template>
        <template slot="action" slot-scope="text, record">
          <a-button size="small" type="primary" @click="pickMaterial(record)">添加</a-button>
        </template>
      </a-table>
    </a-modal>

    <!-- 查看详情 -->
    <a-modal title="报损单详情" :visible="viewVisible" :width="1000" :footer="null" @cancel="viewVisible = false">
      <a-row :gutter="16" style="margin-bottom: 12px">
        <a-col :md="8"><b>单据编号：</b>{{ view.bill.billNumber }}</a-col>
        <a-col :md="8"><b>状态：</b>
          <a-tag :color="statusColor(view.bill.status)">{{ statusText(view.bill.status) }}</a-tag>
        </a-col>
        <a-col :md="8"><b>报损仓库：</b>{{ view.bill.depotName }}</a-col>
        <a-col :md="8"><b>申请人：</b>{{ view.bill.applicantName }}</a-col>
        <a-col :md="8"><b>申请时间：</b>{{ view.bill.applyTime }}</a-col>
        <a-col :md="8"><b>出库单号：</b>{{ view.bill.depotHeadNumber || '—' }}</a-col>
        <a-col :md="24"><b>报损原因：</b>{{ view.bill.reason }}</a-col>
        <a-col :md="24" v-if="view.bill.remark"><b>备注：</b>{{ view.bill.remark }}</a-col>
      </a-row>
      <a-table :columns="detailColumns" :dataSource="view.items" rowKey="id" size="small" :pagination="false" />
      <div style="margin-top: 12px">
        <b>审批记录</b>
        <a-timeline style="margin-top: 10px">
          <a-timeline-item v-for="log in view.logs" :key="log.id" :color="log.action === 'reject' ? 'red' : 'green'">
            {{ log.step }} · {{ log.operatorName }} · {{ log.createTime }}
            <span v-if="log.comment">｜{{ log.comment }}</span>
          </a-timeline-item>
        </a-timeline>
      </div>
    </a-modal>

    <!-- 审核 -->
    <a-modal :title="auditTitle" :visible="auditVisible" :width="1000" :footer="null" @cancel="auditVisible = false">
      <a-row :gutter="16" style="margin-bottom: 12px">
        <a-col :md="8"><b>单据编号：</b>{{ audit.bill.billNumber }}</a-col>
        <a-col :md="8"><b>状态：</b>
          <a-tag :color="statusColor(audit.bill.status)">{{ statusText(audit.bill.status) }}</a-tag>
        </a-col>
        <a-col :md="8"><b>报损仓库：</b>{{ audit.bill.depotName }}</a-col>
        <a-col :md="8"><b>申请人：</b>{{ audit.bill.applicantName }}</a-col>
        <a-col :md="8"><b>申请时间：</b>{{ audit.bill.applyTime }}</a-col>
        <a-col :md="8"><b>合计：</b>{{ audit.bill.totalQty }} / {{ Number(audit.bill.totalAmount || 0).toFixed(2) }}</a-col>
        <a-col :md="24"><b>报损原因：</b>{{ audit.bill.reason }}</a-col>
        <a-col :md="24" v-if="audit.bill.remark"><b>备注：</b>{{ audit.bill.remark }}</a-col>
      </a-row>
      <a-table :columns="detailColumns" :dataSource="audit.items" rowKey="id" size="small" :pagination="false">
        <template slot="amt" slot-scope="text">
          {{ Number(text || 0).toFixed(2) }}
        </template>
      </a-table>
      <div v-if="audit.logs.length" style="margin-top: 12px">
        <b>审批记录</b>
        <a-timeline style="margin-top: 10px">
          <a-timeline-item v-for="log in audit.logs" :key="log.id" :color="log.action === 'reject' ? 'red' : 'green'">
            {{ log.step }} · {{ log.operatorName }} · {{ log.createTime }}
            <span v-if="log.comment">｜{{ log.comment }}</span>
          </a-timeline-item>
        </a-timeline>
      </div>
      <div v-if="audit.canApprove" style="margin-top: 12px; border-top: 1px solid #f0f0f0; padding-top: 12px">
        <div style="margin-bottom: 8px">
          <b>当前环节：</b>{{ audit.bill.status === '0' ? 'CFO 审核' : 'CEO 终审' }}
          <span style="color:#999">（审批流：{{ audit.levelNames }}）</span>
        </div>
        <a-textarea v-model="auditComment" :rows="2" placeholder="审核意见（驳回时请填写原因）" style="margin-bottom: 10px" />
        <div style="text-align: right">
          <a-popconfirm title="确认驳回该报损单？" @confirm="doApprove('reject')">
            <a-button style="margin-right: 8px">驳回</a-button>
          </a-popconfirm>
          <a-popconfirm :title="approveConfirmText" @confirm="doApprove('pass')">
            <a-button type="primary" :loading="approving">审核通过</a-button>
          </a-popconfirm>
        </div>
        <div v-if="audit.bill.status === '1'" style="color:#999; margin-top: 6px; font-size: 12px">
          CEO 终审通过后系统会自动生成「出库/其它」单并扣减仓库库存。
        </div>
      </div>
      <div v-else-if="audit.bill.status !== '2' && audit.bill.status !== '3'" style="margin-top: 12px; color:#999">
        当前账号不是本环节审核人，仅可查看。
      </div>
    </a-modal>
  </a-card>
</template>

<script>
import { getAction, postAction, deleteAction } from '@/api/manage'

const STATUS_MAP = {
  '0': { text: '待 CFO 审核', color: 'orange' },
  '1': { text: '待 CEO 终审', color: 'blue' },
  '2': { text: '已通过', color: 'green' },
  '3': { text: '已驳回', color: 'red' }
}

export default {
  name: 'LossOutList',
  data () {
    return {
      mode: 'mine',
      loading: false,
      saving: false,
      approving: false,
      dataSource: [],
      depotList: [],
      queryParams: { status: '', keyword: '', currentPage: 1, pageSize: 10 },
      pagination: { current: 1, pageSize: 10, total: 0, showSizeChanger: true, showTotal: t => `共 ${t} 条` },
      columns: [
        { title: '单据编号', dataIndex: 'billNumber', width: 170 },
        { title: '报损仓库', dataIndex: 'depotName', width: 120 },
        { title: '报损原因', dataIndex: 'reason', ellipsis: true },
        { title: '数量', dataIndex: 'totalQty', width: 80 },
        { title: '金额', dataIndex: 'totalAmount', width: 100, scopedSlots: { customRender: 'amount' } },
        { title: '状态', dataIndex: 'status', width: 120, scopedSlots: { customRender: 'status' } },
        { title: '申请人', dataIndex: 'applicantName', width: 100 },
        { title: '申请时间', dataIndex: 'applyTime', width: 150 },
        { title: '操作', width: 150, scopedSlots: { customRender: 'action' } }
      ],
      itemColumns: [
        { title: '商品名称', dataIndex: 'materialName', width: 220 },
        { title: '型号', dataIndex: 'materialModel', width: 120 },
        { title: '单位', dataIndex: 'unit', width: 80 },
        { title: '库存', dataIndex: 'stockNumber', width: 80 },
        { title: '数量', width: 100, scopedSlots: { customRender: 'qty' } },
        { title: '单价', width: 110, scopedSlots: { customRender: 'price' } },
        { title: '金额', width: 110, scopedSlots: { customRender: 'amount' } },
        { title: '备注', scopedSlots: { customRender: 'remark' } },
        { title: '操作', width: 70, scopedSlots: { customRender: 'action' } }
      ],
      detailColumns: [
        { title: '商品名称', dataIndex: 'materialName' },
        { title: '型号', dataIndex: 'materialModel', width: 120 },
        { title: '单位', dataIndex: 'unit', width: 80 },
        { title: '数量', dataIndex: 'quantity', width: 90 },
        { title: '单价', dataIndex: 'unitPrice', width: 100 },
        { title: '金额', dataIndex: 'amount', width: 100, scopedSlots: { customRender: 'amt' } },
        { title: '报损前库存', dataIndex: 'stockNumber', width: 110 }
      ],
      formVisible: false,
      formTitle: '新增报损出库',
      form: this.emptyForm(),
      pickerVisible: false,
      pickerKeyword: '',
      pickerLoading: false,
      pickerRows: [],
      pickerTotal: 0,
      pickerPage: 1,
      pickerColumns: [
        { title: '商品名称', dataIndex: 'name' },
        { title: '规格', dataIndex: 'standard', width: 120 },
        { title: '型号', dataIndex: 'model', width: 120 },
        { title: '单位', dataIndex: 'unit', width: 100 },
        { title: '当前库存', width: 100, scopedSlots: { customRender: 'stock' } },
        { title: '操作', width: 80, scopedSlots: { customRender: 'action' } }
      ],
      viewVisible: false,
      view: { bill: {}, items: [], logs: [] },
      auditVisible: false,
      auditTitle: '报损单审核',
      audit: { bill: {}, items: [], logs: [], canApprove: false, levelNames: '' },
      auditComment: ''
    }
  },
  computed: {
    totalQty () {
      return this.form.items.reduce((s, i) => s + (Number(i.quantity) || 0), 0)
    },
    totalAmount () {
      return this.form.items.reduce((s, i) => s + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0).toFixed(2)
    },
    approveConfirmText () {
      return this.audit.bill.status === '1'
        ? '终审通过后将立即生成出库单并扣减库存，确认通过？'
        : '确认通过该报损单？'
    }
  },
  created () {
    this.loadDepot()
    this.loadData()
  },
  methods: {
    emptyForm () {
      return { id: null, depotId: undefined, reason: '', remark: '', items: [] }
    },
    statusText (s) { return (STATUS_MAP[s] || {}).text || s },
    statusColor (s) { return (STATUS_MAP[s] || {}).color || 'default' },
    canEdit (record) { return record.status === '0' || record.status === '3' },
    canDelete (record) { return record.status === '0' || record.status === '3' },
    loadDepot () {
      getAction('/depot/getAllList').then(res => {
        if (res.code === 200) { this.depotList = res.data || [] }
      })
    },
    handleModeChange () {
      this.queryParams.currentPage = 1
      this.loadData()
    },
    loadData () {
      this.loading = true
      const p = this.queryParams
      const req = this.mode === 'pending'
        ? getAction('/loss/pending')
        : getAction('/loss/list', {
          status: p.status, keyword: p.keyword, currentPage: p.currentPage, pageSize: p.pageSize
        })
      req.then(res => {
        if (res.code === 200) {
          this.dataSource = (res.data && res.data.rows) || []
          this.pagination.total = (res.data && res.data.total) || 0
          this.pagination.current = p.currentPage
        } else {
          this.$message.warning(res.data)
        }
      }).finally(() => { this.loading = false })
    },
    handleSearch () {
      this.queryParams.currentPage = 1
      this.loadData()
    },
    handleTableChange (pagination) {
      this.queryParams.currentPage = pagination.current
      this.queryParams.pageSize = pagination.pageSize
      this.loadData()
    },
    handleAdd () {
      this.form = this.emptyForm()
      this.formTitle = '新增报损出库'
      this.formVisible = true
    },
    handleEdit (record) {
      getAction('/loss/info', { id: record.id }).then(res => {
        if (res.code !== 200) { this.$message.warning(res.data); return }
        const bill = res.data.bill
        this.form = {
          id: bill.id,
          depotId: bill.depotId,
          reason: bill.reason,
          remark: bill.remark,
          items: (res.data.items || []).map(i => ({
            key: i.id,
            materialId: i.materialId,
            materialExtendId: i.materialExtendId,
            materialName: i.materialName,
            materialModel: i.materialModel,
            unit: i.unit,
            stockNumber: i.stockNumber,
            quantity: Number(i.quantity) || 0,
            unitPrice: Number(i.unitPrice) || 0,
            remark: i.remark
          }))
        }
        this.formTitle = '编辑报损单 ' + bill.billNumber
        this.formVisible = true
      })
    },
    closeForm () {
      this.formVisible = false
      this.form = this.emptyForm()
    },
    handleView (record) {
      getAction('/loss/info', { id: record.id }).then(res => {
        if (res.code !== 200) { this.$message.warning(res.data); return }
        this.view = { bill: res.data.bill, items: res.data.items || [], logs: res.data.logs || [] }
        this.viewVisible = true
      })
    },
    handleDelete (record) {
      deleteAction('/loss/delete', { id: record.id }).then(res => {
        if (res.code === 200) { this.$message.success('已删除'); this.loadData() } else { this.$message.warning(res.data) }
      })
    },
    openDetail (record) {
      this.auditComment = ''
      getAction('/loss/info', { id: record.id }).then(res => {
        if (res.code !== 200) { this.$message.warning(res.data); return }
        const d = res.data
        const levels = d.levels || []
        this.audit = {
          bill: d.bill,
          items: d.items || [],
          logs: d.logs || [],
          canApprove: !!d.canApprove,
          levelNames: levels.length ? levels.map(l => `L${l.level} ${l.approverName || ''}`).join(' → ') : '未配置'
        }
        this.auditTitle = (d.bill.status === '0' || d.bill.status === '1') ? '报损单审核' : '报损单详情'
        this.auditVisible = true
      })
    },
    doApprove (action) {
      if (action === 'reject' && (!this.auditComment || !this.auditComment.trim())) {
        this.$message.warning('驳回请填写审核意见')
        return
      }
      this.approving = true
      postAction('/loss/approve', {
        id: this.audit.bill.id,
        action: action,
        comment: this.auditComment
      }).then(res => {
        if (res.code === 200) {
          this.$message.success(action === 'pass' ? '审核通过' : '已驳回')
          this.auditVisible = false
          this.loadData()
        } else {
          this.$message.error(res.data)
        }
      }).finally(() => { this.approving = false })
    },
    openMaterialPicker () {
      if (!this.form.depotId) { this.$message.warning('请先选择报损仓库'); return }
      this.pickerKeyword = ''
      this.pickerPage = 1
      this.pickerVisible = true
      this.searchMaterial()
    },
    searchMaterial () {
      this.pickerLoading = true
      getAction('/material/findBySelect', {
        q: this.pickerKeyword, depotId: this.form.depotId, page: this.pickerPage, rows: 8
      }).then(res => {
        this.pickerRows = (res && res.rows) || []
        this.pickerTotal = (res && res.total) || 0
      }).finally(() => { this.pickerLoading = false })
    },
    handlePickerTableChange (pagination) {
      this.pickerPage = pagination.current
      this.searchMaterial()
    },
    pickMaterial (row) {
      const exist = this.form.items.find(i => i.materialExtendId === row.id)
      if (exist) {
        exist.quantity = (Number(exist.quantity) || 0) + 1
        this.$message.success('已累加数量：' + row.name)
        return
      }
      this.form.items.push({
        key: 'k' + Date.now() + Math.random(),
        materialId: null,
        materialExtendId: row.id,
        materialName: row.name,
        materialModel: row.model,
        unit: row.unit,
        stockNumber: row.stock,
        quantity: 1,
        unitPrice: 0,
        remark: ''
      })
      this.$message.success('已添加：' + row.name)
    },
    removeItem (record) {
      this.form.items = this.form.items.filter(i => i.key !== record.key)
    },
    calcAmount (record) {
      return ((Number(record.quantity) || 0) * (Number(record.unitPrice) || 0)).toFixed(2)
    },
    handleSubmit () {
      if (!this.form.depotId) { this.$message.warning('请选择报损仓库'); return }
      if (!this.form.reason || !this.form.reason.trim()) { this.$message.warning('请填写报损原因'); return }
      const validItems = this.form.items.filter(i => Number(i.quantity) > 0)
      if (!validItems.length) { this.$message.warning('请添加报损商品并填写数量'); return }
      const payload = {
        id: this.form.id,
        depotId: this.form.depotId,
        reason: this.form.reason,
        remark: this.form.remark,
        items: validItems.map(i => ({
          materialId: i.materialId,
          materialExtendId: i.materialExtendId,
          materialName: i.materialName,
          materialModel: i.materialModel,
          unit: i.unit,
          quantity: i.quantity,
          unitPrice: i.unitPrice,
          amount: (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0),
          stockNumber: i.stockNumber,
          remark: i.remark
        }))
      }
      this.saving = true
      postAction('/loss/save', payload).then(res => {
        if (res.code === 200) {
          this.$message.success('已提交，等待 CFO 审核')
          this.formVisible = false
          this.loadData()
        } else {
          this.$message.error(res.data)
        }
      }).finally(() => { this.saving = false })
    }
  }
}
</script>

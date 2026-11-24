<template>
  <div>
    <a-radio-group v-model="view" button-style="solid" style="margin-bottom:12px">
      <a-radio-button value="set">账套</a-radio-button>
      <a-radio-button value="subject">会计科目</a-radio-button>
      <a-radio-button value="voucher">凭证</a-radio-button>
      <a-radio-button value="period">期末结账</a-radio-button>
      <a-radio-button value="book">账簿</a-radio-button>
      <a-radio-button value="report">财务报表</a-radio-button>
    </a-radio-group>

    <a-alert v-if="!currentSet && view!=='set'" type="warning" show-icon style="margin-bottom:8px"
             message="请先在“账套”中选择一个账套"/>

    <!-- 账套 -->
    <div v-if="view==='set'">
      <div class="table-operator" style="margin-bottom:10px">
        <a-button type="primary" icon="plus" @click="editSet({})">新增账套</a-button>
      </div>
      <a-table size="middle" bordered rowKey="id" :columns="setColumns" :dataSource="sets" :pagination="false">
        <span slot="op" slot-scope="text, record">
          <a @click="chooseSet(record)">{{ currentSet && currentSet.id === record.id ? '当前账套' : '选用' }}</a>
          <a-divider type="vertical"/>
          <a @click="editSet(record)">编辑</a><a-divider type="vertical"/>
          <a-popconfirm title="确定删除吗?" @confirm="() => delSet(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <!-- 科目 -->
    <div v-if="view==='subject'">
      <div class="table-operator" style="margin-bottom:10px">
        <a-button type="primary" icon="plus" @click="editSubject({})" :disabled="!currentSet">新增科目</a-button>
        <a-popconfirm title="按企业会计准则预置常用科目？" @confirm="initSubject">
          <a-button icon="import" style="margin-left:8px" :disabled="!currentSet">预置科目</a-button>
        </a-popconfirm>
        <a-input v-model="subjectKeyword" placeholder="编码/名称" style="width:200px;margin-left:8px" @pressEnter="loadSubjects"/>
        <a-button @click="loadSubjects" style="margin-left:8px">查询</a-button>
      </div>
      <a-table size="middle" bordered rowKey="id" :columns="subjectColumns" :dataSource="subjects" :pagination="false">
        <span slot="op" slot-scope="text, record">
          <a @click="editSubject(record)">编辑</a><a-divider type="vertical"/>
          <a-popconfirm title="确定删除吗?" @confirm="() => delSubject(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <!-- 凭证 -->
    <div v-if="view==='voucher'">
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="期间">
          <a-input v-model="vq.period" placeholder="yyyy-MM" style="width:120px" allowClear/>
        </a-form-item>
        <a-form-item label="关键字">
          <a-input v-model="vq.keyword" placeholder="摘要/科目" style="width:160px" allowClear/>
        </a-form-item>
        <a-form-item><a-button type="primary" @click="loadVouchers">查询</a-button></a-form-item>
        <a-form-item>
          <a-button type="primary" icon="plus" @click="editVoucher(null)" :disabled="!currentSet">录入凭证</a-button>
        </a-form-item>
      </a-form>
      <a-table size="middle" bordered rowKey="id" :columns="voucherColumns" :dataSource="vouchers"
               :pagination="voucherPage" @change="onVoucherPage">
        <span slot="op" slot-scope="text, record">
          <a v-if="record.audit_status !== '1'" @click="editVoucher(record)">编辑</a>
          <a-divider v-if="record.audit_status !== '1'" type="vertical"/>
          <a @click="auditVoucher(record)">{{ record.audit_status === '1' ? '反审核' : '审核' }}</a>
          <template v-if="record.audit_status !== '1'">
            <a-divider type="vertical"/>
            <a-popconfirm title="确定删除吗?" @confirm="() => delVoucher(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
          </template>
        </span>
      </a-table>
    </div>

    <!-- 结账 -->
    <div v-if="view==='period'">
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="当前期间"><b>{{ currentSet ? currentSet.current_period : '' }}</b></a-form-item>
        <a-form-item label="结账期间">
          <a-input v-model="closePeriod" placeholder="yyyy-MM" style="width:130px"/>
        </a-form-item>
        <a-form-item><a-button type="primary" @click="doClose">结账</a-button></a-form-item>
        <a-form-item><a-button @click="doOpen">反结账</a-button></a-form-item>
      </a-form>
      <a-table size="middle" bordered rowKey="id" :columns="periodColumns" :dataSource="periods" :pagination="false"/>
    </div>

    <!-- 账簿 -->
    <div v-if="view==='book'">
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="账簿">
          <a-select v-model="bookType" style="width:170px" @change="loadBook">
            <a-select-option value="detail">明细账</a-select-option>
            <a-select-option value="general">总账</a-select-option>
            <a-select-option value="subjectBalance">科目余额表</a-select-option>
            <a-select-option value="subjectSummary">科目汇总表</a-select-option>
            <a-select-option value="projectDetail">项目明细账</a-select-option>
            <a-select-option value="projectBalance">项目余额表</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="bookType==='detail'" label="科目编码">
          <a-input v-model="bq.subjectCode" style="width:120px" allowClear/>
        </a-form-item>
        <a-form-item v-if="bookType==='projectDetail'" label="项目">
          <a-input v-model="bq.project" style="width:140px" allowClear/>
        </a-form-item>
        <a-form-item label="起始期间">
          <a-input v-model="bq.beginPeriod" placeholder="yyyy-MM" style="width:110px" allowClear/>
        </a-form-item>
        <a-form-item label="截止期间">
          <a-input v-model="bq.endPeriod" placeholder="yyyy-MM" style="width:110px" allowClear/>
        </a-form-item>
        <a-form-item><a-button type="primary" @click="loadBook">查询</a-button></a-form-item>
      </a-form>
      <div v-if="bookType==='detail' && bookOpening !== null" style="margin-bottom:8px;color:#666">
        期初余额：{{ money(bookOpening) }}
      </div>
      <a-table size="small" bordered rowKey="rowKey" :columns="bookColumns" :dataSource="bookRows" :pagination="false"/>
    </div>

    <!-- 报表 -->
    <div v-if="view==='report'">
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="报表">
          <a-select v-model="reportType" style="width:150px" @change="loadReport">
            <a-select-option value="profit">利润表</a-select-option>
            <a-select-option value="balance">资产负债表</a-select-option>
            <a-select-option value="cash">现金流量表</a-select-option>
          </a-select>
        </a-form-item>
        <template v-if="reportType!=='balance'">
          <a-form-item label="起始期间"><a-input v-model="rq.beginPeriod" placeholder="yyyy-MM" style="width:110px" allowClear/></a-form-item>
          <a-form-item label="截止期间"><a-input v-model="rq.endPeriod" placeholder="yyyy-MM" style="width:110px" allowClear/></a-form-item>
        </template>
        <a-form-item v-else label="期间"><a-input v-model="rq.period" placeholder="yyyy-MM" style="width:110px" allowClear/></a-form-item>
        <a-form-item><a-button type="primary" @click="loadReport">生成报表</a-button></a-form-item>
      </a-form>

      <div v-if="reportType==='profit'">
        <a-table size="middle" bordered rowKey="rowKey" :columns="profitColumns" :dataSource="profitRows" :pagination="false"/>
      </div>

      <div v-if="reportType==='balance'">
        <a-row :gutter="12">
          <a-col :md="12" :sm="24">
            <div style="font-weight:600;margin-bottom:6px">资产</div>
            <a-table size="small" bordered rowKey="rowKey" :columns="balanceColumns" :dataSource="assets" :pagination="false"/>
            <div style="text-align:right;margin-top:6px">资产合计：{{ money(balanceData.assetTotal) }}</div>
          </a-col>
          <a-col :md="12" :sm="24">
            <div style="font-weight:600;margin-bottom:6px">负债</div>
            <a-table size="small" bordered rowKey="rowKey" :columns="balanceColumns" :dataSource="liabilities" :pagination="false"/>
            <div style="font-weight:600;margin:8px 0 6px">所有者权益</div>
            <a-table size="small" bordered rowKey="rowKey" :columns="balanceColumns" :dataSource="equity" :pagination="false"/>
            <div style="text-align:right;margin-top:6px">
              负债合计：{{ money(balanceData.liabilityTotal) }}　权益合计：{{ money(balanceData.equityTotal) }}
            </div>
          </a-col>
        </a-row>
        <a-alert style="margin-top:10px" :type="balanceData.balanced ? 'success' : 'error'" show-icon
                 :message="balanceData.balanced ? '资产 = 负债 + 所有者权益，报表平衡' : ('报表不平衡，差额 ' + money(balanceData.diff))"/>
      </div>

      <div v-if="reportType==='cash'">
        <a-row :gutter="12" style="margin-bottom:10px">
          <a-col :span="6"><a-card size="small"><div>期初现金</div><b>{{ money(cashData.opening) }}</b></a-card></a-col>
          <a-col :span="6"><a-card size="small"><div>现金流入</div><b>{{ money(cashData.inflow) }}</b></a-card></a-col>
          <a-col :span="6"><a-card size="small"><div>现金流出</div><b>{{ money(cashData.outflow) }}</b></a-card></a-col>
          <a-col :span="6"><a-card size="small"><div>期末现金</div><b>{{ money(cashData.closing) }}</b></a-card></a-col>
        </a-row>
        <a-table size="middle" bordered rowKey="rowKey" :columns="cashColumns" :dataSource="cashRows" :pagination="false"/>
      </div>
    </div>

    <a-modal :title="setForm.id ? '编辑账套' : '新增账套'" :visible="setVisible" @ok="saveSet" @cancel="setVisible=false">
      <a-form :label-col="{span:5}" :wrapper-col="{span:18}">
        <a-form-item label="账套名称"><a-input v-model="setForm.set_name"/></a-form-item>
        <a-form-item label="核算单位"><a-input v-model="setForm.company"/></a-form-item>
        <a-form-item label="启用期间"><a-input v-model="setForm.start_period" placeholder="yyyy-MM"/></a-form-item>
        <a-form-item label="币种"><a-input v-model="setForm.currency"/></a-form-item>
        <a-form-item label="备注"><a-textarea v-model="setForm.remark" :rows="2"/></a-form-item>
      </a-form>
    </a-modal>

    <a-modal :title="subjectForm.id ? '编辑科目' : '新增科目'" :visible="subjectVisible" @ok="saveSubject" @cancel="subjectVisible=false">
      <a-form :label-col="{span:5}" :wrapper-col="{span:18}">
        <a-form-item label="科目编码"><a-input v-model="subjectForm.code"/></a-form-item>
        <a-form-item label="科目名称"><a-input v-model="subjectForm.name"/></a-form-item>
        <a-form-item label="类别">
          <a-select v-model="subjectForm.category" style="width:100%">
            <a-select-option value="资产">资产</a-select-option>
            <a-select-option value="负债">负债</a-select-option>
            <a-select-option value="权益">权益</a-select-option>
            <a-select-option value="成本">成本</a-select-option>
            <a-select-option value="损益">损益</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="余额方向">
          <a-select v-model="subjectForm.direction" style="width:100%">
            <a-select-option value="借">借</a-select-option>
            <a-select-option value="贷">贷</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="上级编码"><a-input v-model="subjectForm.parent_code"/></a-form-item>
      </a-form>
    </a-modal>

    <a-modal :title="voucherForm.id ? '编辑凭证' : '录入凭证'" :visible="voucherVisible" width="980px"
             @ok="saveVoucher" @cancel="voucherVisible=false">
      <a-form :label-col="{span:3}" :wrapper-col="{span:20}">
        <a-row :gutter="8">
          <a-col :span="8"><a-form-item label="日期" :label-col="{span:8}">
            <a-date-picker v-model="voucherForm.voucher_date" style="width:100%" format="YYYY-MM-DD"
                           valueFormat="YYYY-MM-DD"/>
          </a-form-item></a-col>
          <a-col :span="8"><a-form-item label="期间" :label-col="{span:8}">
            <a-input v-model="voucherForm.period" placeholder="yyyy-MM"/>
          </a-form-item></a-col>
          <a-col :span="8"><a-form-item label="凭证字" :label-col="{span:8}">
            <a-input v-model="voucherForm.voucher_word" placeholder="记"/>
          </a-form-item></a-col>
        </a-row>
        <a-form-item label="摘要" :label-col="{span:2}" :wrapper-col="{span:20}">
          <a-input v-model="voucherForm.remark"/>
        </a-form-item>
        <a-table size="small" bordered rowKey="_key" :columns="entryColumns" :dataSource="voucherForm.entries"
                 :pagination="false">
          <span slot="subject" slot-scope="text, record">
            <a-select :value="record.subject_code" showSearch optionFilterProp="children" style="width:100%"
                      @change="v => onSubjectChange(record, v)">
              <a-select-option v-for="s in subjects" :key="s.code" :value="s.code">{{s.code}} {{s.name}}</a-select-option>
            </a-select>
          </span>
          <span slot="summary" slot-scope="text, record">
            <a-input v-model="record.summary"/>
          </span>
          <span slot="debit" slot-scope="text, record">
            <a-input-number v-model="record.debit" style="width:100%" :min="0"/>
          </span>
          <span slot="credit" slot-scope="text, record">
            <a-input-number v-model="record.credit" style="width:100%" :min="0"/>
          </span>
          <span slot="project" slot-scope="text, record">
            <a-input v-model="record.project"/>
          </span>
          <span slot="op" slot-scope="text, record">
            <a @click="removeEntry(record)" style="color:#f5222d">删除</a>
          </span>
        </a-table>
        <div style="margin-top:8px">
          <a-button size="small" @click="addEntry">增行</a-button>
          <span style="margin-left:16px">借方合计：<b>{{ money(entryDebit) }}</b>　贷方合计：<b>{{ money(entryCredit) }}</b></span>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
  import { ledger } from '@/api/plugin'

  export default {
    name: 'PluginLedger',
    data() {
      return {
        view: 'set',
        sets: [],
        currentSet: null,
        setVisible: false,
        setForm: {},
        subjects: [],
        subjectKeyword: '',
        subjectVisible: false,
        subjectForm: {},
        vouchers: [],
        vq: { period: '', keyword: '' },
        voucherPage: { current: 1, pageSize: 20, total: 0 },
        voucherVisible: false,
        voucherForm: { entries: [] },
        periods: [],
        closePeriod: '',
        bookType: 'detail',
        bq: { subjectCode: '', project: '', beginPeriod: '', endPeriod: '' },
        bookRows: [],
        bookOpening: null,
        reportType: 'profit',
        rq: { beginPeriod: '', endPeriod: '', period: '' },
        profitRows: [],
        balanceData: {},
        assets: [],
        liabilities: [],
        equity: [],
        cashData: {},
        cashRows: [],
        setColumns: [
          { title: '账套名称', dataIndex: 'set_name' },
          { title: '核算单位', dataIndex: 'company' },
          { title: '启用期间', dataIndex: 'start_period' },
          { title: '当前期间', dataIndex: 'current_period' },
          { title: '币种', dataIndex: 'currency' },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 220 }
        ],
        subjectColumns: [
          { title: '编码', dataIndex: 'code' },
          { title: '名称', dataIndex: 'name' },
          { title: '类别', dataIndex: 'category' },
          { title: '方向', dataIndex: 'direction' },
          { title: '上级编码', dataIndex: 'parent_code' },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 120 }
        ],
        voucherColumns: [
          { title: '期间', dataIndex: 'period' },
          { title: '凭证字号', dataIndex: 'voucher_no', customRender: (t, r) => (r.voucher_word || '记') + '-' + t },
          { title: '日期', dataIndex: 'voucher_date', customRender: t => String(t || '').substring(0, 10) },
          { title: '摘要', dataIndex: 'remark' },
          { title: '金额', dataIndex: 'debitSum' },
          { title: '分录数', dataIndex: 'entryCount' },
          { title: '制单', dataIndex: 'creatorName' },
          { title: '状态', dataIndex: 'audit_status', customRender: t => (t === '1' ? '已审核' : '未审核') },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 160 }
        ],
        periodColumns: [
          { title: '期间', dataIndex: 'period' },
          { title: '状态', dataIndex: 'status', customRender: t => (t === '1' ? '已结账' : '未结账') },
          { title: '结账时间', dataIndex: 'close_time' },
          { title: '备注', dataIndex: 'remark' }
        ],
        profitColumns: [
          { title: '项目', dataIndex: 'name' },
          { title: '本期金额', dataIndex: 'amount', customRender: t => this.money(t) }
        ],
        balanceColumns: [
          { title: '科目', dataIndex: 'name' },
          { title: '金额', dataIndex: 'amount', customRender: t => this.money(t) }
        ],
        cashColumns: [
          { title: '项目（对方科目）', dataIndex: 'item' },
          { title: '流入', dataIndex: 'inflow', customRender: t => this.money(t) },
          { title: '流出', dataIndex: 'outflow', customRender: t => this.money(t) }
        ]
      }
    },
    computed: {
      bookColumns() {
        const map = {
          detail: [
            { title: '日期', dataIndex: 'voucherDate' },
            { title: '凭证号', dataIndex: 'voucherNo' },
            { title: '摘要', dataIndex: 'summary' },
            { title: '科目编码', dataIndex: 'subjectCode' },
            { title: '科目名称', dataIndex: 'subjectName' },
            { title: '借方', dataIndex: 'debit' },
            { title: '贷方', dataIndex: 'credit' },
            { title: '项目', dataIndex: 'project' }
          ],
          general: [
            { title: '科目编码', dataIndex: 'subjectCode' },
            { title: '科目名称', dataIndex: 'subjectName' },
            { title: '类别', dataIndex: 'category' },
            { title: '方向', dataIndex: 'direction' },
            { title: '期初余额', dataIndex: 'opening' },
            { title: '本期借方', dataIndex: 'debit' },
            { title: '本期贷方', dataIndex: 'credit' },
            { title: '期末余额', dataIndex: 'closing' }
          ],
          subjectBalance: [
            { title: '科目编码', dataIndex: 'subjectCode' },
            { title: '科目名称', dataIndex: 'subjectName' },
            { title: '方向', dataIndex: 'direction' },
            { title: '期初余额', dataIndex: 'opening' },
            { title: '本期借方', dataIndex: 'debit' },
            { title: '本期贷方', dataIndex: 'credit' },
            { title: '期末余额', dataIndex: 'closing' }
          ],
          subjectSummary: [
            { title: '科目编码', dataIndex: 'subjectCode' },
            { title: '科目名称', dataIndex: 'subjectName' },
            { title: '借方发生额', dataIndex: 'debit' },
            { title: '贷方发生额', dataIndex: 'credit' },
            { title: '分录数', dataIndex: 'entryCount' }
          ],
          projectDetail: [
            { title: '日期', dataIndex: 'voucherDate' },
            { title: '项目', dataIndex: 'project' },
            { title: '科目', dataIndex: 'subjectName' },
            { title: '摘要', dataIndex: 'summary' },
            { title: '借方', dataIndex: 'debit' },
            { title: '贷方', dataIndex: 'credit' }
          ],
          projectBalance: [
            { title: '项目', dataIndex: 'project' },
            { title: '借方', dataIndex: 'debit' },
            { title: '贷方', dataIndex: 'credit' },
            { title: '余额', dataIndex: 'balance' },
            { title: '分录数', dataIndex: 'entryCount' }
          ]
        }
        const cols = (map[this.bookType] || []).map(c => Object.assign({}, c))
        return cols.map((c, i) => Object.assign(c, { key: 'c' + i, rowKey: 'id' }))
      },
      entryColumns() {
        return [
          { title: '科目', dataIndex: 'subject_code', scopedSlots: { customRender: 'subject' } },
          { title: '摘要', dataIndex: 'summary', scopedSlots: { customRender: 'summary' } },
          { title: '借方金额', dataIndex: 'debit', width: '140px', scopedSlots: { customRender: 'debit' } },
          { title: '贷方金额', dataIndex: 'credit', width: '140px', scopedSlots: { customRender: 'credit' } },
          { title: '项目', dataIndex: 'project', width: '120px', scopedSlots: { customRender: 'project' } },
          { title: '操作', dataIndex: 'op', width: '70px', scopedSlots: { customRender: 'op' } }
        ]
      },
      entryDebit() {
        return (this.voucherForm.entries || []).reduce((s, e) => s + Number(e.debit || 0), 0)
      },
      entryCredit() {
        return (this.voucherForm.entries || []).reduce((s, e) => s + Number(e.credit || 0), 0)
      }
    },
    created() {
      this.loadSets()
    },
    methods: {
      money(v) {
        if (v === null || v === undefined || v === '') return '0.00'
        return Number(v).toFixed(2)
      },
      loadSets() {
        ledger.setList({}).then(r => {
          this.sets = r.data || []
          if (!this.currentSet && this.sets.length) this.chooseSet(this.sets[0])
        })
      },
      chooseSet(record) {
        this.currentSet = Object.assign({}, record)
        this.closePeriod = record.current_period || ''
        this.loadSubjects()
        this.loadVouchers()
        this.loadPeriods()
      },
      editSet(record) {
        this.setForm = Object.assign({ currency: '人民币' }, record)
        this.setVisible = true
      },
      saveSet() {
        ledger.setSave(this.setForm).then(r => {
          if (r.code === 200) {
            this.$message.success('账套已保存')
            this.setVisible = false
            this.loadSets()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      delSet(record) {
        ledger.setDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            if (this.currentSet && this.currentSet.id === record.id) this.currentSet = null
            this.loadSets()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },

      loadSubjects() {
        if (!this.currentSet) return
        ledger.subjectList({ setId: this.currentSet.id, keyword: this.subjectKeyword }).then(r => {
          this.subjects = r.data || []
        })
      },
      editSubject(record) {
        this.subjectForm = Object.assign({ set_id: this.currentSet && this.currentSet.id, category: '资产', direction: '借' }, record)
        this.subjectVisible = true
      },
      saveSubject() {
        ledger.subjectSave(this.subjectForm).then(r => {
          if (r.code === 200) {
            this.$message.success('科目已保存')
            this.subjectVisible = false
            this.loadSubjects()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      delSubject(record) {
        ledger.subjectDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.loadSubjects()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },
      initSubject() {
        ledger.subjectInit({ setId: this.currentSet.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('科目已预置')
            this.loadSubjects()
          } else {
            this.$message.error(r.data || '预置失败')
          }
        })
      },

      loadVouchers() {
        if (!this.currentSet) return
        ledger.voucherList(Object.assign({}, this.vq, {
          setId: this.currentSet.id, currentPage: this.voucherPage.current, pageSize: this.voucherPage.pageSize
        })).then(r => {
          const d = r.data || {}
          this.vouchers = d.rows || []
          this.voucherPage.total = d.total || 0
        })
      },
      onVoucherPage(p) {
        this.voucherPage.current = p.current
        this.voucherPage.pageSize = p.pageSize
        this.loadVouchers()
      },
      editVoucher(record) {
        if (!record) {
          this.voucherForm = {
            set_id: this.currentSet.id, voucher_word: '记', voucher_date: null,
            entries: [this.newEntry(), this.newEntry()]
          }
          this.voucherVisible = true
          return
        }
        ledger.voucherGet({ id: record.id }).then(r => {
          const d = r.data || {}
          this.voucherForm = Object.assign({}, d.voucher, { entries: (d.entries || []).map(e => Object.assign({}, e, { _key: this.uuid() })) })
          this.voucherVisible = true
        })
      },
      newEntry() {
        return { _key: this.uuid(), subject_code: null, summary: '', debit: 0, credit: 0, project: '' }
      },
      addEntry() {
        this.voucherForm.entries.push(this.newEntry())
      },
      removeEntry(record) {
        const idx = this.voucherForm.entries.findIndex(e => e._key === record._key)
        if (idx >= 0) this.voucherForm.entries.splice(idx, 1)
      },
      onSubjectChange(record, code) {
        record.subject_code = code
        const s = this.subjects.find(x => x.code === code)
        record.subject_name = s ? s.name : ''
      },
      saveVoucher() {
        if (Number(this.entryDebit.toFixed(2)) !== Number(this.entryCredit.toFixed(2))) {
          this.$message.error('借贷不平衡，无法保存')
          return
        }
        const payload = Object.assign({}, this.voucherForm, {
          entries: this.voucherForm.entries.map(e => ({
            subject_code: e.subject_code, subject_name: e.subject_name, summary: e.summary,
            debit: e.debit || 0, credit: e.credit || 0, project: e.project
          }))
        })
        ledger.voucherSave(payload).then(r => {
          if (r.code === 200) {
            this.$message.success('凭证已保存')
            this.voucherVisible = false
            this.loadVouchers()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      auditVoucher(record) {
        ledger.voucherAudit({ id: record.id, audit_status: record.audit_status === '1' ? '0' : '1' }).then(r => {
          if (r.code === 200) {
            this.$message.success(record.audit_status === '1' ? '已反审核' : '已审核')
            this.loadVouchers()
          } else {
            this.$message.error(r.data || '操作失败')
          }
        })
      },
      delVoucher(record) {
        ledger.voucherDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.loadVouchers()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },

      loadPeriods() {
        if (!this.currentSet) return
        ledger.periodList({ setId: this.currentSet.id }).then(r => { this.periods = r.data || [] })
      },
      doClose() {
        if (!this.closePeriod) {
          this.$message.warning('请输入结账期间')
          return
        }
        ledger.periodClose({ set_id: this.currentSet.id, period: this.closePeriod }).then(r => {
          if (r.code === 200) {
            this.$message.success(r.data)
            this.loadPeriods()
            this.loadSets()
          } else {
            this.$message.error(r.data || '结账失败')
          }
        })
      },
      doOpen() {
        if (!this.closePeriod) {
          this.$message.warning('请输入要反结账的期间')
          return
        }
        ledger.periodOpen({ set_id: this.currentSet.id, period: this.closePeriod }).then(r => {
          if (r.code === 200) {
            this.$message.success(r.data)
            this.loadPeriods()
            this.loadSets()
          } else {
            this.$message.error(r.data || '反结账失败')
          }
        })
      },

      loadBook() {
        if (!this.currentSet) return
        this.bookOpening = null
        const fn = {
          detail: ledger.bookDetail, general: ledger.bookGeneral, subjectBalance: ledger.subjectBalance,
          subjectSummary: ledger.subjectSummary, projectDetail: ledger.projectDetail, projectBalance: ledger.projectBalance
        }[this.bookType]
        const params = Object.assign({}, this.bq, { setId: this.currentSet.id, currentPage: 1, pageSize: 200 })
        fn(params).then(r => {
          const d = r.data || {}
          this.bookRows = (d.rows || d || []).map((row, i) => Object.assign({}, row, { rowKey: row.id || ('b' + i) }))
          if (d.opening !== undefined) this.bookOpening = d.opening
        })
      },

      loadReport() {
        if (!this.currentSet) return
        const setId = this.currentSet.id
        if (this.reportType === 'profit') {
          ledger.reportProfit({ setId, beginPeriod: this.rq.beginPeriod, endPeriod: this.rq.endPeriod }).then(r => {
            const d = r.data || {}
            this.profitRows = (d.rows || []).map((row, i) => Object.assign({}, row, { rowKey: 'p' + i }))
          })
        } else if (this.reportType === 'balance') {
          ledger.reportBalance({ setId, period: this.rq.period }).then(r => {
            const d = r.data || {}
            this.balanceData = d
            this.assets = (d.assets || []).map((row, i) => Object.assign({}, row, { rowKey: 'a' + i }))
            this.liabilities = (d.liabilities || []).map((row, i) => Object.assign({}, row, { rowKey: 'l' + i }))
            this.equity = (d.equity || []).map((row, i) => Object.assign({}, row, { rowKey: 'e' + i }))
          })
        } else {
          ledger.reportCash({ setId, beginPeriod: this.rq.beginPeriod, endPeriod: this.rq.endPeriod }).then(r => {
            const d = r.data || {}
            this.cashData = d
            this.cashRows = (d.rows || d.details || []).map((row, i) => Object.assign({}, row, { rowKey: 'c' + i }))
          })
        }
      },
      uuid() {
        return 'k' + Math.random().toString(36).slice(2, 10)
      }
    },
    watch: {
      view(v) {
        if (v === 'book') this.loadBook()
        if (v === 'report') this.loadReport()
        if (v === 'period') this.loadPeriods()
        if (v === 'voucher') this.loadVouchers()
      }
    }
  }
</script>

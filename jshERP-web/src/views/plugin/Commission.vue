<template>
  <div>
    <a-radio-group v-model="view" button-style="solid" style="margin-bottom:12px">
      <a-radio-button value="calc">提成试算</a-radio-button>
      <a-radio-button value="rule">提成规则</a-radio-button>
      <a-radio-button value="record">提成记录</a-radio-button>
    </a-radio-group>

    <div v-if="view==='rule'">
      <a-button type="primary" icon="plus" @click="addRule" style="margin-bottom:10px">新增规则</a-button>
      <a-table size="middle" bordered rowKey="id" :columns="ruleColumns" :dataSource="rules" :pagination="false">
        <span slot="op" slot-scope="text, record">
          <a @click="editRule(record)">编辑</a><a-divider type="vertical"/>
          <a-popconfirm title="确定删除吗?" @confirm="() => delRule(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <div v-else-if="view==='calc'">
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="期间">
          <a-date-picker v-model="period" picker="month" valueFormat="YYYY-MM" style="width:130px"/>
        </a-form-item>
        <a-form-item label="规则">
          <a-select v-model="ruleId" style="width:200px" allowClear>
            <a-select-option v-for="r in rules" :key="r.id" :value="r.id">{{r.rule_name}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="calc">试算</a-button>
          <a-button style="margin-left:8px" @click="saveResult" :disabled="!rows.length">保存结果</a-button>
        </a-form-item>
      </a-form>
      <a-alert v-if="summary" type="info" show-icon style="margin-bottom:10px"
               :message="`本期涉及 ${summary.billCount||0} 张已付款完成销售单，提成合计 ${summary.commission||0} 元`"/>
      <a-table size="middle" bordered rowKey="key" :columns="calcColumns" :dataSource="rows" :pagination="false"/>
    </div>

    <div v-else>
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="期间">
          <a-date-picker v-model="period" picker="month" valueFormat="YYYY-MM" style="width:130px"/>
        </a-form-item>
        <a-form-item label="业务员">
          <a-input v-model="salesManName" style="width:150px" allowClear/>
        </a-form-item>
        <a-form-item><a-button type="primary" @click="loadRecords">查询</a-button></a-form-item>
      </a-form>
      <a-table size="middle" bordered rowKey="id" :columns="recordColumns" :dataSource="records" :loading="loading"
               :pagination="recordPagination" @change="onRecordPage"/>
    </div>

    <a-modal :title="ruleForm.id ? '编辑规则' : '新增规则'" :visible="ruleVisible" @ok="saveRule" @cancel="ruleVisible=false" width="760px">
      <a-form :label-col="{span:5}" :wrapper-col="{span:18}">
        <a-form-item label="规则名称"><a-input v-model="ruleForm.rule_name"/></a-form-item>
        <a-form-item label="提成类型">
          <a-select v-model="ruleForm.type" style="width:200px">
            <a-select-option value="1">统一提成</a-select-option>
            <a-select-option value="2">阶梯提成</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="计算方式" v-if="ruleForm.type==='1'">
          <a-select v-model="ruleForm.value_type" style="width:200px">
            <a-select-option value="1">按单据金额比例(%)</a-select-option>
            <a-select-option value="2">按数量定额(元/件)</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="提成值" v-if="ruleForm.type==='1'">
          <a-input-number v-model="ruleForm.value" style="width:200px"/>
        </a-form-item>
        <a-form-item label="阶梯区间" v-if="ruleForm.type==='2'">
          <div v-for="(l, i) in ruleForm.ladders" :key="i" style="margin-bottom:6px">
            金额 <a-input-number v-model="l.min_amount" style="width:110px"/>
            ~ <a-input-number v-model="l.max_amount" style="width:110px" placeholder="不填为以上"/>
            比例% <a-input-number v-model="l.rate" style="width:90px"/>
            <a @click="ruleForm.ladders.splice(i,1)" style="margin-left:8px;color:#f5222d">删除</a>
          </div>
          <a-button size="small" @click="ruleForm.ladders.push({min_amount:null,max_amount:null,rate:null})">添加区间</a-button>
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model="ruleForm.enabledBool" checkedChildren="启用" unCheckedChildren="停用"/>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
  import { commission } from '@/api/plugin'

  export default {
    name: 'PluginCommission',
    data() {
      return {
        view: 'calc',
        period: new Date().toISOString().slice(0, 7),
        rules: [],
        ruleId: null,
        rows: [],
        summary: null,
        loading: false,
        salesManName: '',
        records: [],
        recordPagination: { current: 1, pageSize: 20, total: 0 },
        ruleVisible: false,
        ruleForm: { ladders: [] },
        ruleColumns: [
          { title: '规则名称', dataIndex: 'rule_name' },
          { title: '类型', dataIndex: 'type', customRender: t => (t === '2' ? '阶梯提成' : '统一提成') },
          { title: '提成值/比例', dataIndex: 'value' },
          { title: '计算方式', dataIndex: 'value_type', customRender: t => (t === '2' ? '按件定额' : '按金额比例') },
          { title: '启用', dataIndex: 'enabled', customRender: t => (t === '0' ? '停用' : '启用') },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 120 }
        ],
        calcColumns: [
          { title: '单据号', dataIndex: 'billNumber' },
          { title: '类型', dataIndex: 'subType' },
          { title: '时间', dataIndex: 'operTime' },
          { title: '业务员', dataIndex: 'salesManName' },
          { title: '单据金额', dataIndex: 'billAmount' },
          { title: '数量', dataIndex: 'billQty' },
          { title: '比例%', dataIndex: 'rate' },
          { title: '提成金额', dataIndex: 'commission' },
          { title: '均摊人数', dataIndex: 'shareCount' }
        ],
        recordColumns: [
          { title: '期间', dataIndex: 'period' },
          { title: '单据号', dataIndex: 'bill_number' },
          { title: '业务员', dataIndex: 'sales_man_name' },
          { title: '单据金额', dataIndex: 'bill_amount' },
          { title: '比例%', dataIndex: 'rate' },
          { title: '提成', dataIndex: 'commission' }
        ]
      }
    },
    created() {
      this.loadRules()
      this.calc()
    },
    methods: {
      loadRules() {
        commission.ruleList().then(r => { this.rules = r.data || [] })
      },
      calc() {
        this.loading = true
        commission.calc({ period: this.period, ruleId: this.ruleId }).then(r => {
          const d = r.data || {}
          this.rows = (d.rows || []).map((x, i) => Object.assign({ key: i }, x))
          this.summary = d.summary || null
        }).finally(() => { this.loading = false })
      },
      saveResult() {
        commission.save({ period: this.period, ruleId: this.ruleId }).then(r => {
          if (r.code === 200) {
            this.$message.success('已保存 ' + (r.data.saved || 0) + ' 条提成记录')
            this.loadRecords()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      loadRecords() {
        commission.records({ period: this.period, salesManName: this.salesManName,
          currentPage: this.recordPagination.current, pageSize: this.recordPagination.pageSize }).then(r => {
          const d = r.data || {}
          this.records = d.rows || []
          this.recordPagination.total = d.total || 0
        })
      },
      onRecordPage(p) {
        this.recordPagination.current = p.current
        this.recordPagination.pageSize = p.pageSize
        this.loadRecords()
      },
      addRule() {
        this.ruleForm = { type: '1', value_type: '1', enabledBool: true, ladders: [] }
        this.ruleVisible = true
      },
      editRule(record) {
        commission.ruleGet({ id: record.id }).then(r => {
          const d = r.data || {}
          this.ruleForm = Object.assign({}, d.rule, {
            enabledBool: (d.rule || {}).enabled !== '0',
            ladders: d.ladders || []
          })
          this.ruleVisible = true
        })
      },
      saveRule() {
        const payload = Object.assign({}, this.ruleForm, {
          enabled: this.ruleForm.enabledBool ? '1' : '0'
        })
        delete payload.enabledBool
        commission.ruleSave(payload).then(r => {
          if (r.code === 200) {
            this.$message.success('保存成功')
            this.ruleVisible = false
            this.loadRules()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      delRule(record) {
        commission.ruleDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('删除成功')
            this.loadRules()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      }
    }
  }
</script>

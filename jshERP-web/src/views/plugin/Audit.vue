<template>
  <div>
    <a-radio-group v-model="view" button-style="solid" style="margin-bottom:12px">
      <a-radio-button value="flow">审核流程</a-radio-button>
      <a-radio-button value="todo">待办审核</a-radio-button>
      <a-radio-button value="done">已办审核</a-radio-button>
      <a-radio-button value="mine">我发起的</a-radio-button>
    </a-radio-group>

    <div v-if="view==='flow'">
      <div class="table-operator" style="margin-bottom:10px">
        <a-button type="primary" icon="plus" @click="addFlow">新增流程</a-button>
        <a-button icon="audit" @click="startVisible=true" style="margin-left:8px">发起单据审核</a-button>
        <span style="margin-left:12px;color:#999">最多 5 级，每级可选部门负责人/上级部门负责人/指定人</span>
      </div>
      <a-table size="middle" bordered rowKey="id" :columns="flowColumns" :dataSource="flows" :pagination="false">
        <span slot="op" slot-scope="text, record">
          <a @click="editFlow(record)">编辑</a><a-divider type="vertical"/>
          <a-popconfirm title="确定删除吗?" @confirm="() => delFlow(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <div v-else>
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="单据号">
          <a-input v-model="kw.billNumber" style="width:160px" allowClear/>
        </a-form-item>
        <a-form-item><a-button type="primary" @click="loadTasks">查询</a-button></a-form-item>
      </a-form>
      <a-table size="middle" bordered rowKey="id" :columns="taskColumns" :dataSource="tasks" :loading="loading">
        <span slot="op" slot-scope="text, record">
          <template v-if="view==='todo'">
            <a @click="openAudit(record)">审核</a>
          </template>
          <template v-if="view==='mine'">
            <a @click="showHistory(record)">审核历史</a>
            <template v-if="record.status==='0'">
              <a-divider type="vertical"/>
              <a-popconfirm title="确定撤回吗?" @confirm="() => doWithdraw(record)"><a>撤回</a></a-popconfirm>
              <a-divider type="vertical"/>
              <a-popconfirm title="确定删除吗?" @confirm="() => doDelete(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
            </template>
          </template>
          <template v-if="view==='done'">
            <a @click="showHistory(record)">审核历史</a>
          </template>
        </span>
      </a-table>
    </div>

    <a-modal :title="flowForm.id ? '编辑流程' : '新增流程'" :visible="flowVisible" @ok="saveFlow" @cancel="flowVisible=false" width="760px">
      <a-form :label-col="{span:5}" :wrapper-col="{span:18}">
        <a-form-item label="流程名称"><a-input v-model="flowForm.flow_name"/></a-form-item>
        <a-form-item label="单据类型">
          <a-select v-model="flowForm.bill_type" style="width:220px">
            <a-select-option v-for="t in billTypes" :key="t" :value="t">{{t}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="审核级次">
          <div v-for="(l, i) in flowForm.levels" :key="i" style="margin-bottom:6px">
            <span style="display:inline-block;width:44px">第{{i+1}}级</span>
            <a-select v-model="l.approver_type" style="width:180px">
              <a-select-option value="1">本部门负责人</a-select-option>
              <a-select-option value="2">上级部门负责人</a-select-option>
              <a-select-option value="3">指定审核人</a-select-option>
            </a-select>
            <a-select v-if="l.approver_type==='3'" v-model="l.approver_id" style="width:160px;margin-left:8px" placeholder="选择审核人">
              <a-select-option v-for="u in options.user" :key="u.value" :value="u.value">{{u.label}}</a-select-option>
            </a-select>
            <a v-if="flowForm.levels.length>1" @click="flowForm.levels.splice(i,1)" style="margin-left:8px;color:#f5222d">删除</a>
          </div>
          <a-button size="small" v-if="flowForm.levels.length<5"
                    @click="flowForm.levels.push({approver_type:'1',approver_id:null})">添加级次</a-button>
        </a-form-item>
        <a-form-item label="启用"><a-switch v-model="flowEnabled"/></a-form-item>
      </a-form>
    </a-modal>

    <a-modal title="发起单据审核" :visible="startVisible" @ok="doStart" @cancel="startVisible=false">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="单据类型">
          <a-select v-model="startForm.bill_type" style="width:100%">
            <a-select-option v-for="t in billTypes" :key="t" :value="t">{{t}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="单据号"><a-input v-model="startForm.bill_number"/></a-form-item>
        <a-form-item label="单据ID"><a-input-number v-model="startForm.bill_id" style="width:100%"/></a-form-item>
      </a-form>
    </a-modal>

    <a-modal title="审核" :visible="auditVisible" @ok="submitAudit" @cancel="auditVisible=false">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="审核意见"><a-textarea v-model="auditForm.opinion" :rows="3"/></a-form-item>
        <a-form-item label="结果">
          <a-radio-group v-model="auditForm.agree">
            <a-radio :value="true">同意</a-radio>
            <a-radio :value="false">驳回</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal title="审核历史" :visible="historyVisible" @cancel="historyVisible=false" footer="" width="820px">
      <a-table size="small" bordered rowKey="id" :columns="historyColumns" :dataSource="historyRows" :pagination="false"/>
    </a-modal>
  </div>
</template>

<script>
  import { audit, printTpl, crud } from '@/api/plugin'

  const STATUS_MAP = { '0': '审核中', '1': '已完成', '4': '已驳回' }

  export default {
    name: 'PluginAudit',
    data() {
      return {
        view: 'flow',
        flows: [],
        tasks: [],
        loading: false,
        kw: { billNumber: '' },
        billTypes: [],
        options: { user: [] },
        flowVisible: false,
        flowForm: { levels: [] },
        flowEnabled: true,
        startVisible: false,
        startForm: {},
        auditVisible: false,
        auditForm: { agree: true, opinion: '' },
        currentTask: {},
        historyVisible: false,
        historyRows: [],
        historyColumns: [
          { title: '单据号', dataIndex: 'bill_number' },
          { title: '单据类型', dataIndex: 'bill_type' },
          { title: '发起人', dataIndex: 'creatorName' },
          { title: '提交时间', dataIndex: 'create_time' },
          { title: '状态', dataIndex: 'status', customRender: t => STATUS_MAP[t] || t }
        ],
        flowColumns: [
          { title: '流程名称', dataIndex: 'flow_name' },
          { title: '单据类型', dataIndex: 'bill_type' },
          { title: '审核级数', dataIndex: 'max_level' },
          { title: '启用', dataIndex: 'enabled', customRender: t => (t === '0' ? '停用' : '启用') },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 120 }
        ],
        taskColumns: [
          { title: '单据号', dataIndex: 'bill_number' },
          { title: '单据类型', dataIndex: 'bill_type' },
          { title: '当前级次', dataIndex: 'current_level' },
          { title: '提交人', dataIndex: 'creatorName' },
          { title: '提交时间', dataIndex: 'create_time' },
          { title: '状态', dataIndex: 'status', customRender: t => STATUS_MAP[t] || t },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 170 }
        ]
      }
    },
    created() {
      printTpl.types().then(r => { this.billTypes = r.data || [] })
      crud.options({ type: 'user' }).then(r => { this.options.user = r.data || [] })
      this.loadFlows()
    },
    methods: {
      loadFlows() {
        audit.flowList({}).then(r => { this.flows = r.data || [] })
      },
      loadTasks() {
        this.loading = true
        const p = { billNumber: this.kw.billNumber }
        const fn = this.view === 'todo' ? audit.todo : (this.view === 'done' ? audit.done : audit.mine)
        fn(p).then(r => {
          this.tasks = (r.data || {}).rows || r.data || []
        }).finally(() => { this.loading = false })
      },
      addFlow() {
        this.flowForm = { bill_type: this.billTypes[0], levels: [{ approver_type: '1', approver_id: null }] }
        this.flowEnabled = true
        this.flowVisible = true
      },
      editFlow(record) {
        audit.flowGet({ id: record.id }).then(r => {
          const d = r.data || {}
          this.flowForm = Object.assign({}, d.flow, { levels: d.levels || [] })
          this.flowEnabled = (d.flow || {}).enabled !== '0'
          this.flowVisible = true
        })
      },
      saveFlow() {
        if (!this.flowForm.levels || !this.flowForm.levels.length) {
          this.$message.warning('至少配置一级审核')
          return
        }
        const payload = Object.assign({}, this.flowForm, { enabled: this.flowEnabled ? '1' : '0' })
        audit.flowSave(payload).then(r => {
          if (r.code === 200) {
            this.$message.success('流程已保存')
            this.flowVisible = false
            this.loadFlows()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      delFlow(record) {
        audit.flowDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.loadFlows()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },
      doStart() {
        audit.start(this.startForm).then(r => {
          if (r.code === 200) {
            this.$message.success('已发起审核')
            this.startVisible = false
          } else {
            this.$message.error(r.data || '发起失败')
          }
        })
      },
      openAudit(record) {
        this.currentTask = record
        this.auditForm = { agree: true, opinion: '' }
        this.auditVisible = true
      },
      submitAudit() {
        audit.approve(Object.assign({ taskId: this.currentTask.id, instanceId: this.currentTask.instance_id },
          this.auditForm)).then(r => {
          if (r.code === 200) {
            this.$message.success('审核完成')
            this.auditVisible = false
            this.loadTasks()
          } else {
            this.$message.error(r.data || '审核失败')
          }
        })
      },
      doWithdraw(record) {
        audit.withdraw({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已撤回')
            this.loadTasks()
          } else {
            this.$message.error(r.data || '撤回失败')
          }
        })
      },
      doDelete(record) {
        audit.del({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.loadTasks()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },
      showHistory(record) {
        audit.history({ id: record.id }).then(r => {
          const d = r.data || {}
          this.historyRows = (d.tasks || d.rows || d || [])
          this.historyVisible = true
        })
      }
    }
  }
</script>

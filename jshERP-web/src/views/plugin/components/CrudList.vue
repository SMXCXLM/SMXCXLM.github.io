<template>
  <div>
    <div class="table-page-search-wrapper">
      <a-form layout="inline" @keyup.enter.native="load">
        <a-row :gutter="16">
          <a-col :md="6" :sm="24">
            <a-form-item label="关键字">
              <a-input v-model="keyword" placeholder="名称/编号等" allowClear @pressEnter="load"/>
            </a-form-item>
          </a-col>
          <a-col :md="6" :sm="24">
            <a-button type="primary" @click="load">查询</a-button>
            <a-button style="margin-left:8px" @click="reset">重置</a-button>
          </a-col>
        </a-row>
      </a-form>
    </div>
    <div class="table-operator" style="margin:8px 0">
      <a-button type="primary" icon="plus" @click="add">新增</a-button>
      <span v-if="hint" style="margin-left:12px;color:#999">{{ hint }}</span>
    </div>
    <a-table size="middle" bordered rowKey="id" :columns="tableColumns" :dataSource="rows"
             :pagination="pagination" :loading="loading" @change="onPageChange" :scroll="{x: scrollX}">
      <template slot="actions" slot-scope="text, record">
        <a @click="edit(record)">编辑</a>
        <a-divider type="vertical"/>
        <a-popconfirm title="确定删除吗?" @confirm="() => remove(record)">
          <a style="color:#f5222d">删除</a>
        </a-popconfirm>
        <template v-if="showDetail">
          <a-divider type="vertical"/>
          <a @click="$emit('detail', record)">明细</a>
        </template>
        <template v-if="extraText">
          <a-divider type="vertical"/>
          <a @click="$emit('extra', record)">{{extraText}}</a>
        </template>
      </template>
    </a-table>

    <a-modal :title="form.id ? '编辑' : '新增'" :visible="visible" @ok="submit" @cancel="visible=false"
             :confirmLoading="saving" width="720px">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-row :gutter="12">
          <a-col :md="12" :sm="24" v-for="f in fields" :key="f.key">
            <a-form-item :label="f.label" v-if="!f.hidden">
              <a-input v-if="!f.type || f.type==='input'" v-model="form[f.key]" :placeholder="f.placeholder"/>
              <a-input-number v-else-if="f.type==='number'" v-model="form[f.key]" style="width:100%"/>
              <a-textarea v-else-if="f.type==='textarea'" v-model="form[f.key]" :rows="2"/>
              <a-select v-else-if="f.type==='select'" v-model="form[f.key]" style="width:100%" allowClear>
                <a-select-option v-for="o in (options[f.optionKey]||[])" :key="o.value" :value="o.value">{{o.label}}</a-select-option>
              </a-select>
              <a-date-picker v-else-if="f.type==='date'" v-model="form[f.key]" style="width:100%" showTime
                             format="YYYY-MM-DD HH:mm:ss" valueFormat="YYYY-MM-DD HH:mm:ss"/>
              <a-input v-else v-model="form[f.key]"/>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
  import { crud } from '@/api/plugin'

  export default {
    name: 'PluginCrudList',
    props: {
      table: { type: String, required: true },
      columns: { type: Array, required: true },
      fields: { type: Array, required: true },
      showDetail: { type: Boolean, default: false },
      extraText: { type: String, default: '' },
      hint: { type: String, default: '' },
      options: { type: Object, default: () => ({}) },
      listApi: { type: Function, default: null },
      saveApi: { type: Function, default: null },
      deleteApi: { type: Function, default: null }
    },
    data() {
      return {
        keyword: '',
        rows: [],
        loading: false,
        saving: false,
        visible: false,
        pagination: { current: 1, pageSize: 10, total: 0, showSizeChanger: true, showTotal: t => `共 ${t} 条` },
        form: { id: null }
      }
    },
    computed: {
      tableColumns() {
        const cols = this.columns.map(c => Object.assign({}, c))
        cols.push({ title: '操作', dataIndex: 'actions', scopedSlots: { customRender: 'actions' }, width: 130 })
        return cols
      },
      scrollX() {
        return Math.max(900, this.columns.length * 120)
      }
    },
    created() {
      this.load()
    },
    methods: {
      load() {
        this.loading = true
        const fn = this.listApi || (p => crud.list(p))
        fn({
          table: this.table,
          keyword: this.keyword,
          currentPage: this.pagination.current,
          pageSize: this.pagination.pageSize
        }).then(res => {
          const data = res.data || {}
          this.rows = data.rows || []
          this.pagination.total = data.total || 0
        }).finally(() => { this.loading = false })
      },
      reset() {
        this.keyword = ''
        this.pagination.current = 1
        this.load()
      },
      onPageChange(p) {
        this.pagination.current = p.current
        this.pagination.pageSize = p.pageSize
        this.load()
      },
      add() {
        this.form = { id: null }
        this.visible = true
      },
      edit(record) {
        this.form = Object.assign({}, record)
        this.visible = true
      },
      submit() {
        this.saving = true
        const payload = Object.assign({ table: this.table }, this.form)
        const fn = this.saveApi || (p => crud.save(p))
        fn(payload).then(res => {
          if (res.code === 200) {
            this.$message.success('保存成功')
            this.visible = false
            this.load()
          } else {
            this.$message.error(res.data || '保存失败')
          }
        }).finally(() => { this.saving = false })
      },
      remove(record) {
        const fn = this.deleteApi || (p => crud.del(p))
        fn({ table: this.table, id: record.id }).then(res => {
          if (res.code === 200) {
            this.$message.success('删除成功')
            this.load()
          } else {
            this.$message.error(res.data || '删除失败')
          }
        })
      }
    }
  }
</script>

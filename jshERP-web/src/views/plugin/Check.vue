<template>
  <div>
    <a-form layout="inline" style="margin-bottom:10px">
      <a-form-item label="仓库">
        <a-select v-model="q.depotId" style="width:150px" allowClear>
          <a-select-option v-for="d in options.depot" :key="d.value" :value="d.value">{{d.label}}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model="q.status" style="width:130px" allowClear>
          <a-select-option value="0">待初盘</a-select-option>
          <a-select-option value="1">待复盘</a-select-option>
          <a-select-option value="2">待完成</a-select-option>
          <a-select-option value="3">已完成</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="单号">
        <a-input v-model="q.keyword" style="width:170px" allowClear/>
      </a-form-item>
      <a-form-item><a-button type="primary" @click="load">查询</a-button></a-form-item>
      <a-form-item>
        <a-button type="primary" icon="plus" @click="createVisible=true">新建盘点</a-button>
      <span style="margin-left:12px;color:#999">初盘差异绝对值 &gt; 5 需二次复盘</span>
      </a-form-item>
    </a-form>

    <a-table size="middle" bordered rowKey="id" :columns="columns" :dataSource="rows" :loading="loading"
             :pagination="pagination" @change="onPage">
      <span slot="op" slot-scope="text, record">
        <a @click="openDetail(record)">{{ record.status === '0' ? '初盘录入' : (record.status === '1' ? '复盘' : '查看') }}</a>
        <a-divider type="vertical"/>
        <a-popconfirm title="确定删除吗?" @confirm="() => del(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
      </span>
    </a-table>

    <a-modal title="新建盘点单" :visible="createVisible" @ok="doCreate" @cancel="createVisible=false">
      <a-form :label-col="{span:5}" :wrapper-col="{span:16}">
        <a-form-item label="盘点仓库">
          <a-select v-model="createForm.depot_id" style="width:100%">
            <a-select-option v-for="d in options.depot" :key="d.value" :value="d.value">{{d.label}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="盘点方式">
          <a-select v-model="createForm.check_type" style="width:100%">
            <a-select-option value="全盘">全盘</a-select-option>
            <a-select-option value="抽盘">抽盘</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注"><a-textarea v-model="createForm.remark" :rows="2"/></a-form-item>
      </a-form>
    </a-modal>

    <a-modal :title="'盘点单 ' + (order.check_no || '')" :visible="detailVisible" width="1000px" @cancel="detailVisible=false">
      <template slot="footer">
        <a-button @click="detailVisible=false">关闭</a-button>
        <a-button @click="saveFirst" :disabled="order.status !== '0' && order.status !== '1'">保存初盘</a-button>
        <a-button @click="saveSecond" :disabled="order.status !== '1'">保存复盘</a-button>
        <a-button type="primary" @click="finish" :disabled="order.status === '3'">完成盘点</a-button>
      </template>
      <a-alert type="info" show-icon style="margin-bottom:8px"
               :message="`仓库：${order.depotName || ''}　状态：${statusText}　需复盘：${secondCount} 项`"/>
      <a-table size="small" bordered rowKey="id" :columns="itemColumns" :dataSource="items" :pagination="false" :scroll="{x:900}">
        <span slot="first_qty" slot-scope="text, record">
          <a-input-number :value="record.first_qty" @change="v => record.first_qty = v" style="width:100px"/>
        </span>
        <span slot="second_qty" slot-scope="text, record">
          <a-input-number :value="record.second_qty" @change="v => record.second_qty = v" style="width:100px"
                          :disabled="record.need_second !== '1'"/>
        </span>
      </a-table>
    </a-modal>
  </div>
</template>

<script>
  import { stockCheck, crud } from '@/api/plugin'

  const STATUS = { '0': '待初盘', '1': '待复盘', '2': '待完成', '3': '已完成' }

  export default {
    name: 'PluginCheck',
    data() {
      return {
        q: { depotId: null, status: null, keyword: '' },
        options: { depot: [] },
        rows: [],
        loading: false,
        pagination: { current: 1, pageSize: 20, total: 0 },
        createVisible: false,
        createForm: { check_type: '全盘' },
        detailVisible: false,
        order: {},
        items: [],
        secondCount: 0,
        columns: [
          { title: '盘点单号', dataIndex: 'check_no' },
          { title: '仓库', dataIndex: 'depotName' },
          { title: '方式', dataIndex: 'check_type' },
          { title: '盘点日期', dataIndex: 'check_date' },
          { title: '商品数', dataIndex: 'itemCount' },
          { title: '待复盘', dataIndex: 'secondCount' },
          { title: '状态', dataIndex: 'status', customRender: t => STATUS[t] || t },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 150 }
        ],
        itemColumns: [
          { title: '商品', dataIndex: 'materialName' },
          { title: '规格', dataIndex: 'standard' },
          { title: '型号', dataIndex: 'model' },
          { title: '条码', dataIndex: 'barCode' },
          { title: '单位', dataIndex: 'unit' },
          { title: '账面数量', dataIndex: 'book_qty' },
          { title: '初盘数量', dataIndex: 'first_qty', scopedSlots: { customRender: 'first_qty' } },
          { title: '初盘差异', dataIndex: 'first_diff' },
          { title: '复盘数量', dataIndex: 'second_qty', scopedSlots: { customRender: 'second_qty' } },
          { title: '复盘差异', dataIndex: 'second_diff' },
          { title: '最终数量', dataIndex: 'final_qty' }
        ]
      }
    },
    computed: {
      statusText() { return STATUS[this.order.status] || this.order.status || '' }
    },
    created() {
      crud.options({ type: 'depot' }).then(r => { this.options.depot = r.data || [] })
      this.load()
    },
    methods: {
      load() {
        this.loading = true
        stockCheck.list(Object.assign({}, this.q, {
          currentPage: this.pagination.current, pageSize: this.pagination.pageSize
        })).then(r => {
          const d = r.data || {}
          this.rows = d.rows || []
          this.pagination.total = d.total || 0
        }).finally(() => { this.loading = false })
      },
      onPage(p) {
        this.pagination.current = p.current
        this.pagination.pageSize = p.pageSize
        this.load()
      },
      doCreate() {
        if (!this.createForm.depot_id) {
          this.$message.warning('请选择盘点仓库')
          return
        }
        stockCheck.create(this.createForm).then(r => {
          if (r.code === 200) {
            this.$message.success('盘点单已创建，自动带出 ' + (r.data.itemCount || 0) + ' 个商品')
            this.createVisible = false
            this.load()
          } else {
            this.$message.error(r.data || '创建失败')
          }
        })
      },
      openDetail(record) {
        stockCheck.get({ id: record.id }).then(r => {
          const d = r.data || {}
          this.order = d.order || {}
          this.items = (d.items || []).map(i => Object.assign({}, i))
          this.secondCount = this.items.filter(i => i.need_second === '1').length
          this.detailVisible = true
        })
      },
      saveFirst() {
        const items = this.items.map(i => ({ id: i.id, first_qty: i.first_qty, remark: i.remark }))
        stockCheck.first({ id: this.order.id, items }).then(r => {
          if (r.code === 200) {
            this.$message.success('初盘已保存，需复盘 ' + (r.data.needSecond || 0) + ' 项')
            this.openDetail(this.order)
            this.load()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      saveSecond() {
        const items = this.items.filter(i => i.need_second === '1')
          .map(i => ({ id: i.id, second_qty: i.second_qty, remark: i.remark }))
        stockCheck.second({ id: this.order.id, items }).then(r => {
          if (r.code === 200) {
            this.$message.success('复盘已保存')
            this.openDetail(this.order)
            this.load()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      finish() {
        stockCheck.finish({ id: this.order.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('盘点完成，生成差异出入库 ' + (r.data.adjustCount || 0) + ' 笔')
            this.detailVisible = false
            this.load()
          } else {
            this.$message.error(r.data || '操作失败')
          }
        })
      },
      del(record) {
        stockCheck.del({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.load()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      }
    }
  }
</script>

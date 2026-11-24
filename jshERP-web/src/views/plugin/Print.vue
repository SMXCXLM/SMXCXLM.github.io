<template>
  <div>
    <div class="table-operator" style="margin-bottom:10px">
      <a-button type="primary" icon="plus" @click="openEditor({})">新增模板</a-button>
      <a-button icon="printer" @click="openPreview" style="margin-left:8px">单据预览/打印</a-button>
    </div>
    <a-table size="middle" bordered rowKey="id" :columns="columns" :dataSource="templates" :pagination="false">
      <span slot="op" slot-scope="text, record">
        <a @click="openEditor(record)">编辑</a><a-divider type="vertical"/>
        <a-popconfirm title="确定删除吗?" @confirm="() => del(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
      </span>
    </a-table>

    <a-modal :title="form.id ? '编辑模板' : '新增模板'" :visible="editorVisible" width="760px"
             @ok="save" @cancel="editorVisible=false">
      <a-form :label-col="{span:5}" :wrapper-col="{span:18}">
        <a-form-item label="模板名称"><a-input v-model="form.template_name"/></a-form-item>
        <a-form-item label="单据类型">
          <a-select v-model="form.bill_type" style="width:220px">
            <a-select-option v-for="t in types" :key="t" :value="t">{{t}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="纸张规格">
          <a-select v-model="form.page_size" style="width:220px">
            <a-select-option value="241x140">241×140 三联</a-select-option>
            <a-select-option value="241x93">241×93 两联</a-select-option>
            <a-select-option value="A4">A4</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="设为默认">
          <a-switch v-model="defaultBool"/>
        </a-form-item>
        <a-form-item label="公司/表头信息">
          <a-textarea v-model="form.header_json" :rows="4"
                      placeholder='JSON，例如 {"company":"石门思雨食品有限公司","title":"销售出库单","showLogo":true}'/>
        </a-form-item>
        <a-form-item label="明细列配置">
          <a-textarea v-model="form.detail_json" :rows="4"
                      placeholder='JSON，例如 [{"key":"materialName","title":"商品"},{"key":"qty","title":"数量"}]'/>
        </a-form-item>
        <a-form-item label="图标URL"><a-input v-model="form.icon"/></a-form-item>
        <a-form-item label="备注"><a-textarea v-model="form.remark" :rows="2"/></a-form-item>
      </a-form>
    </a-modal>

    <a-modal title="单据预览" :visible="previewVisible" width="900px" @cancel="previewVisible=false">
      <template slot="footer">
        <a-button @click="previewVisible=false">关闭</a-button>
        <a-button type="primary" icon="printer" @click="doPrint">打印</a-button>
      </template>
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="单据类型">
          <a-select v-model="previewType" style="width:180px" @change="onPreviewType">
            <a-select-option v-for="t in types" :key="t" :value="t">{{t}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="单据号">
          <a-input v-model="previewNumber" placeholder="输入单据号后按回车" @pressEnter="findBill"/>
        </a-form-item>
        <a-form-item><a-button @click="findBill">载入</a-button></a-form-item>
      </a-form>
      <div id="print-area" v-if="bill.head" style="border:1px solid #ddd;padding:12px">
        <div style="text-align:center;font-size:18px;font-weight:700">{{company}} {{bill.head.sub_type}}单</div>
        <div style="display:flex;justify-content:space-between;font-size:12px;margin:8px 0">
          <span>单据号：{{bill.head.number}}</span>
          <span>日期：{{bill.head.oper_time}}</span>
          <span>仓库：{{bill.head.depotName}}</span>
        </div>
        <table style="width:100%;border-collapse:collapse;font-size:12px">
          <thead>
          <tr>
            <th style="border:1px solid #333;padding:3px">商品</th>
            <th style="border:1px solid #333;padding:3px">规格</th>
            <th style="border:1px solid #333;padding:3px">条码</th>
            <th style="border:1px solid #333;padding:3px">单位</th>
            <th style="border:1px solid #333;padding:3px">数量</th>
            <th style="border:1px solid #333;padding:3px">单价</th>
            <th style="border:1px solid #333;padding:3px">金额</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="i in bill.items" :key="i.id">
            <td style="border:1px solid #333;padding:3px">{{i.materialName}}</td>
            <td style="border:1px solid #333;padding:3px">{{i.standard}}</td>
            <td style="border:1px solid #333;padding:3px">{{i.barCode}}</td>
            <td style="border:1px solid #333;padding:3px">{{i.material_unit}}</td>
            <td style="border:1px solid #333;padding:3px;text-align:right">{{i.basic_number}}</td>
            <td style="border:1px solid #333;padding:3px;text-align:right">{{i.unit_price}}</td>
            <td style="border:1px solid #333;padding:3px;text-align:right">{{i.all_price}}</td>
          </tr>
          <tr>
            <td colspan="6" style="border:1px solid #333;padding:3px;text-align:right">合计</td>
            <td style="border:1px solid #333;padding:3px;text-align:right">{{bill.head.total_price}}</td>
          </tr>
          </tbody>
        </table>
        <div style="display:flex;justify-content:space-between;margin-top:10px;font-size:12px">
          <span>制单：{{bill.head.creatorName}}</span>
          <span>业务员：{{bill.head.sales_man}}</span>
          <span>备注：{{bill.head.remark}}</span>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script>
  import { printTpl, stats } from '@/api/plugin'

  export default {
    name: 'PluginPrint',
    data() {
      return {
        templates: [],
        types: [],
        editorVisible: false,
        form: {},
        defaultBool: false,
        previewVisible: false,
        previewType: '销售出库',
        previewNumber: '',
        bill: {},
        company: '石门思雨食品有限公司',
        columns: [
          { title: '模板名称', dataIndex: 'template_name' },
          { title: '单据类型', dataIndex: 'bill_type' },
          { title: '纸张', dataIndex: 'page_size' },
          { title: '默认', dataIndex: 'is_default', customRender: t => (t === '1' ? '是' : '') },
          { title: '备注', dataIndex: 'remark' },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 120 }
        ]
      }
    },
    created() {
      printTpl.types().then(r => { this.types = r.data || [] })
      this.load()
    },
    methods: {
      load() {
        printTpl.list({}).then(r => { this.templates = r.data || [] })
      },
      openEditor(record) {
        this.form = Object.assign({ page_size: '241x140', bill_type: this.types[0] }, record)
        this.defaultBool = this.form.is_default === '1'
        this.editorVisible = true
      },
      save() {
        const payload = Object.assign({}, this.form, { is_default: this.defaultBool ? '1' : '0' })
        printTpl.save(payload).then(r => {
          if (r.code === 200) {
            this.$message.success('模板已保存')
            this.editorVisible = false
            this.load()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      del(record) {
        printTpl.del({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.load()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },
      openPreview() {
        this.bill = {}
        this.previewVisible = true
      },
      onPreviewType() {
        this.previewNumber = ''
        this.bill = {}
      },
      findBill() {
        if (!this.previewNumber) {
          this.$message.warning('请输入单据号')
          return
        }
        stats.exportList({ subType: this.previewType, number: this.previewNumber, pageSize: 5 }).then(r => {
          const rows = ((r.data || {}).rows) || []
          if (!rows.length) {
            this.$message.warning('未找到该单据')
            return
          }
          printTpl.data({ billType: this.previewType, billId: rows[0].id }).then(res => {
            this.bill = res.data || {}
            const tpl = this.bill.template
            if (tpl && tpl.header_json) {
              try {
                const h = JSON.parse(tpl.header_json)
                if (h.company) this.company = h.company
              } catch (e) { /* 忽略非 JSON 配置 */ }
            }
            this.$message.success('已载入单据 ' + rows[0].number)
          })
        })
      },
      doPrint() {
        const html = document.getElementById('print-area').innerHTML
        const win = window.open('', '_blank', 'width=900,height=700')
        win.document.write('<html><head><title>打印</title></head><body>' + html + '</body></html>')
        win.document.close()
        win.focus()
        win.print()
      }
    }
  }
</script>

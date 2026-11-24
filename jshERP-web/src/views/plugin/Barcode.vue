<template>
  <div>
    <a-row :gutter="12">
      <a-col :md="16" :sm="24">
        <a-form layout="inline" style="margin-bottom:10px">
          <a-form-item>
            <a-input v-model="q.keyword" placeholder="名称/条码/规格/型号" style="width:190px" allowClear
                     @pressEnter="search"/>
          </a-form-item>
          <a-form-item>
            <a-select v-model="q.depotId" style="width:130px" allowClear placeholder="仓库">
              <a-select-option v-for="d in depots" :key="d.value" :value="d.value">{{d.label}}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="search">查询</a-button>
            <a-button style="margin-left:8px" @click="loadAll">加载全部商品</a-button>
          </a-form-item>
        </a-form>
        <div style="margin-bottom:8px">
          <a-button size="small" @click="triggerImport">导入 Excel</a-button>
          <a-button size="small" style="margin-left:8px" @click="exportSelected">导出 Excel</a-button>
          <a-button size="small" style="margin-left:8px" @click="clearSelected">清空已选</a-button>
          <span style="margin-left:10px;color:#888">已选 {{selected.length}} 项</span>
          <input ref="file" type="file" accept=".xlsx,.xls,.csv" style="display:none" @change="onImportFile"/>
        </div>
        <a-table size="small" bordered :rowKey="'extendId'" :columns="productColumns" :dataSource="products"
                 :loading="loading" :pagination="pagination" @change="onPageChange" :scroll="{x:900}"
                 :rowSelection="rowSelection">
          <span slot="price" slot-scope="text, record">{{ priceOf(record, template) }}</span>
        </a-table>
      </a-col>
      <a-col :md="8" :sm="24">
        <a-card size="small" title="条码模板">
          <a-form layout="vertical">
            <a-form-item label="当前模板">
              <a-select v-model="templateId" style="width:100%" @change="onTemplateChange">
                <a-select-option v-for="t in templates" :key="t.id" :value="t.id">{{t.template_name}}</a-select-option>
              </a-select>
            </a-form-item>
            <a-button type="primary" block @click="openTemplate">编辑模板</a-button>
            <a-button block style="margin-top:8px" @click="printLabels">打印条码（{{totalCopies}} 张）</a-button>
          </a-form>
          <a-divider/>
          <div style="font-size:12px;color:#666;line-height:1.9">
            标签：{{template.width || 40}} × {{template.height || 30}} mm，每行 {{template.columns || 3}} 张<br/>
            模板可在上方编辑并设为默认；打印时请选择实际标签纸尺寸。
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card size="small" :title="'已选商品（' + selected.length + '）'" style="margin-top:12px">
      <a-table size="small" bordered rowKey="extendId" :columns="selectedColumns" :dataSource="selected"
               :pagination="false" :scroll="{x:760}">
        <a-input-number slot="qty" slot-scope="text, record" :min="1" :max="999" :value="record.printQty"
                        style="width:80px" @change="v => changeQty(record, v)"/>
        <span slot="op" slot-scope="text, record"><a @click="removeSelected(record)">移除</a></span>
      </a-table>
    </a-card>

    <a-modal title="条码打印模板" :visible="tplVisible" @cancel="tplVisible=false" @ok="saveTemplate" width="760px">
      <a-form :label-col="{span: 7}" :wrapper-col="{span: 15}">
        <a-row :gutter="12">
          <a-col :md="12">
            <a-form-item label="模板名称"><a-input v-model="tpl.template_name"/></a-form-item>
            <a-form-item label="标签宽(mm)"><a-input-number v-model="tpl.width" :min="20" :max="120" style="width:100%"/></a-form-item>
            <a-form-item label="标签高(mm)"><a-input-number v-model="tpl.height" :min="10" :max="120" style="width:100%"/></a-form-item>
            <a-form-item label="每行列数"><a-input-number v-model="tpl.columns" :min="1" :max="6" style="width:100%"/></a-form-item>
            <a-form-item label="条码高度"><a-input-number v-model="tpl.barcode_height" :min="20" :max="100" style="width:100%"/></a-form-item>
            <a-form-item label="价格取数">
              <a-select v-model="tpl.price_type">
                <a-select-option value="commodity">零售价</a-select-option>
                <a-select-option value="wholesale">批发价</a-select-option>
                <a-select-option value="low">最低价</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="设为默认"><a-switch v-model="tpl.is_default"/></a-form-item>
          </a-col>
          <a-col :md="12">
            <a-form-item label="显示标题"><a-switch v-model="tpl.show_title"/></a-form-item>
            <a-form-item label="标题文字"><a-input v-model="tpl.title_text"/></a-form-item>
            <a-form-item label="显示商品名称"><a-switch v-model="tpl.show_name"/></a-form-item>
            <a-form-item label="显示规格"><a-switch v-model="tpl.show_standard"/></a-form-item>
            <a-form-item label="显示单位"><a-switch v-model="tpl.show_unit"/></a-form-item>
            <a-form-item label="显示价格"><a-switch v-model="tpl.show_price"/></a-form-item>
            <a-form-item label="显示条码"><a-switch v-model="tpl.show_barcode"/></a-form-item>
            <a-form-item label="显示店铺"><a-switch v-model="tpl.show_shop"/></a-form-item>
            <a-form-item label="店铺名称"><a-input v-model="tpl.shop_name"/></a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
  import JsBarcode from 'jsbarcode'
  import * as XLSX from 'xlsx'
  import { barcode, pos } from '@/api/plugin'
  import { exportExcel } from './reportUtil'

  const EMPTY_Q = () => ({ keyword: '', depotId: null })

  export default {
    name: 'PluginBarcode',
    data() {
      return {
        q: EMPTY_Q(),
        products: [],
        allProducts: [],
        selected: [],
        templates: [],
        template: {},
        templateId: null,
        depots: [],
        loading: false,
        pagination: { current: 1, pageSize: 20, total: 0, showSizeChanger: true },
        tplVisible: false,
        tpl: {}
      }
    },
    computed: {
      productColumns() {
        return [
          { title: '商品名称', dataIndex: 'materialName', width: 150 },
          { title: '规格', dataIndex: 'standard', width: 100 },
          { title: '型号', dataIndex: 'model', width: 100 },
          { title: '条码', dataIndex: 'barCode', width: 140 },
          { title: '单位', dataIndex: 'saleUnit', width: 70 },
          { title: '零售价', dataIndex: 'commodityPrice', width: 90 },
          { title: '批发价', dataIndex: 'wholesalePrice', width: 90 },
          { title: '最低价', dataIndex: 'lowPrice', width: 90 },
          { title: '库存', dataIndex: 'stock', width: 80 }
        ]
      },
      selectedColumns() {
        return [
          { title: '商品名称', dataIndex: 'materialName' },
          { title: '规格', dataIndex: 'standard' },
          { title: '条码', dataIndex: 'barCode' },
          { title: '单位', dataIndex: 'saleUnit' },
          { title: '单价', dataIndex: 'printPrice' },
          { title: '打印数量', dataIndex: 'printQty', width: 100, scopedSlots: { customRender: 'qty' } },
          { title: '操作', dataIndex: 'op', width: 70, scopedSlots: { customRender: 'op' } }
        ]
      },
      selectedKeys() {
        return this.selected.map(p => p.extendId)
      },
      totalCopies() {
        return this.selected.reduce((sum, p) => sum + (p.printQty || 1), 0)
      },
      rowSelection() {
        return {
          selectedRowKeys: this.selectedKeys,
          columnTitle: '选择',
          onSelect: (record, selected) => this.toggle(record, selected),
          onSelectAll: (selected, selectedRows, changeRows) => this.toggleAll(selected, changeRows)
        }
      }
    },
    created() {
      pos.init({}).then(r => {
        this.depots = (r.data || {}).depots || []
        if (this.depots.length) this.q.depotId = this.depots[0].value
        this.load()
      })
      this.loadTemplates()
    },
    methods: {
      priceOf(record, tpl) {
        const type = (tpl && tpl.price_type) || 'commodity'
        if (type === 'wholesale') return record.wholesalePrice || 0
        if (type === 'low') return record.lowPrice || 0
        return record.commodityPrice || 0
      },
      load() {
        this.loading = true
        barcode.products(Object.assign({ currentPage: this.pagination.current, pageSize: this.pagination.pageSize }, this.q)).then(r => {
          const d = r.data || {}
          this.products = d.rows || []
          this.pagination.total = d.total || 0
        }).finally(() => { this.loading = false })
      },
      loadAll() {
        this.loading = true
        barcode.all({ depotId: this.q.depotId, limit: 3000 }).then(r => {
          this.allProducts = r.data || []
          this.products = this.allProducts.slice()
          this.pagination.total = this.products.length
        }).finally(() => { this.loading = false })
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
      toggle(record, checked) {
        if (checked) {
          if (!this.selected.some(p => p.extendId === record.extendId)) {
            this.selected.push(Object.assign({}, record, {
              printQty: 1,
              printPrice: this.priceOf(record, this.template)
            }))
          }
        } else {
          this.selected = this.selected.filter(p => p.extendId !== record.extendId)
        }
      },
      toggleAll(checked, changeRows) {
        changeRows.forEach(row => this.toggle(row, checked))
      },
      changeQty(record, value) {
        record.printQty = value || 1
      },
      removeSelected(record) {
        this.selected = this.selected.filter(p => p.extendId !== record.extendId)
      },
      clearSelected() {
        this.selected = []
      },
      loadTemplates() {
        barcode.templates({}).then(r => {
          this.templates = r.data || []
          const def = this.templates.find(t => String(t.is_default) === '1') || this.templates[0] || {}
          this.template = def
          this.templateId = def.id || null
        })
      },
      onTemplateChange(id) {
        const t = this.templates.find(x => x.id === id)
        if (t) {
          this.template = t
          this.selected.forEach(p => { p.printPrice = this.priceOf(p, t) })
        }
      },
      openTemplate() {
        const t = this.template || {}
        const toBool = v => String(v) === '1'
        this.tpl = Object.assign({}, t, {
          show_title: toBool(t.show_title), show_name: toBool(t.show_name),
          show_standard: toBool(t.show_standard), show_unit: toBool(t.show_unit),
          show_price: toBool(t.show_price), show_barcode: toBool(t.show_barcode),
          show_shop: toBool(t.show_shop), is_default: toBool(t.is_default)
        })
        this.tplVisible = true
      },
      saveTemplate() {
        const payload = Object.assign({}, this.tpl)
        ;['show_title', 'show_name', 'show_standard', 'show_unit', 'show_price', 'show_barcode', 'show_shop', 'is_default']
          .forEach(k => { payload[k] = payload[k] ? '1' : '0' })
        barcode.saveTemplate(payload).then(() => {
          this.$message.success('模板已保存')
          this.tplVisible = false
          this.loadTemplates()
        })
      },
      esc(v) {
        return String(v === null || v === undefined ? '' : v)
          .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      },
      printLabels() {
        if (!this.selected.length) {
          this.$message.warning('请先选择商品')
          return
        }
        const tpl = this.template || {}
        const cols = Number(tpl.columns) || 3
        const width = Number(tpl.width) || 40
        const height = Number(tpl.height) || 30
        const barHeight = Number(tpl.barcode_height) || 30
        const labels = []
        this.selected.forEach(p => {
          const copies = Math.max(1, Number(p.printQty) || 1)
          for (let i = 0; i < copies; i++) labels.push(p)
        })
        const labelHtml = labels.map(p => {
          let svg = ''
          if (String(tpl.show_barcode) !== '0' && p.barCode) {
            try {
              const el = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
              JsBarcode(el, String(p.barCode), {
                format: 'CODE128', width: 1.4, height: barHeight, displayValue: false, margin: 0
              })
              svg = el.outerHTML
            } catch (e) {
              svg = '<div style="font-size:9px">' + this.esc(p.barCode) + '</div>'
            }
          }
          const lines = []
          if (String(tpl.show_title) === '1') lines.push('<div class="title">' + this.esc(tpl.title_text || '') + '</div>')
          if (String(tpl.show_shop) === '1') lines.push('<div class="shop">' + this.esc(tpl.shop_name || '') + '</div>')
          if (String(tpl.show_name) === '1') lines.push('<div class="name">' + this.esc(p.materialName) + '</div>')
          if (String(tpl.show_standard) === '1' && p.standard) lines.push('<div class="spec">' + this.esc(p.standard) + '</div>')
          if (String(tpl.show_unit) === '1' && p.saleUnit) lines.push('<div class="spec">单位：' + this.esc(p.saleUnit) + '</div>')
          if (String(tpl.show_price) === '1') lines.push('<div class="price">￥' + this.esc(this.priceOf(p, tpl)) + '</div>')
          return '<div class="label">' + lines.join('') + svg + '</div>'
        }).join('')
        const win = window.open('', '_blank', 'width=900,height=700')
        if (!win) return
        win.document.write('<html><head><meta charset="utf-8"><title>条码打印</title><style>'
          + 'body{margin:0;font-family:"Microsoft YaHei",Arial}'
          + '.grid{display:grid;grid-template-columns:repeat(' + cols + ', ' + width + 'mm);gap:2mm;padding:2mm}'
          + '.label{width:' + width + 'mm;height:' + height + 'mm;box-sizing:border-box;padding:1mm;'
          + 'display:flex;flex-direction:column;align-items:center;justify-content:center;overflow:hidden;text-align:center}'
          + '.title{font-size:8px;font-weight:700;line-height:1.1}.shop{font-size:7px;line-height:1.1}'
          + '.name{font-size:8px;font-weight:600;line-height:1.15;max-height:2.3em;overflow:hidden}'
          + '.spec{font-size:7px;line-height:1.1;color:#333}.price{font-size:9px;font-weight:700}'
          + 'svg{max-width:100%;height:' + barHeight + 'px}'
          + '@page{size:auto;margin:4mm}'
          + '</style></head><body><div class="grid">' + labelHtml + '</div></body></html>')
        win.document.close()
        setTimeout(() => { win.focus(); win.print() }, 400)
      },
      triggerImport() {
        this.$refs.file.value = ''
        this.$refs.file.click()
      },
      onImportFile(e) {
        const file = e.target.files && e.target.files[0]
        if (!file) return
        const reader = new FileReader()
        reader.onload = ev => {
          try {
            const data = new Uint8Array(ev.target.result)
            const book = XLSX.read(data, { type: 'array' })
            const sheet = book.Sheets[book.SheetNames[0]]
            const rows = XLSX.utils.sheet_to_json(sheet, { defval: '' })
            this.importRows(rows)
          } catch (err) {
            this.$message.error('读取 Excel 失败：' + err.message)
          }
        }
        reader.readAsArrayBuffer(file)
      },
      importRows(rows) {
        if (!this.allProducts.length) {
          this.$message.warning('请先点击“加载全部商品”，再导入 Excel')
          return
        }
        let added = 0
        let missed = 0
        rows.forEach(row => {
          const barCode = String(row['条码'] || row['barCode'] || row['条形码'] || row['商品条码'] || '').trim()
          const name = String(row['商品名称'] || row['名称'] || row['name'] || '').trim()
          const qty = Number(row['打印数量'] || row['数量'] || row['qty'] || 1)
          const match = barCode
            ? this.allProducts.find(p => String(p.barCode) === barCode)
            : this.allProducts.find(p => String(p.materialName) === name)
          if (!match) {
            missed++
            return
          }
          const exist = this.selected.find(p => p.extendId === match.extendId)
          if (exist) {
            exist.printQty = Math.max(1, Number(exist.printQty || 1) + (Number(qty) || 1))
          } else {
            this.selected.push(Object.assign({}, match, {
              printQty: Math.max(1, Number(qty) || 1),
              printPrice: this.priceOf(match, this.template)
            }))
          }
          added++
        })
        this.$message.success(`导入完成：匹配 ${added} 行，未匹配 ${missed} 行`)
      },
      exportSelected() {
        const rows = this.selected.length ? this.selected : this.products
        const columns = [
          { title: '商品名称', dataIndex: 'materialName' },
          { title: '规格', dataIndex: 'standard' },
          { title: '型号', dataIndex: 'model' },
          { title: '条码', dataIndex: 'barCode' },
          { title: '单位', dataIndex: 'saleUnit' },
          { title: '零售价', dataIndex: 'commodityPrice' },
          { title: '批发价', dataIndex: 'wholesalePrice' },
          { title: '最低价', dataIndex: 'lowPrice' },
          { title: '打印数量', dataIndex: 'printQty' }
        ]
        exportExcel('条码打印商品.xlsx', columns, rows)
      }
    }
  }
</script>

<style scoped>
</style>

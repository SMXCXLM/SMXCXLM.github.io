<template>
  <div>
    <a-row :gutter="12" style="margin-bottom:10px">
      <a-col :md="6">
        <div style="font-size:12px;color:#888;margin-bottom:4px">收银仓库</div>
        <a-select v-model="depotId" style="width:100%">
          <a-select-option v-for="d in depots" :key="d.value" :value="d.value">{{d.label}}</a-select-option>
        </a-select>
      </a-col>
      <a-col :md="7">
        <div style="font-size:12px;color:#888;margin-bottom:4px">会员（可选）</div>
        <a-select v-model="memberId" showSearch :filterOption="false" allowClear placeholder="输入姓名/手机号搜索会员"
                  style="width:100%" @search="searchMember" @change="onMemberChange">
          <a-select-option v-for="m in members" :key="m.value" :value="m.value">
            {{m.label}}（余额 {{m.advanceIn}}）
          </a-select-option>
        </a-select>
      </a-col>
      <a-col :md="5">
        <div style="font-size:12px;color:#888;margin-bottom:4px">支付方式</div>
        <a-select v-model="payType" style="width:100%">
          <a-select-option v-for="p in payTypes" :key="p" :value="p">{{p}}</a-select-option>
        </a-select>
      </a-col>
      <a-col :md="6" style="text-align:right;padding-top:18px">
        <a-button @click="openHolds">取单</a-button>
        <a-button style="margin-left:8px" @click="holdCart">挂单</a-button>
      </a-col>
    </a-row>

    <a-row :gutter="12">
      <a-col :md="15">
        <a-card size="small" title="扫码 / 商品检索">
          <a-input-search ref="scanInput" v-model="scanCode" size="large" placeholder="扫描商品条码或序列号，回车加入购物车"
                          enterButton="加入" @search="scan" style="margin-bottom:10px"/>
          <a-form layout="inline" style="margin-bottom:8px">
            <a-form-item>
              <a-input v-model="keyword" placeholder="商品名称/条码/型号" style="width:200px" allowClear @pressEnter="searchProduct"/>
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="searchProduct">查询商品</a-button>
            </a-form-item>
            <a-form-item>
              <span style="color:#888">库存实时校验，序列号商品需扫码或手工录入</span>
            </a-form-item>
          </a-form>
          <a-table size="small" bordered rowKey="extendId" :columns="productColumns" :dataSource="products"
                   :pagination="false" :scroll="{y: 190}">
            <span slot="add" slot-scope="text, record"><a @click="pickProduct(record)">加入</a></span>
          </a-table>
        </a-card>

        <a-card size="small" :title="'购物车（' + cart.length + ' 行）'" style="margin-top:12px">
          <a-table size="small" bordered rowKey="key" :columns="cartColumns" :dataSource="cart"
                   :pagination="false" :scroll="{x:820}">
            <a-input-number slot="price" slot-scope="text, record" :min="0" :precision="2" :value="record.price"
                            style="width:90px" @change="v => changePrice(record, v)"/>
            <a-input-number slot="qty" slot-scope="text, record" :min="1" :precision="2" :value="record.qty"
                            style="width:80px" :disabled="String(record.enableSerialNumber) === '1'"
                            @change="v => changeQty(record, v)"/>
            <span slot="sn" slot-scope="text, record">
              <a-tag v-for="sn in record.snList" :key="sn" closable @close="removeSn(record, sn)">{{sn}}</a-tag>
              <a v-if="String(record.enableSerialNumber) === '1'" @click="openSerial(record)">录入序列号</a>
            </span>
            <span slot="amount" slot-scope="text, record">{{ lineAmount(record) }}</span>
            <span slot="op" slot-scope="text, record"><a @click="removeLine(record)">删除</a></span>
          </a-table>
        </a-card>
      </a-col>

      <a-col :md="9">
        <a-card size="small" title="结算">
          <div style="font-size:13px;line-height:2.2">
            <div style="display:flex;justify-content:space-between"><span>商品合计</span><b>{{ totalAmount }} 元</b></div>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>整单优惠</span>
              <a-input-number v-model="discount" :min="0" :precision="2" style="width:120px"/>
            </div>
            <div style="display:flex;justify-content:space-between;font-size:20px;color:#fa541c;border-top:1px dashed #eee;padding-top:8px;margin-top:4px">
              <span>应收</span><b>{{ payableAmount }} 元</b>
            </div>
            <div v-if="member" style="display:flex;justify-content:space-between;color:#888">
              <span>会员余额</span><span>{{ member.advanceIn }} 元</span>
            </div>
          </div>
          <a-button type="primary" size="large" block style="margin-top:14px" :loading="checkingOut" @click="checkout">
            结算收款
          </a-button>
          <a-button block style="margin-top:8px" @click="clearCart">清空购物车</a-button>
          <div style="font-size:12px;color:#999;margin-top:10px">
            会员预付款支付将从会员余额扣减；结算后自动生成零售出库单。
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-modal title="选择商品" :visible="pickVisible" @cancel="pickVisible=false" footer="" width="760px">
      <a-table size="small" bordered rowKey="extendId" :columns="productColumns" :dataSource="pickRows" :pagination="false">
        <span slot="add" slot-scope="text, record"><a @click="pickFromModal(record)">加入</a></span>
      </a-table>
    </a-modal>

    <a-modal title="录入序列号" :visible="snVisible" @cancel="snVisible=false" @ok="confirmSerial" width="520px">
      <div style="margin-bottom:8px">商品：<b>{{ snProduct.materialName }}</b>（{{ snProduct.standard || '' }}）</div>
      <a-input-search v-model="snInput" placeholder="扫描或输入序列号，回车添加" enterButton="添加" @search="addSn"/>
      <a-table size="small" bordered style="margin-top:10px" :dataSource="snRows" :columns="snColumns" :pagination="false">
        <span slot="op" slot-scope="text, record"><a @click="snRows = snRows.filter(x => x.sn !== record.sn)">移除</a></span>
      </a-table>
    </a-modal>

    <a-modal title="挂单列表" :visible="holdsVisible" @cancel="holdsVisible=false" footer="" width="820px">
      <a-table size="small" bordered rowKey="id" :columns="holdColumns" :dataSource="holds" :pagination="false">
        <span slot="op" slot-scope="text, record">
          <a @click="takeHold(record)">取单</a>
          <a-divider type="vertical"/>
          <a-popconfirm title="确认删除该挂单？" @confirm="deleteHold(record)"><a>删除</a></a-popconfirm>
        </span>
      </a-table>
    </a-modal>

    <a-modal title="收款成功 / 小票" :visible="receiptVisible" @cancel="receiptVisible=false" width="460px"
             :footer="null">
      <div style="text-align:center;margin-bottom:10px">
        <a-tag color="green">已结算</a-tag>
        <div style="font-size:20px;color:#fa541c;margin-top:6px">应收 {{ lastBill.payableAmount || 0 }} 元</div>
        <div style="color:#666">单号：{{ lastBill.number }}</div>
      </div>
      <a-button type="primary" block @click="printReceipt">打印小票</a-button>
      <div v-if="receiptData.template" style="font-size:12px;color:#888;text-align:center;margin-top:8px">
        已匹配三联打印模板：{{ receiptData.template.template_name }}
      </div>
      <div v-else style="font-size:12px;color:#888;text-align:center;margin-top:8px">
        未找到“零售出库”三联打印模板，将使用内置小票样式。
      </div>
    </a-modal>
  </div>
</template>

<script>
  import { pos } from '@/api/plugin'

  let lineSeed = 1

  export default {
    name: 'PluginPos',
    data() {
      return {
        depots: [],
        members: [],
        payTypes: ['现金', '会员预付款', '微信', '支付宝', '银行卡'],
        depotId: null,
        memberId: null,
        payType: '现金',
        scanCode: '',
        keyword: '',
        products: [],
        pickVisible: false,
        pickRows: [],
        cart: [],
        discount: 0,
        checkingOut: false,
        snVisible: false,
        snProduct: {},
        snInput: '',
        snRows: [],
        holdsVisible: false,
        holds: [],
        receiptVisible: false,
        receiptData: {},
        lastBill: {}
      }
    },
    computed: {
      member() {
        return this.members.find(m => m.value === this.memberId) || null
      },
      totalAmount() {
        const total = this.cart.reduce((sum, row) => sum + Number(row.qty || 0) * Number(row.price || 0), 0)
        return total.toFixed(2)
      },
      payableAmount() {
        const value = Number(this.totalAmount) - Number(this.discount || 0)
        return Math.max(0, value).toFixed(2)
      },
      productColumns() {
        return [
          { title: '商品名称', dataIndex: 'materialName', width: 150 },
          { title: '规格', dataIndex: 'standard', width: 90 },
          { title: '条码', dataIndex: 'barCode', width: 120 },
          { title: '单位', dataIndex: 'saleUnit', width: 60 },
          { title: '售价', dataIndex: 'price', width: 80 },
          { title: '库存', dataIndex: 'stock', width: 70 },
          { title: '操作', dataIndex: 'add', width: 60, scopedSlots: { customRender: 'add' } }
        ]
      },
      cartColumns() {
        return [
          { title: '商品名称', dataIndex: 'materialName', width: 150 },
          { title: '规格', dataIndex: 'standard', width: 80 },
          { title: '条码', dataIndex: 'barCode', width: 110 },
          { title: '单位', dataIndex: 'saleUnit', width: 60 },
          { title: '单价', dataIndex: 'price', width: 100, scopedSlots: { customRender: 'price' } },
          { title: '数量', dataIndex: 'qty', width: 90, scopedSlots: { customRender: 'qty' } },
          { title: '金额', dataIndex: 'amount', width: 90, scopedSlots: { customRender: 'amount' } },
          { title: '序列号', dataIndex: 'snList', width: 150, scopedSlots: { customRender: 'sn' } },
          { title: '操作', dataIndex: 'op', width: 60, scopedSlots: { customRender: 'op' } }
        ]
      },
      snColumns() {
        return [
          { title: '序列号', dataIndex: 'sn' },
          { title: '操作', dataIndex: 'op', width: 70, scopedSlots: { customRender: 'op' } }
        ]
      },
      holdColumns() {
        return [
          { title: '挂单号', dataIndex: 'holdNo', width: 150 },
          { title: '会员', dataIndex: 'memberName', width: 120 },
          { title: '金额', dataIndex: 'totalAmount', width: 90 },
          { title: '时间', dataIndex: 'createTime', width: 160 },
          { title: '备注', dataIndex: 'remark' },
          { title: '操作', dataIndex: 'op', width: 110, scopedSlots: { customRender: 'op' } }
        ]
      }
    },
    created() {
      pos.init({}).then(r => {
        const d = r.data || {}
        this.depots = d.depots || []
        this.members = d.members || []
        this.payTypes = d.payTypes || this.payTypes
        if (this.depots.length) this.depotId = this.depots[0].value
        this.searchProduct()
      })
    },
    methods: {
      lineAmount(row) {
        return (Number(row.qty || 0) * Number(row.price || 0)).toFixed(2)
      },
      searchProduct() {
        pos.search({ code: this.keyword, depotId: this.depotId, pageSize: 30 }).then(r => {
          this.products = r.data || []
        })
      },
      scan() {
        const code = String(this.scanCode || '').trim()
        if (!code) return
        pos.search({ code, depotId: this.depotId }).then(r => {
          const rows = r.data || []
          this.scanCode = ''
          if (!rows.length) {
            this.$message.warning('未找到商品：' + code)
          } else if (rows.length === 1) {
            this.pickProduct(rows[0])
          } else {
            this.pickRows = rows
            this.pickVisible = true
          }
          this.focusScan()
        })
      },
      pickFromModal(record) {
        this.pickVisible = false
        this.pickProduct(record)
      },
      pickProduct(record) {
        if (String(record.enableSerialNumber) === '1') {
          if (record.serialNumber) {
            this.addSerialLine(record, [record.serialNumber])
          } else {
            this.openSerial(record)
          }
          return
        }
        const exist = this.cart.find(row => row.extendId === record.extendId)
        if (exist) {
          exist.qty = Number(exist.qty || 0) + 1
        } else {
          this.cart.push(Object.assign({}, record, {
            key: 'L' + (lineSeed++),
            qty: 1,
            price: Number(record.price || 0),
            snList: []
          }))
        }
        this.focusScan()
      },
      addSerialLine(record, snList) {
        const exist = this.cart.find(row => row.extendId === record.extendId && String(row.enableSerialNumber) === '1')
        if (exist) {
          snList.forEach(sn => {
            if (!exist.snList.includes(sn)) exist.snList.push(sn)
          })
          exist.qty = exist.snList.length
        } else {
          this.cart.push(Object.assign({}, record, {
            key: 'L' + (lineSeed++),
            qty: snList.length,
            price: Number(record.price || 0),
            snList: snList.slice()
          }))
        }
        this.focusScan()
      },
      focusScan() {
        this.$nextTick(() => {
          const input = this.$refs.scanInput
          if (input && input.focus) input.focus()
        })
      },
      changePrice(record, value) {
        record.price = value || 0
      },
      changeQty(record, value) {
        record.qty = value || 1
      },
      removeLine(record) {
        this.cart = this.cart.filter(row => row.key !== record.key)
      },
      clearCart() {
        this.cart = []
        this.discount = 0
      },
      removeSn(record, sn) {
        record.snList = record.snList.filter(x => x !== sn)
        record.qty = record.snList.length
        if (!record.snList.length) this.removeLine(record)
      },
      openSerial(record) {
        this.snProduct = record
        this.snRows = (record.snList || []).map(sn => ({ sn }))
        this.snInput = ''
        this.snVisible = true
      },
      addSn() {
        const sn = String(this.snInput || '').trim()
        if (!sn) return
        if (this.snRows.some(row => row.sn === sn)) {
          this.$message.warning('序列号重复')
          return
        }
        this.snRows.push({ sn })
        this.snInput = ''
      },
      confirmSerial() {
        if (!this.snRows.length) {
          this.$message.warning('请至少录入一个序列号')
          return
        }
        const record = this.snProduct
        this.addSerialLine(record, this.snRows.map(r => r.sn))
        this.snVisible = false
      },
      searchMember(keyword) {
        pos.memberSearch({ keyword }).then(r => { this.members = r.data || [] })
      },
      onMemberChange() {
        const m = this.member
        if (m && this.payType === '会员预付款') {
          this.$message.info('会员余额：' + m.advanceIn + ' 元')
        }
      },
      checkout() {
        if (!this.cart.length) {
          this.$message.warning('购物车为空')
          return
        }
        if (!this.depotId) {
          this.$message.warning('请选择收银仓库')
          return
        }
        if (this.payType === '会员预付款' && !this.memberId) {
          this.$message.warning('会员预付款支付请先选择会员')
          return
        }
        const items = this.cart.map(row => ({
          materialId: row.materialId,
          materialName: row.materialName,
          extendId: row.extendId,
          qty: row.qty,
          price: row.price,
          unit: row.saleUnit,
          sku: row.sku,
          snList: row.snList
        }))
        this.checkingOut = true
        pos.checkout({
          depotId: this.depotId,
          memberId: this.memberId,
          payType: this.payType,
          discount: this.discount,
          items
        }).then(r => {
          this.lastBill = r.data || {}
          this.$message.success('结算成功：' + (this.lastBill.number || ''))
          this.receiptData = {}
          pos.receipt({ billId: this.lastBill.billId }).then(rr => {
            this.receiptData = rr.data || {}
            this.receiptVisible = true
          })
          this.clearCart()
          this.searchProduct()
          if (this.payType === '会员预付款') this.searchMember('')
        }).finally(() => { this.checkingOut = false })
      },
      holdCart() {
        if (!this.cart.length) {
          this.$message.warning('购物车为空')
          return
        }
        const items = this.cart.map(row => ({
          materialId: row.materialId, materialName: row.materialName, extendId: row.extendId,
          qty: row.qty, price: row.price, unit: row.saleUnit, sku: row.sku,
          snList: row.snList, enableSerialNumber: row.enableSerialNumber, barCode: row.barCode,
          standard: row.standard, stock: row.stock
        }))
        pos.hold({
          depotId: this.depotId,
          memberId: this.memberId,
          memberName: this.member ? this.member.label : '',
          discount: this.discount,
          items
        }).then(r => {
          this.$message.success('挂单成功：' + ((r.data || {}).hold_no || ''))
          this.clearCart()
        })
      },
      openHolds() {
        pos.holds({}).then(r => {
          this.holds = r.data || []
          this.holdsVisible = true
        })
      },
      takeHold(record) {
        pos.holdGet({ id: record.id }).then(r => {
          const d = r.data || {}
          let items = []
          try {
            items = typeof d.items === 'string' ? JSON.parse(d.items) : (d.items || [])
          } catch (e) {
            items = []
          }
          this.cart = items.map(item => Object.assign({}, item, {
            key: 'L' + (lineSeed++),
            snList: item.snList || [],
            qty: Number(item.qty || 1),
            price: Number(item.price || 0)
          }))
          this.depotId = d.depot_id || this.depotId
          this.memberId = d.member_id || null
          this.discount = Number(d.discount_amount || 0)
          this.holdsVisible = false
          this.$message.success('已取单')
        })
      },
      deleteHold(record) {
        pos.holdDelete({ id: record.id }).then(() => {
          this.$message.success('已删除')
          this.openHolds()
        })
      },
      printReceipt() {
        const d = this.receiptData || {}
        const head = d.head || {}
        const items = d.items || []
        const rows = items.map(item => '<tr><td>' + this.esc(item.materialName) + '</td>'
          + '<td style="text-align:right">' + item.basic_number + '</td>'
          + '<td style="text-align:right">' + item.unit_price + '</td>'
          + '<td style="text-align:right">' + item.all_price + '</td></tr>').join('')
        const win = window.open('', '_blank', 'width=420,height=640')
        if (!win) return
        win.document.write('<html><head><meta charset="utf-8"><title>零售小票</title><style>'
          + 'body{font-family:"Microsoft YaHei",Arial;width:72mm;margin:0 auto;font-size:12px;color:#000}'
          + 'h1{font-size:16px;text-align:center;margin:6px 0}'
          + '.meta{font-size:11px;line-height:1.6}'
          + 'table{width:100%;border-collapse:collapse;margin-top:6px}'
          + 'th,td{font-size:11px;padding:2px 0;border-bottom:1px dashed #999}'
          + '.total{font-size:14px;font-weight:700;margin-top:6px;text-align:right}'
          + '.foot{text-align:center;font-size:11px;margin-top:8px}'
          + '@page{size:80mm auto;margin:3mm}'
          + '</style></head><body>'
          + '<h1>零售销售单</h1>'
          + '<div class="meta">单号：' + this.esc(head.number || this.lastBill.number) + '<br/>'
          + '时间：' + this.esc(head.oper_time || '') + '<br/>'
          + '会员：' + this.esc(head.memberName || '散客') + '<br/>'
          + '收银员：' + this.esc(head.creatorName || '') + '</div>'
          + '<table><thead><tr><th style="text-align:left">商品</th><th style="text-align:right">数量</th>'
          + '<th style="text-align:right">单价</th><th style="text-align:right">金额</th></tr></thead><tbody>'
          + rows + '</tbody></table>'
          + '<div class="total">合计：' + this.lastBill.totalAmount + ' 元<br/>'
          + '优惠：' + this.lastBill.discountAmount + ' 元<br/>'
          + '应收：' + this.lastBill.payableAmount + ' 元<br/>'
          + '支付：' + this.esc(this.lastBill.payType) + '</div>'
          + '<div class="foot">谢谢惠顾，欢迎再次光临！</div>'
          + '</body></html>')
        win.document.close()
        setTimeout(() => { win.focus(); win.print() }, 300)
      },
      esc(v) {
        return String(v === null || v === undefined ? '' : v)
          .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      }
    }
  }
</script>

<style scoped>
</style>

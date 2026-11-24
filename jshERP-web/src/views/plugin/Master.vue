<template>
  <div>
    <a-radio-group v-model="view" button-style="solid" style="margin-bottom:12px">
      <a-radio-button value="vehicle">车辆管理</a-radio-button>
      <a-radio-button value="follow">客户跟进</a-radio-button>
    </a-radio-group>

    <crud-list v-if="view==='vehicle'" table="jsh_vehicle" :columns="vehicleColumns" :fields="vehicleFields"
               :options="options" hint="记录车牌号、品牌、驾驶员、发动机号等车辆档案"/>
    <crud-list v-else table="jsh_customer_follow" :columns="followColumns" :fields="followFields"
               :options="options" hint="记录客户跟进时间、内容、是否继续跟进与下次跟进时间"/>
  </div>
</template>

<script>
  import CrudList from './components/CrudList'
  import { crud } from '@/api/plugin'

  export default {
    name: 'PluginMaster',
    components: { CrudList },
    props: { tab: { type: String, default: 'vehicle' } },
    data() {
      return {
        view: this.tab || 'vehicle',
        options: {
          customer: [], person: [],
          vehicleStatus: [
            { value: '在用', label: '在用' },
            { value: '维修', label: '维修' },
            { value: '停用', label: '停用' }
          ],
          yesNo: [
            { value: '1', label: '继续跟进' },
            { value: '0', label: '不再跟进' }
          ]
        },
        vehicleColumns: [
          { title: '车牌号', dataIndex: 'plate_no' },
          { title: '品牌', dataIndex: 'brand' },
          { title: '车型', dataIndex: 'model' },
          { title: '驾驶员', dataIndex: 'driver' },
          { title: '驾驶员电话', dataIndex: 'driver_phone' },
          { title: '发动机号', dataIndex: 'engine_no' },
          { title: '车架号', dataIndex: 'vin' },
          { title: '状态', dataIndex: 'status' },
          { title: '备注', dataIndex: 'remark' }
        ],
        vehicleFields: [
          { key: 'plate_no', label: '车牌号' },
          { key: 'brand', label: '车辆品牌' },
          { key: 'model', label: '车型' },
          { key: 'driver', label: '驾驶员' },
          { key: 'driver_phone', label: '驾驶员电话' },
          { key: 'engine_no', label: '发动机号' },
          { key: 'vin', label: '车架号' },
          { key: 'buy_date', label: '购置日期', type: 'date' },
          { key: 'status', label: '状态', type: 'select', optionKey: 'vehicleStatus' },
          { key: 'remark', label: '备注', type: 'textarea' }
        ],
        followColumns: [
          { title: '客户', dataIndex: 'customer_name' },
          { title: '跟进时间', dataIndex: 'follow_time' },
          { title: '跟进内容', dataIndex: 'content' },
          { title: '是否继续', dataIndex: 'is_continue', customRender: t => (t === '1' ? '继续' : '结束') },
          { title: '下次跟进', dataIndex: 'next_time' }
        ],
        followFields: [
          { key: 'customer_id', label: '客户', type: 'select', optionKey: 'customer' },
          { key: 'customer_name', label: '客户名称' },
          { key: 'follow_time', label: '跟进时间', type: 'date' },
          { key: 'content', label: '跟进内容', type: 'textarea' },
          { key: 'is_continue', label: '是否继续', type: 'select', optionKey: 'yesNo' },
          { key: 'next_time', label: '下次跟进时间', type: 'date' },
          { key: 'follower', label: '跟进人', type: 'select', optionKey: 'person' }
        ]
      }
    },
    watch: {
      tab(v) { if (v) this.view = v }
    },
    created() {
      crud.options({ type: 'customer' }).then(r => { this.options.customer = r.data || [] })
      crud.options({ type: 'person' }).then(r => { this.options.person = r.data || [] })
    }
  }
</script>

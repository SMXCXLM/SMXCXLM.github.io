<template>
  <div>
    <a-radio-group v-model="view" button-style="solid" style="margin-bottom:12px">
      <a-radio-button value="asset">资产台账</a-radio-button>
      <a-radio-button value="category">资产类别</a-radio-button>
      <a-radio-button value="dep">折旧表</a-radio-button>
    </a-radio-group>

    <crud-list v-if="view==='category'" table="jsh_fa_category" :columns="catColumns" :fields="catFields"
               :options="options" hint="设置折旧期限(月)、残值率，折旧方法为年限平均法"/>
    <crud-list v-else-if="view==='asset'" ref="assetList" table="jsh_fa_asset" :columns="assetColumns" :fields="assetFields"
               :options="options" showDetail extraText="变更使用人"
               :list-api="listAssets" :save-api="saveAsset" :delete-api="deleteAsset"
               @detail="showHistory" @extra="openChangeUser"
               hint="资产原值、残值率、折旧期限可按类别自动带出"/>
    <div v-else>
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="折旧期间">
          <a-date-picker v-model="period" picker="month" valueFormat="YYYY-MM" style="width:140px"/>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="loadDep">计算</a-button>
        </a-form-item>
      </a-form>
      <a-alert type="info" show-icon style="margin-bottom:10px"
               :message="`本期累计折旧合计 ${depTotal} 元，净值合计 ${depNet} 元（年限平均法：月折旧=(原值-原值×残值率)/折旧期限）`"/>
      <a-table size="middle" bordered rowKey="id" :columns="depColumns" :dataSource="depRows" :pagination="false" :scroll="{x:1300}"/>
    </div>

    <a-modal title="变更使用人" :visible="changeVisible" @ok="submitChangeUser" @cancel="changeVisible=false">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="资产">
          <a-input :value="currentAsset.asset_no + ' ' + (currentAsset.name||'')" disabled/>
        </a-form-item>
        <a-form-item label="新使用人">
          <a-select v-model="newUserId" style="width:100%" allowClear>
            <a-select-option v-for="u in options.user" :key="u.value" :value="u.value">{{u.label}}</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal title="历史使用人" :visible="historyVisible" @cancel="historyVisible=false" footer="" width="700px">
      <a-table size="small" bordered rowKey="id" :columns="historyColumns" :dataSource="historyRows" :pagination="false"/>
    </a-modal>
  </div>
</template>

<script>
  import CrudList from './components/CrudList'
  import { crud, fixedAsset } from '@/api/plugin'

  export default {
    name: 'PluginAsset',
    components: { CrudList },
    data() {
      return {
        view: 'asset',
        period: new Date().toISOString().slice(0, 7),
        depRows: [],
        depTotal: 0,
        depNet: 0,
        options: { user: [], depot: [], category: [], assetStatus: [
          { value: '在用', label: '在用' }, { value: '闲置', label: '闲置' }, { value: '报废', label: '报废' }
        ] },
        changeVisible: false,
        currentAsset: {},
        newUserId: null,
        historyVisible: false,
        historyRows: [],
        historyColumns: [
          { title: '使用人', dataIndex: 'user_name' },
          { title: '开始使用', dataIndex: 'start_time' },
          { title: '结束使用', dataIndex: 'end_time' },
          { title: '备注', dataIndex: 'remark' }
        ],
        catColumns: [
          { title: '类别', dataIndex: 'name' },
          { title: '折旧期限(月)', dataIndex: 'useful_life' },
          { title: '残值率%', dataIndex: 'residual_rate' },
          { title: '折旧方法', dataIndex: 'method' },
          { title: '备注', dataIndex: 'remark' }
        ],
        catFields: [
          { key: 'name', label: '类别名称' },
          { key: 'useful_life', label: '折旧期限(月)', type: 'number' },
          { key: 'residual_rate', label: '残值率(%)', type: 'number' },
          { key: 'method', label: '折旧方法', type: 'input', placeholder: '年限平均法' },
          { key: 'remark', label: '备注', type: 'textarea' }
        ],
        assetColumns: [
          { title: '资产编号', dataIndex: 'asset_no' },
          { title: '资产名称', dataIndex: 'name' },
          { title: '类别', dataIndex: 'categoryName' },
          { title: '原值', dataIndex: 'price' },
          { title: '残值率%', dataIndex: 'residual_rate' },
          { title: '折旧期限(月)', dataIndex: 'useful_life' },
          { title: '购置时间', dataIndex: 'buy_time' },
          { title: '当前使用人', dataIndex: 'currentUserName' },
          { title: '状态', dataIndex: 'status' }
        ],
        assetFields: [
          { key: 'asset_no', label: '资产编号', placeholder: '留空自动生成' },
          { key: 'name', label: '资产名称' },
          { key: 'category_id', label: '资产类别', type: 'select', optionKey: 'category' },
          { key: 'price', label: '资产原值', type: 'number' },
          { key: 'residual_rate', label: '残值率(%)', type: 'number' },
          { key: 'useful_life', label: '折旧期限(月)', type: 'number' },
          { key: 'buy_time', label: '购置时间', type: 'date' },
          { key: 'current_user_id', label: '当前使用人', type: 'select', optionKey: 'user' },
          { key: 'depot_id', label: '存放位置(仓库)', type: 'select', optionKey: 'depot' },
          { key: 'status', label: '状态', type: 'select', optionKey: 'assetStatus' },
          { key: 'remark', label: '备注', type: 'textarea' }
        ],
        depColumns: [
          { title: '资产编号', dataIndex: 'asset_no' },
          { title: '资产名称', dataIndex: 'name' },
          { title: '类别', dataIndex: 'categoryName' },
          { title: '原值', dataIndex: 'price' },
          { title: '残值率%', dataIndex: 'residual_rate' },
          { title: '期限(月)', dataIndex: 'useful_life' },
          { title: '已计提月数', dataIndex: 'usedMonths' },
          { title: '月折旧额', dataIndex: 'monthlyDepreciation' },
          { title: '累计折旧', dataIndex: 'accumulatedDepreciation' },
          { title: '净值', dataIndex: 'netValue' }
        ]
      }
    },
    created() {
      crud.options({ type: 'user' }).then(r => { this.options.user = r.data || [] })
      crud.options({ type: 'depot' }).then(r => { this.options.depot = r.data || [] })
      this.loadCategories()
      this.loadDep()
    },
    methods: {
      listAssets(p) {
        return fixedAsset.list(p)
      },
      saveAsset(p) {
        return fixedAsset.save(p)
      },
      deleteAsset(p) {
        return fixedAsset.del(p)
      },
      loadCategories() {
        crud.list({ table: 'jsh_fa_category', pageSize: 100 }).then(r => {
          this.options.category = ((r.data || {}).rows || []).map(c => ({ value: c.id, label: c.name }))
        })
      },
      loadDep() {
        if (!this.period) return
        fixedAsset.depreciation({ period: this.period }).then(r => {
          const d = r.data || {}
          this.depRows = d.rows || []
          this.depTotal = d.totalDepreciation || 0
          this.depNet = d.totalNetValue || 0
        })
      },
      openChangeUser(record) {
        this.currentAsset = record
        this.newUserId = record.current_user_id
        this.changeVisible = true
      },
      submitChangeUser() {
        if (!this.newUserId) {
          this.$message.warning('请选择使用人')
          return
        }
        fixedAsset.changeUser({ asset_id: this.currentAsset.id, user_id: this.newUserId }).then(r => {
          if (r.code === 200) {
            this.$message.success('变更成功')
            this.changeVisible = false
            this.loadDep()
            if (this.$refs.assetList) this.$refs.assetList.load()
          } else {
            this.$message.error(r.data || '变更失败')
          }
        })
      },
      showHistory(record) {
        fixedAsset.users({ assetId: record.id }).then(r => {
          this.historyRows = r.data || []
          this.historyVisible = true
        })
      }
    }
  }
</script>

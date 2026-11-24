<template>
  <div>
    <a-radio-group v-model="view" button-style="solid" style="margin-bottom:12px">
      <a-radio-button value="bom">BOM 管理</a-radio-button>
      <a-radio-button value="task">生产任务</a-radio-button>
      <a-radio-button value="material">领料/退料/用料</a-radio-button>
      <a-radio-button value="process">工序汇报</a-radio-button>
      <a-radio-button value="inspection">验收记录</a-radio-button>
      <a-radio-button value="instock">完工入库</a-radio-button>
    </a-radio-group>

    <!-- BOM -->
    <div v-if="view==='bom'">
      <div class="table-operator" style="margin-bottom:10px">
        <a-button type="primary" icon="plus" @click="editBom(null)">新增 BOM</a-button>
        <a-input v-model="bomKeyword" placeholder="BOM编号/名称" style="width:200px;margin-left:8px" @pressEnter="loadBom"/>
        <a-button @click="loadBom" style="margin-left:8px">查询</a-button>
      </div>
      <a-table size="middle" bordered rowKey="id" :columns="bomColumns" :dataSource="boms"
               :pagination="bomPage" @change="onBomPage">
        <span slot="op" slot-scope="text, record">
          <a @click="editBom(record)">编辑</a><a-divider type="vertical"/>
          <a-popconfirm title="确定删除吗?" @confirm="() => delBom(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <!-- 生产任务 -->
    <div v-if="view==='task'">
      <div class="table-operator" style="margin-bottom:10px">
        <a-button type="primary" icon="plus" @click="editTask(null)">新建生产任务</a-button>
        <a-select v-model="taskQuery.status" style="width:130px;margin-left:8px" allowClear placeholder="状态">
          <a-select-option value="0">待生产</a-select-option>
          <a-select-option value="1">生产中</a-select-option>
          <a-select-option value="2">已完工</a-select-option>
          <a-select-option value="3">已关闭</a-select-option>
        </a-select>
        <a-input v-model="taskQuery.keyword" placeholder="任务号/商品" style="width:180px;margin-left:8px" @pressEnter="loadTasks"/>
        <a-button @click="loadTasks" style="margin-left:8px">查询</a-button>
      </div>
      <a-table size="middle" bordered rowKey="id" :columns="taskColumns" :dataSource="tasks"
               :pagination="taskPage" @change="onTaskPage">
        <span slot="op" slot-scope="text, record">
          <a @click="openTask(record)">详情</a><a-divider type="vertical"/>
          <a @click="editTask(record)">编辑</a><a-divider type="vertical"/>
          <a-popconfirm title="确定删除吗?" @confirm="() => delTask(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <!-- 领退料 -->
    <div v-if="view==='material'">
      <a-form layout="inline" style="margin-bottom:10px">
        <a-form-item label="类型">
          <a-select v-model="matQuery.type" style="width:120px" allowClear>
            <a-select-option value="领料">领料</a-select-option>
            <a-select-option value="退料">退料</a-select-option>
            <a-select-option value="用料">用料</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item><a-button type="primary" @click="loadMaterials">查询</a-button></a-form-item>
        <a-form-item><a-button icon="plus" @click="openMaterial(null)">登记领退料</a-button></a-form-item>
      </a-form>
      <a-table size="middle" bordered rowKey="id" :columns="materialColumns" :dataSource="materials"
               :pagination="materialPage" @change="onMaterialPage">
        <span slot="op" slot-scope="text, record">
          <a-popconfirm title="删除并回冲库存?" @confirm="() => delMaterial(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <!-- 工序汇报 -->
    <div v-if="view==='process'">
      <div class="table-operator" style="margin-bottom:10px">
        <a-button type="primary" icon="plus" @click="openReport(null)">新增工序汇报</a-button>
      </div>
      <a-table size="middle" bordered rowKey="id" :columns="reportColumns" :dataSource="reports"
               :pagination="reportPage" @change="onReportPage">
        <span slot="op" slot-scope="text, record">
          <a @click="openReport(record)">编辑</a><a-divider type="vertical"/>
          <a-popconfirm title="确定删除吗?" @confirm="() => delReport(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <!-- 验收 -->
    <div v-if="view==='inspection'">
      <div class="table-operator" style="margin-bottom:10px">
        <a-button type="primary" icon="plus" @click="openInspection(null)">新增验收记录</a-button>
      </div>
      <a-table size="middle" bordered rowKey="id" :columns="inspectionColumns" :dataSource="inspections"
               :pagination="inspectionPage" @change="onInspectionPage">
        <span slot="op" slot-scope="text, record">
          <a @click="openInspection(record)">编辑</a><a-divider type="vertical"/>
          <a-popconfirm title="确定删除吗?" @confirm="() => delInspection(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <!-- 完工入库 -->
    <div v-if="view==='instock'">
      <div class="table-operator" style="margin-bottom:10px">
        <a-button type="primary" icon="plus" @click="openInstock(null)">完工入库</a-button>
      </div>
      <a-table size="middle" bordered rowKey="id" :columns="instockColumns" :dataSource="instocks"
               :pagination="instockPage" @change="onInstockPage">
        <span slot="op" slot-scope="text, record">
          <a-popconfirm title="删除并回冲库存?" @confirm="() => delInstock(record)"><a style="color:#f5222d">删除</a></a-popconfirm>
        </span>
      </a-table>
    </div>

    <!-- BOM 编辑 -->
    <a-modal :title="bomForm.id ? '编辑 BOM' : '新增 BOM'" :visible="bomVisible" width="1000px"
             @ok="saveBom" @cancel="bomVisible=false">
      <a-form :label-col="{span:4}" :wrapper-col="{span:19}">
        <a-row :gutter="8">
          <a-col :span="12"><a-form-item label="BOM编号" :label-col="{span:8}"><a-input v-model="bomForm.bom_no"/></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="BOM名称" :label-col="{span:8}"><a-input v-model="bomForm.bom_name"/></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="成品商品" :label-col="{span:8}">
            <a-select v-model="bomForm.material_id" showSearch optionFilterProp="children" style="width:100%">
              <a-select-option v-for="m in options.material" :key="m.value" :value="m.value">{{m.label}}</a-select-option>
            </a-select>
          </a-form-item></a-col>
          <a-col :span="12"><a-form-item label="版本" :label-col="{span:8}"><a-input v-model="bomForm.version"/></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="标准产出" :label-col="{span:8}"><a-input-number v-model="bomForm.output_qty" style="width:100%"/></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="单位" :label-col="{span:8}"><a-input v-model="bomForm.unit"/></a-form-item></a-col>
        </a-row>
        <a-form-item label="备注"><a-input v-model="bomForm.remark"/></a-form-item>

        <a-divider orientation="left">物料明细</a-divider>
        <a-table size="small" bordered rowKey="_key" :columns="bomItemColumns" :dataSource="bomForm.items" :pagination="false">
          <span slot="material" slot-scope="text, record">
            <a-select v-model="record.material_id" showSearch optionFilterProp="children" style="width:100%" @change="v => onBomItemMaterial(record, v)">
              <a-select-option v-for="m in options.material" :key="m.value" :value="m.value">{{m.label}}</a-select-option>
            </a-select>
          </span>
          <span slot="unit" slot-scope="text, record"><a-input v-model="record.unit"/></span>
          <span slot="qty" slot-scope="text, record"><a-input-number v-model="record.qty" style="width:100%"/></span>
          <span slot="loss" slot-scope="text, record"><a-input-number v-model="record.loss_rate" style="width:100%"/></span>
          <span slot="op" slot-scope="text, record"><a @click="removeBomItem(record)" style="color:#f5222d">删除</a></span>
        </a-table>
        <a-button size="small" style="margin-top:6px" @click="addBomItem">加物料行</a-button>

        <a-divider orientation="left">工序</a-divider>
        <a-table size="small" bordered rowKey="_key" :columns="bomProcessColumns" :dataSource="bomForm.processes" :pagination="false">
          <span slot="seq" slot-scope="text, record"><a-input-number v-model="record.seq" style="width:100%"/></span>
          <span slot="name" slot-scope="text, record"><a-input v-model="record.process_name"/></span>
          <span slot="center" slot-scope="text, record"><a-input v-model="record.work_center"/></span>
          <span slot="hours" slot-scope="text, record"><a-input-number v-model="record.standard_hours" style="width:100%"/></span>
          <span slot="op" slot-scope="text, record"><a @click="removeBomProcess(record)" style="color:#f5222d">删除</a></span>
        </a-table>
        <a-button size="small" style="margin-top:6px" @click="addBomProcess">加工序行</a-button>
      </a-form>
    </a-modal>

    <!-- 生产任务编辑 -->
    <a-modal :title="taskForm.id ? '编辑生产任务' : '新建生产任务'" :visible="taskVisible" width="700px"
             @ok="saveTask" @cancel="taskVisible=false">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="BOM">
          <a-select v-model="taskForm.bom_id" showSearch optionFilterProp="children" style="width:100%" @change="onTaskBom">
            <a-select-option v-for="b in boms" :key="b.id" :value="b.id">{{b.bom_no}} {{b.bom_name}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="计划数量"><a-input-number v-model="taskForm.plan_qty" style="width:100%"/></a-form-item>
        <a-form-item label="计划开工">
          <a-date-picker v-model="taskForm.start_date" style="width:100%" showTime format="YYYY-MM-DD HH:mm:ss"
                         valueFormat="YYYY-MM-DD HH:mm:ss"/>
        </a-form-item>
        <a-form-item label="计划完工">
          <a-date-picker v-model="taskForm.end_date" style="width:100%" showTime format="YYYY-MM-DD HH:mm:ss"
                         valueFormat="YYYY-MM-DD HH:mm:ss"/>
        </a-form-item>
        <a-form-item label="车间/班组"><a-input v-model="taskForm.workshop"/></a-form-item>
        <a-form-item label="状态">
          <a-select v-model="taskForm.status" style="width:100%">
            <a-select-option value="0">待生产</a-select-option>
            <a-select-option value="1">生产中</a-select-option>
            <a-select-option value="2">已完工</a-select-option>
            <a-select-option value="3">已关闭</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注"><a-textarea v-model="taskForm.remark" :rows="2"/></a-form-item>
      </a-form>
    </a-modal>

    <!-- 任务详情 -->
    <a-modal :title="'生产任务 ' + (taskDetail.task ? taskDetail.task.task_no : '')" :visible="detailVisible"
             width="1060px" @cancel="detailVisible=false" footer="">
      <a-descriptions size="small" bordered :column="3" style="margin-bottom:10px">
        <a-descriptions-item label="成品">{{ taskDetail.task ? taskDetail.task.materialName : '' }}</a-descriptions-item>
        <a-descriptions-item label="计划数量">{{ taskDetail.task ? taskDetail.task.plan_qty : '' }}</a-descriptions-item>
        <a-descriptions-item label="已完工">{{ taskDetail.task ? taskDetail.task.finished_qty : '' }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ statusText(taskDetail.task && taskDetail.task.status) }}</a-descriptions-item>
        <a-descriptions-item label="车间">{{ taskDetail.task ? taskDetail.task.workshop : '' }}</a-descriptions-item>
        <a-descriptions-item label="BOM">{{ taskDetail.task ? taskDetail.task.bomName : '' }}</a-descriptions-item>
      </a-descriptions>
      <div style="margin-bottom:8px">
        <a-button size="small" type="primary" @click="openMaterial(null)">领料/退料/用料</a-button>
        <a-button size="small" style="margin-left:8px" @click="openReport(null)">工序汇报</a-button>
        <a-button size="small" style="margin-left:8px" @click="openInspection(null)">验收</a-button>
        <a-button size="small" style="margin-left:8px" @click="openInstock(null)">完工入库</a-button>
      </div>

      <a-divider orientation="left">应领料清单（按 BOM 用量）</a-divider>
      <a-table size="small" bordered rowKey="materialId" :columns="needColumns" :dataSource="taskDetail.bomItems || []"
               :pagination="false"/>
      <a-divider orientation="left">领退料记录</a-divider>
      <a-table size="small" bordered rowKey="id" :columns="detailMaterialColumns" :dataSource="taskDetail.materials || []"
               :pagination="false"/>
      <a-divider orientation="left">工序汇报</a-divider>
      <a-table size="small" bordered rowKey="id" :columns="reportColumns" :dataSource="taskDetail.reports || []" :pagination="false"/>
      <a-divider orientation="left">验收记录</a-divider>
      <a-table size="small" bordered rowKey="id" :columns="inspectionColumns" :dataSource="taskDetail.inspections || []" :pagination="false"/>
    </a-modal>

    <!-- 领退料登记 -->
    <a-modal title="登记领料/退料/用料" :visible="materialVisible" @ok="saveMaterial" @cancel="materialVisible=false">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="生产任务">
          <a-select v-model="materialForm.task_id" showSearch optionFilterProp="children" style="width:100%">
            <a-select-option v-for="t in tasks" :key="t.id" :value="t.id">{{t.task_no}} {{t.materialName}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model="materialForm.type" style="width:100%">
            <a-select-option value="领料">领料</a-select-option>
            <a-select-option value="退料">退料</a-select-option>
            <a-select-option value="用料">用料</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="物料">
          <a-select v-model="materialForm.material_id" showSearch optionFilterProp="children" style="width:100%">
            <a-select-option v-for="m in options.material" :key="m.value" :value="m.value">{{m.label}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="仓库">
          <a-select v-model="materialForm.depot_id" style="width:100%">
            <a-select-option v-for="d in options.depot" :key="d.value" :value="d.value">{{d.label}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="数量"><a-input-number v-model="materialForm.qty" style="width:100%"/></a-form-item>
        <a-form-item label="单位"><a-input v-model="materialForm.unit"/></a-form-item>
        <a-form-item label="经办人">
          <a-select v-model="materialForm.operator" style="width:100%" allowClear>
            <a-select-option v-for="p in options.person" :key="p.value" :value="p.value">{{p.label}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注"><a-input v-model="materialForm.remark"/></a-form-item>
      </a-form>
    </a-modal>

    <!-- 工序汇报 -->
    <a-modal title="工序汇报" :visible="reportVisible" @ok="saveReport" @cancel="reportVisible=false">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="生产任务">
          <a-select v-model="reportForm.task_id" showSearch optionFilterProp="children" style="width:100%">
            <a-select-option v-for="t in tasks" :key="t.id" :value="t.id">{{t.task_no}} {{t.materialName}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="工序名称"><a-input v-model="reportForm.process_name"/></a-form-item>
        <a-form-item label="汇报数量"><a-input-number v-model="reportForm.report_qty" style="width:100%"/></a-form-item>
        <a-form-item label="合格数量"><a-input-number v-model="reportForm.qualified_qty" style="width:100%"/></a-form-item>
        <a-form-item label="不合格数量"><a-input-number v-model="reportForm.unqualified_qty" style="width:100%"/></a-form-item>
        <a-form-item label="报工人"><a-input v-model="reportForm.worker"/></a-form-item>
        <a-form-item label="汇报时间">
          <a-date-picker v-model="reportForm.report_time" style="width:100%" showTime format="YYYY-MM-DD HH:mm:ss"
                         valueFormat="YYYY-MM-DD HH:mm:ss"/>
        </a-form-item>
        <a-form-item label="备注"><a-input v-model="reportForm.remark"/></a-form-item>
      </a-form>
    </a-modal>

    <!-- 验收 -->
    <a-modal title="验收记录" :visible="inspectionVisible" @ok="saveInspection" @cancel="inspectionVisible=false">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="生产任务">
          <a-select v-model="inspectionForm.task_id" showSearch optionFilterProp="children" style="width:100%">
            <a-select-option v-for="t in tasks" :key="t.id" :value="t.id">{{t.task_no}} {{t.materialName}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="送检数量"><a-input-number v-model="inspectionForm.inspect_qty" style="width:100%"/></a-form-item>
        <a-form-item label="合格数量"><a-input-number v-model="inspectionForm.qualified_qty" style="width:100%"/></a-form-item>
        <a-form-item label="不合格数量"><a-input-number v-model="inspectionForm.unqualified_qty" style="width:100%"/></a-form-item>
        <a-form-item label="验收人"><a-input v-model="inspectionForm.inspector"/></a-form-item>
        <a-form-item label="验收结果">
          <a-select v-model="inspectionForm.result" style="width:100%">
            <a-select-option value="合格">合格</a-select-option>
            <a-select-option value="不合格">不合格</a-select-option>
            <a-select-option value="让步接收">让步接收</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="验收时间">
          <a-date-picker v-model="inspectionForm.inspect_time" style="width:100%" showTime format="YYYY-MM-DD HH:mm:ss"
                         valueFormat="YYYY-MM-DD HH:mm:ss"/>
        </a-form-item>
        <a-form-item label="备注"><a-input v-model="inspectionForm.remark"/></a-form-item>
      </a-form>
    </a-modal>

    <!-- 完工入库 -->
    <a-modal title="完工入库" :visible="instockVisible" @ok="saveInstock" @cancel="instockVisible=false">
      <a-form :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="生产任务">
          <a-select v-model="instockForm.task_id" showSearch optionFilterProp="children" style="width:100%" @change="onInstockTask">
            <a-select-option v-for="t in tasks" :key="t.id" :value="t.id">{{t.task_no}} {{t.materialName}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="入库商品">
          <a-select v-model="instockForm.material_id" showSearch optionFilterProp="children" style="width:100%">
            <a-select-option v-for="m in options.material" :key="m.value" :value="m.value">{{m.label}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="入库仓库">
          <a-select v-model="instockForm.depot_id" style="width:100%">
            <a-select-option v-for="d in options.depot" :key="d.value" :value="d.value">{{d.label}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="入库数量"><a-input-number v-model="instockForm.qty" style="width:100%"/></a-form-item>
        <a-form-item label="单位"><a-input v-model="instockForm.unit"/></a-form-item>
        <a-form-item label="成本单价"><a-input-number v-model="instockForm.unit_price" style="width:100%"/></a-form-item>
        <a-form-item label="备注"><a-input v-model="instockForm.remark"/></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
  import { production, crud } from '@/api/plugin'

  const STATUS = { '0': '待生产', '1': '生产中', '2': '已完工', '3': '已关闭' }

  export default {
    name: 'PluginProd',
    data() {
      return {
        view: 'bom',
        options: { material: [], depot: [], person: [] },
        boms: [],
        bomKeyword: '',
        bomPage: { current: 1, pageSize: 20, total: 0 },
        bomVisible: false,
        bomForm: { items: [], processes: [], output_qty: 1 },
        tasks: [],
        taskQuery: { status: null, keyword: '' },
        taskPage: { current: 1, pageSize: 20, total: 0 },
        taskVisible: false,
        taskForm: { plan_qty: 1, status: '0' },
        detailVisible: false,
        taskDetail: {},
        materials: [],
        matQuery: { type: null, taskId: null },
        materialPage: { current: 1, pageSize: 20, total: 0 },
        materialVisible: false,
        materialForm: { type: '领料' },
        reports: [],
        reportPage: { current: 1, pageSize: 20, total: 0 },
        reportVisible: false,
        reportForm: {},
        inspections: [],
        inspectionPage: { current: 1, pageSize: 20, total: 0 },
        inspectionVisible: false,
        inspectionForm: { result: '合格' },
        instocks: [],
        instockPage: { current: 1, pageSize: 20, total: 0 },
        instockVisible: false,
        instockForm: {},
        bomColumns: [
          { title: 'BOM编号', dataIndex: 'bom_no' },
          { title: 'BOM名称', dataIndex: 'bom_name' },
          { title: '成品', dataIndex: 'materialName' },
          { title: '标准产出', dataIndex: 'output_qty' },
          { title: '单位', dataIndex: 'unit' },
          { title: '版本', dataIndex: 'version' },
          { title: '启用', dataIndex: 'enabled', customRender: t => (t === '0' ? '停用' : '启用') },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 120 }
        ],
        taskColumns: [
          { title: '任务号', dataIndex: 'task_no' },
          { title: '成品', dataIndex: 'materialName' },
          { title: '计划数量', dataIndex: 'plan_qty' },
          { title: '已完工', dataIndex: 'finished_qty' },
          { title: '车间', dataIndex: 'workshop' },
          { title: '计划开工', dataIndex: 'start_date', customRender: t => String(t || '').substring(0, 10) },
          { title: '状态', dataIndex: 'status', customRender: t => STATUS[t] || t },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 200 }
        ],
        materialColumns: [
          { title: '任务号', dataIndex: 'taskNo' },
          { title: '类型', dataIndex: 'type' },
          { title: '物料', dataIndex: 'materialName' },
          { title: '仓库', dataIndex: 'depotName' },
          { title: '数量', dataIndex: 'qty' },
          { title: '单位', dataIndex: 'unit' },
          { title: '时间', dataIndex: 'oper_time' },
          { title: '经办人', dataIndex: 'operatorName' },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 90 }
        ],
        detailMaterialColumns: [
          { title: '类型', dataIndex: 'type' },
          { title: '物料', dataIndex: 'materialName' },
          { title: '数量', dataIndex: 'qty' },
          { title: '单位', dataIndex: 'unit' },
          { title: '仓库', dataIndex: 'depotName' },
          { title: '时间', dataIndex: 'oper_time' },
          { title: '经办人', dataIndex: 'operatorName' }
        ],
        reportColumns: [
          { title: '任务号', dataIndex: 'taskNo' },
          { title: '工序', dataIndex: 'process_name' },
          { title: '汇报数量', dataIndex: 'report_qty' },
          { title: '合格', dataIndex: 'qualified_qty' },
          { title: '不合格', dataIndex: 'unqualified_qty' },
          { title: '报工人', dataIndex: 'worker' },
          { title: '汇报时间', dataIndex: 'report_time' },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 100 }
        ],
        inspectionColumns: [
          { title: '验收单号', dataIndex: 'inspect_no' },
          { title: '送检数量', dataIndex: 'inspect_qty' },
          { title: '合格', dataIndex: 'qualified_qty' },
          { title: '不合格', dataIndex: 'unqualified_qty' },
          { title: '验收人', dataIndex: 'inspector' },
          { title: '结果', dataIndex: 'result' },
          { title: '验收时间', dataIndex: 'inspect_time' },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 100 }
        ],
        instockColumns: [
          { title: '入库单号', dataIndex: 'number' },
          { title: '任务号', dataIndex: 'taskNo' },
          { title: '商品', dataIndex: 'materialName' },
          { title: '仓库', dataIndex: 'depotName' },
          { title: '数量', dataIndex: 'qty' },
          { title: '单位', dataIndex: 'unit' },
          { title: '成本单价', dataIndex: 'unit_price' },
          { title: '入库时间', dataIndex: 'oper_time' },
          { title: '操作', dataIndex: 'op', scopedSlots: { customRender: 'op' }, width: 90 }
        ],
        needColumns: [
          { title: '物料', dataIndex: 'materialName' },
          { title: '规格', dataIndex: 'standard' },
          { title: '型号', dataIndex: 'model' },
          { title: '单位', dataIndex: 'unit' },
          { title: 'BOM用量', dataIndex: 'qty' },
          { title: '损耗率%', dataIndex: 'lossRate' },
          { title: '应领数量', dataIndex: 'needQty' }
        ],
        bomItemColumns: [
          { title: '物料', dataIndex: 'material_id', scopedSlots: { customRender: 'material' } },
          { title: '单位', dataIndex: 'unit', width: '90px', scopedSlots: { customRender: 'unit' } },
          { title: '用量', dataIndex: 'qty', width: '110px', scopedSlots: { customRender: 'qty' } },
          { title: '损耗率%', dataIndex: 'loss_rate', width: '100px', scopedSlots: { customRender: 'loss' } },
          { title: '操作', dataIndex: 'op', width: '70px', scopedSlots: { customRender: 'op' } }
        ],
        bomProcessColumns: [
          { title: '顺序', dataIndex: 'seq', width: '90px', scopedSlots: { customRender: 'seq' } },
          { title: '工序名称', dataIndex: 'process_name', scopedSlots: { customRender: 'name' } },
          { title: '工作中心', dataIndex: 'work_center', scopedSlots: { customRender: 'center' } },
          { title: '标准工时', dataIndex: 'standard_hours', width: '110px', scopedSlots: { customRender: 'hours' } },
          { title: '操作', dataIndex: 'op', width: '70px', scopedSlots: { customRender: 'op' } }
        ]
      }
    },
    created() {
      crud.options({ type: 'material' }).then(r => { this.options.material = r.data || [] })
      crud.options({ type: 'depot' }).then(r => { this.options.depot = r.data || [] })
      crud.options({ type: 'person' }).then(r => { this.options.person = r.data || [] })
      this.loadBom()
      this.loadTasks()
    },
    methods: {
      statusText(s) {
        return STATUS[s] || s || ''
      },
      loadBom() {
        production.bomList({ keyword: this.bomKeyword, currentPage: this.bomPage.current, pageSize: this.bomPage.pageSize }).then(r => {
          const d = r.data || {}
          this.boms = d.rows || []
          this.bomPage.total = d.total || 0
        })
      },
      onBomPage(p) {
        this.bomPage.current = p.current
        this.bomPage.pageSize = p.pageSize
        this.loadBom()
      },
      editBom(record) {
        if (!record) {
          this.bomForm = { items: [], processes: [], output_qty: 1, enabled: '1' }
          this.bomVisible = true
          return
        }
        production.bomGet({ id: record.id }).then(r => {
          const d = r.data || {}
          this.bomForm = Object.assign({}, d.bom, {
            items: (d.items || []).map(i => Object.assign({}, i, { _key: this.uuid() })),
            processes: (d.processes || []).map(p => Object.assign({}, p, { _key: this.uuid() }))
          })
          this.bomVisible = true
        })
      },
      addBomItem() {
        this.bomForm.items.push({ _key: this.uuid(), material_id: null, unit: '', qty: 1, loss_rate: 0 })
      },
      removeBomItem(record) {
        const i = this.bomForm.items.findIndex(x => x._key === record._key)
        if (i >= 0) this.bomForm.items.splice(i, 1)
      },
      onBomItemMaterial(record, id) {
        const m = this.options.material.find(x => x.value === id)
        if (m) record.unit = m.unit || record.unit
        record.material_name = m ? m.label : ''
        record.material_extend_id = m ? m.extendId : null
      },
      addBomProcess() {
        this.bomForm.processes.push({ _key: this.uuid(), seq: this.bomForm.processes.length + 1, process_name: '', work_center: '', standard_hours: 0 })
      },
      removeBomProcess(record) {
        const i = this.bomForm.processes.findIndex(x => x._key === record._key)
        if (i >= 0) this.bomForm.processes.splice(i, 1)
      },
      saveBom() {
        production.bomSave(this.bomForm).then(r => {
          if (r.code === 200) {
            this.$message.success('BOM 已保存')
            this.bomVisible = false
            this.loadBom()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      delBom(record) {
        production.bomDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.loadBom()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },

      loadTasks() {
        production.taskList(Object.assign({}, this.taskQuery, {
          currentPage: this.taskPage.current, pageSize: this.taskPage.pageSize
        })).then(r => {
          const d = r.data || {}
          this.tasks = d.rows || []
          this.taskPage.total = d.total || 0
        })
      },
      onTaskPage(p) {
        this.taskPage.current = p.current
        this.taskPage.pageSize = p.pageSize
        this.loadTasks()
      },
      editTask(record) {
        if (!record) {
          this.taskForm = { plan_qty: 1, status: '0' }
          this.taskVisible = true
          return
        }
        this.taskForm = Object.assign({}, record)
        this.taskVisible = true
      },
      onTaskBom(bomId) {
        const b = this.boms.find(x => x.id === bomId)
        if (b) {
          this.taskForm.material_id = b.material_id
          this.taskForm.material_extend_id = b.material_extend_id
          this.taskForm.unit = b.unit
        }
      },
      saveTask() {
        production.taskSave(this.taskForm).then(r => {
          if (r.code === 200) {
            this.$message.success('生产任务已保存')
            this.taskVisible = false
            this.loadTasks()
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      delTask(record) {
        production.taskDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.loadTasks()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },
      openTask(record) {
        production.taskGet({ id: record.id }).then(r => {
          this.taskDetail = r.data || {}
          this.detailVisible = true
        })
      },

      loadMaterials() {
        production.materialList(Object.assign({}, this.matQuery, {
          currentPage: this.materialPage.current, pageSize: this.materialPage.pageSize
        })).then(r => {
          const d = r.data || {}
          this.materials = d.rows || []
          this.materialPage.total = d.total || 0
        })
      },
      onMaterialPage(p) {
        this.materialPage.current = p.current
        this.materialPage.pageSize = p.pageSize
        this.loadMaterials()
      },
      openMaterial(record) {
        if (record) {
          this.materialForm = Object.assign({}, record)
        } else {
          const task = this.taskDetail.task
          this.materialForm = { type: '领料', task_id: task ? task.id : null }
        }
        this.materialVisible = true
      },
      saveMaterial() {
        if (!this.materialForm.task_id) {
          this.$message.warning('请选择生产任务')
          return
        }
        production.materialSave(this.materialForm).then(r => {
          if (r.code === 200) {
            this.$message.success('已登记，库存已联动')
            this.materialVisible = false
            if (this.view === 'material') this.loadMaterials()
            if (this.detailVisible) this.openTask(this.taskDetail.task)
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      delMaterial(record) {
        production.materialDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除并回冲库存')
            this.loadMaterials()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },

      loadReports() {
        production.reportList({ currentPage: this.reportPage.current, pageSize: this.reportPage.pageSize }).then(r => {
          const d = r.data || {}
          this.reports = d.rows || []
          this.reportPage.total = d.total || 0
        })
      },
      onReportPage(p) {
        this.reportPage.current = p.current
        this.reportPage.pageSize = p.pageSize
        this.loadReports()
      },
      openReport(record) {
        if (record) {
          this.reportForm = Object.assign({}, record)
        } else {
          const task = this.taskDetail.task
          this.reportForm = { task_id: task ? task.id : null }
        }
        this.reportVisible = true
      },
      saveReport() {
        production.reportSave(this.reportForm).then(r => {
          if (r.code === 200) {
            this.$message.success('已保存')
            this.reportVisible = false
            if (this.view === 'process') this.loadReports()
            if (this.detailVisible && this.taskDetail.task) this.openTask(this.taskDetail.task)
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      delReport(record) {
        production.reportDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.loadReports()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },

      loadInspections() {
        production.inspectionList({ currentPage: this.inspectionPage.current, pageSize: this.inspectionPage.pageSize }).then(r => {
          const d = r.data || {}
          this.inspections = d.rows || []
          this.inspectionPage.total = d.total || 0
        })
      },
      onInspectionPage(p) {
        this.inspectionPage.current = p.current
        this.inspectionPage.pageSize = p.pageSize
        this.loadInspections()
      },
      openInspection(record) {
        if (record) {
          this.inspectionForm = Object.assign({}, record)
        } else {
          const task = this.taskDetail.task
          this.inspectionForm = { result: '合格', task_id: task ? task.id : null }
        }
        this.inspectionVisible = true
      },
      saveInspection() {
        production.inspectionSave(this.inspectionForm).then(r => {
          if (r.code === 200) {
            this.$message.success('已保存')
            this.inspectionVisible = false
            if (this.view === 'inspection') this.loadInspections()
            if (this.detailVisible && this.taskDetail.task) this.openTask(this.taskDetail.task)
          } else {
            this.$message.error(r.data || '保存失败')
          }
        })
      },
      delInspection(record) {
        production.inspectionDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.loadInspections()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },

      loadInstocks() {
        production.instockList({ currentPage: this.instockPage.current, pageSize: this.instockPage.pageSize }).then(r => {
          const d = r.data || {}
          this.instocks = d.rows || []
          this.instockPage.total = d.total || 0
        })
      },
      onInstockPage(p) {
        this.instockPage.current = p.current
        this.instockPage.pageSize = p.pageSize
        this.loadInstocks()
      },
      openInstock(record) {
        if (record) {
          this.instockForm = Object.assign({}, record)
        } else {
          const task = this.taskDetail.task
          this.instockForm = {
            task_id: task ? task.id : null, material_id: task ? task.material_id : null,
            unit: task ? task.unit : ''
          }
        }
        this.instockVisible = true
      },
      onInstockTask(taskId) {
        const t = this.tasks.find(x => x.id === taskId)
        if (t) {
          this.instockForm.material_id = t.material_id
          this.instockForm.unit = t.unit
        }
      },
      saveInstock() {
        production.instockSave(this.instockForm).then(r => {
          if (r.code === 200) {
            this.$message.success('完工入库成功，库存已增加')
            this.instockVisible = false
            if (this.view === 'instock') this.loadInstocks()
            if (this.detailVisible && this.taskDetail.task) this.openTask(this.taskDetail.task)
          } else {
            this.$message.error(r.data || '入库失败')
          }
        })
      },
      delInstock(record) {
        production.instockDelete({ id: record.id }).then(r => {
          if (r.code === 200) {
            this.$message.success('已删除')
            this.loadInstocks()
          } else {
            this.$message.error(r.data || '删除失败')
          }
        })
      },
      uuid() {
        return 'k' + Math.random().toString(36).slice(2, 10)
      }
    },
    watch: {
      view(v) {
        if (v === 'bom') this.loadBom()
        if (v === 'task') this.loadTasks()
        if (v === 'material') this.loadMaterials()
        if (v === 'process') this.loadReports()
        if (v === 'inspection') this.loadInspections()
        if (v === 'instock') this.loadInstocks()
      }
    }
  }
</script>

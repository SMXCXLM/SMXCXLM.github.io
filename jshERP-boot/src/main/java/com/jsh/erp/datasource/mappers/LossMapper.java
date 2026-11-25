package com.jsh.erp.datasource.mappers;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 报损管理数据访问（注解式，避免依赖 XML）
 */
@Mapper
public interface LossMapper {

    @Select({"<script>",
            "SELECT b.id, b.bill_number AS billNumber, b.depot_id AS depotId, b.depot_name AS depotName,",
            " b.reason, b.status, b.applicant_id AS applicantId, b.applicant_name AS applicantName,",
            " DATE_FORMAT(b.apply_time,'%Y-%m-%d %H:%i') AS applyTime,",
            " b.total_qty AS totalQty, b.total_amount AS totalAmount,",
            " b.cfo_name AS cfoName, DATE_FORMAT(b.cfo_time,'%Y-%m-%d %H:%i') AS cfoTime, b.cfo_comment AS cfoComment,",
            " b.ceo_name AS ceoName, DATE_FORMAT(b.ceo_time,'%Y-%m-%d %H:%i') AS ceoTime, b.ceo_comment AS ceoComment,",
            " b.depot_head_number AS depotHeadNumber, b.remark",
            "FROM jsh_loss_bill b",
            "WHERE b.delete_flag = '0' AND b.tenant_id = #{tenantId}",
            "<if test=\"status != null and status != ''\"> AND b.status = #{status} </if>",
            "<if test=\"keyword != null and keyword != ''\"> AND (b.bill_number LIKE CONCAT('%',#{keyword},'%') OR b.reason LIKE CONCAT('%',#{keyword},'%') OR b.applicant_name LIKE CONCAT('%',#{keyword},'%')) </if>",
            "<if test=\"mine != null and mine == 1\"> AND b.applicant_id = #{userId} </if>",
            "ORDER BY b.id DESC",
            "LIMIT #{offset}, #{rows}",
            "</script>"})
    List<Map<String, Object>> selectBills(@Param("tenantId") Long tenantId,
                                         @Param("userId") Long userId,
                                         @Param("mine") Integer mine,
                                         @Param("status") String status,
                                         @Param("keyword") String keyword,
                                         @Param("offset") int offset,
                                         @Param("rows") int rows);

    @Select({"<script>",
            "SELECT COUNT(*) FROM jsh_loss_bill b",
            "WHERE b.delete_flag = '0' AND b.tenant_id = #{tenantId}",
            "<if test=\"status != null and status != ''\"> AND b.status = #{status} </if>",
            "<if test=\"keyword != null and keyword != ''\"> AND (b.bill_number LIKE CONCAT('%',#{keyword},'%') OR b.reason LIKE CONCAT('%',#{keyword},'%') OR b.applicant_name LIKE CONCAT('%',#{keyword},'%')) </if>",
            "<if test=\"mine != null and mine == 1\"> AND b.applicant_id = #{userId} </if>",
            "</script>"})
    int countBills(@Param("tenantId") Long tenantId,
                   @Param("userId") Long userId,
                   @Param("mine") Integer mine,
                   @Param("status") String status,
                   @Param("keyword") String keyword);

    @Select("SELECT b.id, b.bill_number AS billNumber, b.depot_id AS depotId, b.depot_name AS depotName," +
            " b.reason, b.status, b.applicant_id AS applicantId, b.applicant_name AS applicantName," +
            " DATE_FORMAT(b.apply_time,'%Y-%m-%d %H:%i') AS applyTime," +
            " b.total_qty AS totalQty, b.total_amount AS totalAmount," +
            " b.cfo_id AS cfoId, b.cfo_name AS cfoName, DATE_FORMAT(b.cfo_time,'%Y-%m-%d %H:%i') AS cfoTime, b.cfo_comment AS cfoComment," +
            " b.ceo_id AS ceoId, b.ceo_name AS ceoName, DATE_FORMAT(b.ceo_time,'%Y-%m-%d %H:%i') AS ceoTime, b.ceo_comment AS ceoComment," +
            " b.depot_head_id AS depotHeadId, b.depot_head_number AS depotHeadNumber, b.remark, b.tenant_id AS tenantId" +
            " FROM jsh_loss_bill b WHERE b.id = #{id} AND b.delete_flag = '0'")
    Map<String, Object> selectBill(@Param("id") Long id);

    @Select("SELECT i.id, i.bill_id AS billId, i.material_id AS materialId, i.material_extend_id AS materialExtendId," +
            " i.material_name AS materialName, i.material_model AS materialModel, i.unit," +
            " i.quantity, i.basic_number AS basicNumber, i.unit_price AS unitPrice, i.amount," +
            " i.stock_number AS stockNumber, i.remark" +
            " FROM jsh_loss_item i WHERE i.bill_id = #{billId} AND i.delete_flag = '0' ORDER BY i.id")
    List<Map<String, Object>> selectItems(@Param("billId") Long billId);

    @Select("SELECT l.id, l.step, l.action, l.operator_id AS operatorId, l.operator_name AS operatorName," +
            " l.comment, DATE_FORMAT(l.create_time,'%Y-%m-%d %H:%i') AS createTime" +
            " FROM jsh_loss_log l WHERE l.bill_id = #{billId} ORDER BY l.id")
    List<Map<String, Object>> selectLogs(@Param("billId") Long billId);

    @Select({"<script>",
            "SELECT f.id AS flowId, f.max_level AS maxLevel, f.flow_name AS flowName FROM jsh_af_flow f",
            "WHERE f.bill_type = '报损出库' AND f.enabled = '1' AND f.delete_flag = '0'",
            "<if test=\"tenantId != null\"> AND (f.tenant_id = #{tenantId} OR f.tenant_id IS NULL) </if>",
            "ORDER BY f.tenant_id DESC, f.id DESC LIMIT 1",
            "</script>"})
    Map<String, Object> selectFlow(@Param("tenantId") Long tenantId);

    @Select("SELECT l.id, l.flow_id AS flowId, l.level, l.approver_type AS approverType, l.approver_id AS approverId," +
            " (SELECT u.username FROM jsh_user u WHERE u.id = l.approver_id LIMIT 1) AS approverName" +
            " FROM jsh_af_flow_level l WHERE l.flow_id = #{flowId} AND l.delete_flag = '0' ORDER BY l.level")
    List<Map<String, Object>> selectFlowLevels(@Param("flowId") Long flowId);

    @Insert("INSERT INTO jsh_loss_bill (bill_number, depot_id, depot_name, reason, status," +
            " applicant_id, applicant_name, apply_time, total_qty, total_amount, remark," +
            " delete_flag, create_time, update_time) VALUES (#{billNumber}, #{depotId}, #{depotName}, #{reason}, #{status}," +
            " #{applicantId}, #{applicantName}, NOW(), #{totalQty}, #{totalAmount}, #{remark}," +
            " '0', NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBill(Map<String, Object> map);

    @Update("UPDATE jsh_loss_bill SET depot_id = #{depotId}, depot_name = #{depotName}, reason = #{reason}," +
            " status = #{status}, total_qty = #{totalQty}, total_amount = #{totalAmount}, remark = #{remark}," +
            " cfo_id = NULL, cfo_name = NULL, cfo_time = NULL, cfo_comment = NULL," +
            " ceo_id = NULL, ceo_name = NULL, ceo_time = NULL, ceo_comment = NULL," +
            " update_time = NOW(), apply_time = NOW() WHERE id = #{id}")
    int updateBillBase(Map<String, Object> map);

    @Update("UPDATE jsh_loss_bill SET status = '1', cfo_id = #{userId}, cfo_name = #{userName}, cfo_time = NOW()," +
            " cfo_comment = #{comment}, update_time = NOW() WHERE id = #{id}")
    int updateCfoPass(@Param("id") Long id, @Param("userId") Long userId,
                      @Param("userName") String userName, @Param("comment") String comment);

    @Update("UPDATE jsh_loss_bill SET status = '2', ceo_id = #{userId}, ceo_name = #{userName}, ceo_time = NOW()," +
            " ceo_comment = #{comment}, depot_head_id = #{depotHeadId}, depot_head_number = #{depotHeadNumber}," +
            " update_time = NOW() WHERE id = #{id}")
    int updateCeoPass(@Param("id") Long id, @Param("userId") Long userId, @Param("userName") String userName,
                      @Param("comment") String comment, @Param("depotHeadId") Long depotHeadId,
                      @Param("depotHeadNumber") String depotHeadNumber);

    @Update("UPDATE jsh_loss_bill SET status = '3', update_time = NOW() WHERE id = #{id}")
    int updateReject(@Param("id") Long id);

    @Update("UPDATE jsh_loss_bill SET delete_flag = '1', update_time = NOW() WHERE id = #{id}")
    int deleteBill(@Param("id") Long id);

    @Insert("INSERT INTO jsh_loss_item (bill_id, material_id, material_extend_id, material_name, material_model," +
            " unit, quantity, basic_number, unit_price, amount, stock_number, remark, delete_flag)" +
            " VALUES (#{billId}, #{materialId}, #{materialExtendId}, #{materialName}, #{materialModel}," +
            " #{unit}, #{quantity}, #{basicNumber}, #{unitPrice}, #{amount}, #{stockNumber}, #{remark}, '0')")
    int insertItem(Map<String, Object> map);

    @Delete("DELETE FROM jsh_loss_item WHERE bill_id = #{billId}")
    int deleteItems(@Param("billId") Long billId);

    @Insert("INSERT INTO jsh_loss_log (bill_id, step, action, operator_id, operator_name, comment, create_time)" +
            " VALUES (#{billId}, #{step}, #{action}, #{operatorId}, #{operatorName}, #{comment}, NOW())")
    int insertLog(Map<String, Object> map);

    @Select("SELECT me.id FROM jsh_material_extend me WHERE me.material_id = #{materialId} AND me.delete_flag = '0'" +
            " ORDER BY me.id LIMIT 1")
    Long selectDefaultExtendId(@Param("materialId") Long materialId);

    @Select("SELECT me.material_id FROM jsh_material_extend me WHERE me.id = #{extendId} AND me.delete_flag = '0' LIMIT 1")
    Long selectMaterialIdByExtendId(@Param("extendId") Long extendId);

    @Insert("INSERT INTO jsh_depot_head (type, sub_type, default_number, number, create_time, oper_time, creator," +
            " bill_type, remark, status, total_price, change_amount, discount, discount_money, discount_last_money," +
            " source, delete_flag)" +
            " VALUES ('出库', '其它', #{number}, #{number}, NOW(), NOW(), #{creator}, '报损出库', #{remark}, '1'," +
            " #{totalAmount}, #{totalAmount}, #{totalAmount}, 0, #{totalAmount}, '0', '0')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDepotHead(Map<String, Object> map);

    @Insert("INSERT INTO jsh_depot_item (header_id, material_id, material_extend_id, material_unit, sku," +
            " oper_number, basic_number, unit_price, purchase_unit_price, tax_unit_price, all_price, remark, depot_id," +
            " tax_rate, tax_money, tax_last_money, delete_flag)" +
            " VALUES (#{headerId}, #{materialId}, #{materialExtendId}, #{materialUnit}, ''," +
            " #{operNumber}, #{basicNumber}, #{unitPrice}, #{unitPrice}, #{unitPrice}, #{allPrice}, #{remark}, #{depotId}," +
            " 0, 0, 0, '0')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDepotItem(Map<String, Object> map);

    @Select("SELECT u.username FROM jsh_user u WHERE u.id = #{userId}")
    String selectUserName(@Param("userId") Long userId);

    @Select("SELECT d.name FROM jsh_depot d WHERE d.id = #{depotId} AND d.delete_flag = '0'")
    String selectDepotName(@Param("depotId") Long depotId);

    @Select("SELECT COUNT(*) FROM jsh_depot_head h WHERE h.number = #{number}")
    int countDepotHeadByNumber(@Param("number") String number);
}



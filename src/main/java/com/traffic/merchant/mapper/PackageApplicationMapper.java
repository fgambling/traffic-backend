package com.traffic.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.merchant.entity.PackageApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface PackageApplicationMapper extends BaseMapper<PackageApplication> {

    @Select("<script>" +
            "SELECT a.id, a.merchant_id AS merchantId, a.target_pkg AS targetPkg, " +
            "  a.remark, a.image_url AS imageUrl, a.status, a.admin_note AS adminNote, " +
            "  a.created_at AS createdAt, " +
            "  m.name AS merchantName, m.contact_phone AS contactPhone " +
            "FROM package_application a " +
            "JOIN merchant m ON m.id = a.merchant_id " +
            "<where>" +
            "  <if test='status != null'> AND a.status = #{status} </if>" +
            "</where>" +
            "ORDER BY a.created_at DESC" +
            "</script>")
    IPage<Map<String, Object>> pageApplications(Page<Map<String, Object>> page,
                                                @Param("status") Integer status);

    @Select("SELECT COUNT(1) FROM package_application WHERE status = 0")
    long countPending();
}

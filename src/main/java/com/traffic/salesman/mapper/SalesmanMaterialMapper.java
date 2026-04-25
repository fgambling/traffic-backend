package com.traffic.salesman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.salesman.entity.SalesmanMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SalesmanMaterialMapper extends BaseMapper<SalesmanMaterial> {

    @Select("SELECT * FROM salesman_material WHERE salesman_id = #{salesmanId} ORDER BY created_at DESC")
    List<SalesmanMaterial> findBySalesmanId(Integer salesmanId);
}

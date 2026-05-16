package com.traffic.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    @Select("SELECT * FROM admin_user WHERE username = #{username} LIMIT 1")
    AdminUser findByUsername(String username);
}

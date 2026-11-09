package com.example.docksystem_erp.dto.User;

import com.example.docksystem_erp.dto.Department.DepartmentResponseDto;
import com.example.docksystem_erp.dto.Role.RoleResponseDto;
import com.example.docksystem_erp.entity.User.User;
import lombok.Data;

@Data
public class UserResponseDto {
    private Long userNo;
    private String userName;
    private String userId;
    // 비밀번호(해시)는 응답에 절대 포함하지 않는다.
    private String userPhone;
    private String userWork;
    private Long userSalary;
    private DepartmentResponseDto department;
    private RoleResponseDto role;
    private Long storeNo;
    private String storeName;
    private String employmentType;

    public static UserResponseDto fromEntity(User user){
        UserResponseDto dto = new UserResponseDto();
        dto.setUserNo(user.getUserNo());
        dto.setUserName(user.getUserName());
        dto.setUserId(user.getUserId());
        dto.setUserPhone(user.getUserPhone());
        dto.setUserWork(user.getUserWork());
        dto.setUserSalary(user.getUserSalary());
        if (user.getDepartment() != null) {
            dto.setDepartment(DepartmentResponseDto.fromEntity(user.getDepartment()));
        }
        dto.setRole(RoleResponseDto.fromEntity(user.getRole()));
        dto.setEmploymentType(user.getEmploymentType());
        if (user.getStore() != null) {
            dto.setStoreNo(user.getStore().getStoreNo());
            dto.setStoreName(user.getStore().getStoreName());
        }
        return dto;
    }
}

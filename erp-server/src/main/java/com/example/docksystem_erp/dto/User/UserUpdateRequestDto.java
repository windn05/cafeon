package com.example.docksystem_erp.dto.User;

import lombok.Data;

@Data
public class UserUpdateRequestDto {
    private Long userNo;
    private String userName;
    private String userId;
    private String userPw;
    private String userPhone;
    private String userWork;
    private Long userSalary;
    private Long departmentNo;
    private Long roleNo;
    private Long storeNo;
    private String employmentType;
}

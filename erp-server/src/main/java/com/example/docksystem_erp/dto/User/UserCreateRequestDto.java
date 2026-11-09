package com.example.docksystem_erp.dto.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserCreateRequestDto {

    @NotBlank(message = "이름은 필수입니다.")
    private String userName;
    @NotBlank(message = "아이디는 필수입니다.")
    private String userId;
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String userPw;
    @NotBlank(message = "연락처는 필수입니다.")
    private String userPhone;
    @NotBlank(message = "업무는 필수입니다.")
    private String userWork;
    @NotBlank(message = "기본급은 필수입니다.")
    private Long userSalary;
    // 본사 직원만 부서가 있다. 매장 · 센트럴키친 직원은 소속 매장이 곧 소속이라 비워 둔다
    private Long departmentNo;
    @NotNull(message = "직급을 입력해주세요.")
    private Long roleNo;
    private Long storeNo;
    private String employmentType;
}

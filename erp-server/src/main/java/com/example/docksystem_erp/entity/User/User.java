package com.example.docksystem_erp.entity.User;

import com.example.docksystem_erp.dto.User.UserUpdateRequestDto;
import com.example.docksystem_erp.entity.Department.Department;
import com.example.docksystem_erp.entity.Role.Role;
import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userNo;
    @Column(nullable = false,length = 50)
    private String userName;
    @Column(nullable = false,unique = true,length = 50)
    private String userId;
    @Column(nullable = false,length = 100)
    private String userPw;
    @Column(nullable = false,length = 100)
    private String userPhone;
    @Column(nullable = false,length = 50)
    private String userWork;
    @Column(nullable = false)
    private Long userSalary;
    @ManyToOne
    @JoinColumn(name = "department_no")
    private Department department;
    @ManyToOne
    @JoinColumn(name = "role_no")
    private Role role;
    // 소속 매장 (본사 직원은 비워 둔다)
    @ManyToOne
    @JoinColumn(name = "store_no")
    private Store store;
    // 고용 형태: 정규직 · 계약직 · 파트타임 (파트타임은 급여를 시급으로 본다)
    @Column(length = 20)
    private String employmentType;

    // 비밀번호는 암호화가 필요하므로 UserService에서 따로 처리한다.
    public void updateUser(UserUpdateRequestDto requestDto){
        this.userName = requestDto.getUserName();
        this.userId = requestDto.getUserId();
        this.userPhone = requestDto.getUserPhone();
        this.userWork = requestDto.getUserWork();
        this.userSalary = requestDto.getUserSalary();
        this.employmentType = requestDto.getEmploymentType();
    }
}

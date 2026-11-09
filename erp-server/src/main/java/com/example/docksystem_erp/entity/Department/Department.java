package com.example.docksystem_erp.entity.Department;

import com.example.docksystem_erp.dto.Department.DepartmentUpdateRequestDto;
import com.example.docksystem_erp.entity.User.User;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
@Table(name = "departments")
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long departmentNo;
    @Column(nullable = false,unique = true,length = 50)
    private String departmentName;
    @OneToMany(mappedBy = "department")
    private List<User> users;

    public void updateDepartment(DepartmentUpdateRequestDto dto){
        this.departmentName = dto.getDepartmentName();
    }
}

package com.example.docksystem_erp.config;

import com.example.docksystem_erp.entity.Department.Department;
import com.example.docksystem_erp.entity.Role.Role;
import com.example.docksystem_erp.entity.User.User;
import com.example.docksystem_erp.repository.Department.DepartmentRepository;
import com.example.docksystem_erp.repository.Role.RoleRepository;
import com.example.docksystem_erp.repository.User.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 사용자가 한 명도 없으면 로그인할 수 없으므로, 최초 실행 시 관리자 계정을 하나 만든다.
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialAdminLoader implements CommandLineRunner {
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init-admin.user-id}")
    private String adminId;
    @Value("${app.init-admin.password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        Department department = departmentRepository.findAll().stream().findFirst().orElseGet(() -> {
            Department d = new Department();
            d.setDepartmentName("관리부");
            return departmentRepository.save(d);
        });
        Role role = roleRepository.findAll().stream().findFirst().orElseGet(() -> {
            Role r = new Role();
            r.setRoleName("관리자");
            return roleRepository.save(r);
        });

        User admin = new User();
        admin.setUserId(adminId);
        admin.setUserName("관리자");
        admin.setUserPw(passwordEncoder.encode(adminPassword));
        admin.setUserPhone("-");
        admin.setUserWork("시스템 관리");
        admin.setUserSalary(0L);
        admin.setDepartment(department);
        admin.setRole(role);
        userRepository.save(admin);
        log.info("초기 관리자 계정을 만들었습니다. 아이디: {} (비밀번호는 app.init-admin.password 설정값)", adminId);
    }
}

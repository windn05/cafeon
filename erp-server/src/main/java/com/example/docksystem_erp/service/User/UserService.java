package com.example.docksystem_erp.service.User;

import com.example.docksystem_erp.dto.User.UserCreateRequestDto;
import com.example.docksystem_erp.dto.User.UserResponseDto;
import com.example.docksystem_erp.dto.User.UserUpdateRequestDto;
import com.example.docksystem_erp.entity.Department.Department;
import com.example.docksystem_erp.entity.Role.Role;
import com.example.docksystem_erp.entity.User.User;
import com.example.docksystem_erp.repository.Department.DepartmentRepository;
import com.example.docksystem_erp.repository.Role.RoleRepository;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import com.example.docksystem_erp.repository.User.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor

public class UserService {
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final StoreRepository storeRepository;
    private final com.example.docksystem_erp.repository.Schedule.ShiftRepository shiftRepository;
    private final PasswordEncoder passwordEncoder;

    //새로운 사용자 정보 생성
    public User createUser(UserCreateRequestDto requestDto){
        User user = new User();
        user.setUserName(requestDto.getUserName());
        user.setUserId(requestDto.getUserId());
        user.setUserPw(passwordEncoder.encode(requestDto.getUserPw()));
        user.setUserPhone(requestDto.getUserPhone());
        user.setUserWork(requestDto.getUserWork());
        user.setUserSalary(requestDto.getUserSalary());
        user.setDepartment(findDepartment(requestDto.getStoreNo(), requestDto.getDepartmentNo()));
        Role role = roleRepository.findById(requestDto.getRoleNo())
                .orElseThrow(()->new EntityNotFoundException(("존재하지 않는 직급입니다."+requestDto.getRoleNo())));
        user.setRole(role);
        user.setEmploymentType(requestDto.getEmploymentType());
        user.setStore(findStore(requestDto.getStoreNo()));
        return userRepository.save(user);
    }
    //db상 모든 사용자 정보 조회
    public List<UserResponseDto> getAllUser(){
        return userRepository.findAll().stream()
                .map(UserResponseDto ::fromEntity)
                .collect(Collectors.toList());
    }
    //삭제
    public void deleteUser(Long userNo){
        if(!userRepository.existsById(userNo)){
            throw new EntityNotFoundException("해당 No의 사용자를 찾을 수 없습니다."+userNo);
        }
        if (shiftRepository.existsByUser_UserNo(userNo)) {
            throw new IllegalStateException("근무표 기록이 있는 직원은 삭제할 수 없습니다. 소속 매장을 비우면 근무표에서 빠집니다.");
        }
        userRepository.deleteById(userNo);
    }
    //업데이트
    public User updateUser(Long userNo, UserUpdateRequestDto requestDto){
        User existingUser = userRepository.findById(userNo)
                .orElseThrow(()->new EntityNotFoundException("해당 No의 사용자를 찾을 수 없습니다."));
        Department department = findDepartment(requestDto.getStoreNo(), requestDto.getDepartmentNo());
        Role role = roleRepository.findById(requestDto.getRoleNo())
                .orElseThrow(()->new EntityNotFoundException(("존재하지 않는 직급입니다."+requestDto.getRoleNo())));
        existingUser.updateUser(requestDto);
        // 새 비밀번호를 입력한 경우에만 암호화해서 바꾼다. 비어 있으면 기존 비밀번호 유지.
        if (requestDto.getUserPw() != null && !requestDto.getUserPw().isBlank()) {
            existingUser.setUserPw(passwordEncoder.encode(requestDto.getUserPw()));
        }
        existingUser.setDepartment(department);
        existingUser.setRole(role);
        existingUser.setStore(findStore(requestDto.getStoreNo()));
        return existingUser;
    }

    // 소속 매장이 있으면(매장 · 센트럴키친 직원) 부서는 두지 않고, 본사 직원은 부서가 꼭 있어야 한다
    private Department findDepartment(Long storeNo, Long departmentNo) {
        if (storeNo != null) return null;
        if (departmentNo == null) throw new IllegalArgumentException("본사 직원은 부서를 골라야 합니다.");
        return departmentRepository.findById(departmentNo)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 부서입니다." + departmentNo));
    }

    private Store findStore(Long storeNo) {
        if (storeNo == null) return null;
        return storeRepository.findById(storeNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 매장을 찾을 수 없습니다: " + storeNo));
    }
}

package com.example.docksystem_erp.service.Department;

import com.example.docksystem_erp.dto.Department.DepartmentCreateRequestDto;
import com.example.docksystem_erp.dto.Department.DepartmentResponseDto;
import com.example.docksystem_erp.dto.Department.DepartmentUpdateRequestDto;
import com.example.docksystem_erp.entity.Department.Department;
import com.example.docksystem_erp.repository.Department.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    @Autowired
    public DepartmentService(DepartmentRepository departmentRepository){
        this.departmentRepository = departmentRepository;
    }
    //새로운 부서 정보 생성
    public Department createDepartment(DepartmentCreateRequestDto requestDto){
        Department department = new Department();
        department.setDepartmentName(requestDto.getDepartmentName());
        return departmentRepository.save(department);
    }
    //db상 모든 창고 정보 조회
    public List<DepartmentResponseDto> getAllDepartment(){
        return departmentRepository.findAll().stream()
                .map(DepartmentResponseDto :: fromEntity)
                .collect(Collectors.toList());
    }
    //삭제
    public void deleteDepartment(Long departmentNo){
        if(!departmentRepository.existsById(departmentNo)){
            throw new EntityNotFoundException("해당 No의 부서를 찾을 수 없습니다."+departmentNo);
        }
        departmentRepository.deleteById(departmentNo);
    }
    //업데이트
    public Department updateDepartment(Long departmentNo, DepartmentUpdateRequestDto requestDto){
        Department existingDepartment = departmentRepository.findById(departmentNo)
                .orElseThrow(()-> new EntityNotFoundException("해당 No의 부서를 찾을 수 없습니다."+departmentNo));
        existingDepartment.updateDepartment(requestDto);
        return existingDepartment;
    }
}

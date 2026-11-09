package com.example.docksystem_erp.controller.User;

import com.example.docksystem_erp.dto.User.UserCreateRequestDto;
import com.example.docksystem_erp.dto.User.UserResponseDto;
import com.example.docksystem_erp.dto.User.UserUpdateRequestDto;
import com.example.docksystem_erp.entity.User.User;
import com.example.docksystem_erp.service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/users")
public class UserController {
    private final UserService userService;

    //All 사용자 정보 조회
    @GetMapping
    public ResponseEntity<List<UserResponseDto>> getAllUser(){
        List<UserResponseDto> users = userService.getAllUser();
        return ResponseEntity.ok(users);
    }
    //Create
    @PostMapping
    public UserResponseDto createUser(@RequestBody UserCreateRequestDto requestDto){
        return UserResponseDto.fromEntity(userService.createUser(requestDto));
    }
    //Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id")Long userNo){
        userService.deleteUser(userNo);
        return ResponseEntity.noContent().build();
    }
    //Update
    @PutMapping("/{userNo}")
    public UserResponseDto updateUser(@PathVariable("userNo")Long userNo,
                                      @RequestBody UserUpdateRequestDto requestDto){
        User updateUser = userService.updateUser(userNo,requestDto);
        return UserResponseDto.fromEntity(updateUser);
    }
}

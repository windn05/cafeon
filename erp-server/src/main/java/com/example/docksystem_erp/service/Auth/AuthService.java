package com.example.docksystem_erp.service.Auth;

import com.example.docksystem_erp.JwtSecurity.JwtProvider;
import com.example.docksystem_erp.dto.Auth.LoginRequestDto;
import com.example.docksystem_erp.dto.Auth.LoginResponseDto;
import com.example.docksystem_erp.repository.User.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    // 아이디·비밀번호가 맞으면 토큰을 발급하고, 틀리면 빈 값을 돌려준다.
    public Optional<LoginResponseDto> login(LoginRequestDto requestDto) {
        if (requestDto.getUserId() == null || requestDto.getPassword() == null) {
            return Optional.empty();
        }
        return userRepository.findByUserId(requestDto.getUserId())
                .filter(user -> passwordEncoder.matches(requestDto.getPassword(), user.getUserPw()))
                .map(user -> new LoginResponseDto(
                        jwtProvider.createToken(user.getUserId()),
                        user.getUserId(),
                        user.getUserName()));
    }
}

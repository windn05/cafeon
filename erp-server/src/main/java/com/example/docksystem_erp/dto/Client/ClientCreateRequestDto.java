package com.example.docksystem_erp.dto.Client;

import com.example.docksystem_erp.entity.Client.ClientType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClientCreateRequestDto {
    @NotBlank(message = "거래처의 이름은 필수입니다.")
    private String clientName;
    @Enumerated(EnumType.STRING)
    @NotBlank(message = "거래처의 타입은 필수입니다.")
    private ClientType type;
    @NotBlank(message = "거래처의 사업자등록번호는 필수입니다.")
    @Size(min = 12,max = 12,message = "사업자 등록 번호는 12자리여야 합니다.")
    private String clientBrn;
    @NotBlank(message = "거래처의 대표명은 필수입니다.")
    private String clientCeo;
    @NotBlank(message = "거래처의 담당자명은 필수입니다.")
    private String clientManager;
    @NotBlank(message = "거래처의 전화번호는 필수입니다.")
    private String clientPhone;
    private Integer leadTimeDays;
}

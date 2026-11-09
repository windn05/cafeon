package com.example.docksystem_erp.dto.Client;

import com.example.docksystem_erp.entity.Client.ClientType;
import lombok.Data;

@Data
public class ClientUpdateRequestDto {

    private Long clientNo;
    private String clientName;
    private ClientType type;
    private String clientBrn;
    private String clientCeo;
    private String clientManager;
    private String clientPhone;
    private Integer leadTimeDays;
}

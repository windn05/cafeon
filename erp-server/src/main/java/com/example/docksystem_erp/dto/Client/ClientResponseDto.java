package com.example.docksystem_erp.dto.Client;

import com.example.docksystem_erp.entity.Client.Client;
import com.example.docksystem_erp.entity.Client.ClientType;
import lombok.Data;

@Data
public class ClientResponseDto {
    private Long clientNo;
    private String clientName;
    private ClientType type;
    private String typeLabel;
    private String clientBrn;
    private String clientCeo;
    private String clientManager;
    private String clientPhone;
    private Integer leadTimeDays;
    // 이 업체를 주 공급업체로 둔 식자재 수
    private long materialCount;

    public static ClientResponseDto fromEntity(Client client) {
        ClientResponseDto dto = new ClientResponseDto();
        dto.setClientNo(client.getClientNo());
        dto.setClientName(client.getClientName());
        dto.setType(client.getType());
        dto.setTypeLabel(client.getType().getLabel());
        dto.setClientBrn(client.getClientBrn());
        dto.setClientCeo(client.getClientCeo());
        dto.setClientManager(client.getClientManager());
        dto.setClientPhone(client.getClientPhone());
        dto.setLeadTimeDays(client.getLeadTimeDays());
        return dto;
    }
}

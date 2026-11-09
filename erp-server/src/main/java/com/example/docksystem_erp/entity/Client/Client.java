package com.example.docksystem_erp.entity.Client;

import com.example.docksystem_erp.util.BizNumber;
import com.example.docksystem_erp.dto.Client.ClientUpdateRequestDto;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "clients")
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long clientNo;
    @Column(nullable = false,length = 100)
    private String clientName;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClientType type;
    @Column(nullable = false,unique = true,length = 12)
    private String clientBrn;
    @Column(nullable = false,length = 50)
    private String clientCeo;
    @Column(nullable = false,length = 50)
    private String clientManager;
    @Column(nullable = false,length = 50)
    private String clientPhone;
    // 발주 후 납품까지 걸리는 날 수 (주간 발주 계획에 사용)
    private Integer leadTimeDays;

    public void updateClient(ClientUpdateRequestDto requestDto){
        this.leadTimeDays = requestDto.getLeadTimeDays();
        this.clientName = requestDto.getClientName();
        this.type = requestDto.getType();
        this.clientBrn = BizNumber.digitsOnly(requestDto.getClientBrn());
        this.clientCeo = requestDto.getClientCeo();
        this.clientManager = requestDto.getClientManager();
        this.clientPhone = requestDto.getClientPhone();

    }
}

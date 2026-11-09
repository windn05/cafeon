package com.example.docksystem_erp.entity.Client;

import com.fasterxml.jackson.annotation.JsonValue;

// 공급업체 유형
public enum ClientType {
    COFFEE("원두·커피"),
    DAIRY("유제품"),
    BAKERY("베이커리 원료"),
    PRODUCE("농산물·과일"),
    SAUCE("시럽·소스"),
    PACKAGING("포장재·소모품"),
    EQUIPMENT("설비·유지보수"),
    LOGISTICS("물류·배송");

    private final String label;

    ClientType(String label){
        this.label=label;
    }

    @JsonValue // 응답·요청 모두 한글 라벨로 주고받는다
    public String getLabel(){
        return label;
    }
}

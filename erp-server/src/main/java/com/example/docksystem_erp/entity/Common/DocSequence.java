package com.example.docksystem_erp.entity.Common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// 문서 번호 순번: 접두어(종류-날짜, 예: RQ-261111-)마다 마지막으로 쓴 순번
@Entity
@Getter
@Setter
@Table(name = "doc_sequences")
public class DocSequence {
    @Id
    @Column(length = 20)
    private String prefix;
    @Column(nullable = false)
    private long lastNo;
}

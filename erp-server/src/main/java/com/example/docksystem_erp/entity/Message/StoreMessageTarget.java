package com.example.docksystem_erp.entity.Message;

import com.example.docksystem_erp.entity.Store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 메시지를 받는 매장 1곳. 매장 POS가 받아 갔는지 · 읽었는지 · 답했는지를 남긴다.
// targetNo는 매장별 받은 편지함의 순서 번호로도 쓴다 (POS는 마지막으로 받은 번호 다음부터 가져감).
@Entity
@Getter
@Setter
@Table(name = "store_message_targets", indexes = @Index(name = "idx_msg_target_store", columnList = "store_no,target_no"))
public class StoreMessageTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "target_no")
    private Long targetNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_no")
    private StoreMessage message;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_no")
    private Store store;
    // POS가 받아 간 시각
    private LocalDateTime deliveredAt;
    // 매장에서 확인 누른 시각
    private LocalDateTime readAt;
    @Column(length = 500)
    private String reply;
    private LocalDateTime repliedAt;
}

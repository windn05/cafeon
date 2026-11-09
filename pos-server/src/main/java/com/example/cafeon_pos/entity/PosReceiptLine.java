package com.example.cafeon_pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Entity
@Getter
@Setter
@Table(name = "pos_receipt_lines")
public class PosReceiptLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private PosReceipt receipt;
    @Column(nullable = false, length = 30)
    private String menuCode;
    @Column(nullable = false, length = 50)
    private String menuName;
    private long unitPrice;
    private int quantity;
    private long amount;
}

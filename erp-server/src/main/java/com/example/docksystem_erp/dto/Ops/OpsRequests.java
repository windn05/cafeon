package com.example.docksystem_erp.dto.Ops;

import java.time.LocalDate;
import java.util.List;

// 매장 운영(판매 · 마감 · 재고) 요청 형식
public final class OpsRequests {
    private OpsRequests() {}

    // POS 영수증 1장
    public record Receipt(Long storeNo, LocalDate saleDate, String paymentMethod, String channel, List<ReceiptItem> items) {}

    public record ReceiptItem(Long menuNo, Integer quantity) {}

    // 일 마감
    public record Closing(Long storeNo, LocalDate date, Long cashCounted, String note) {}

    // 폐기 등록
    public record Waste(Long storeNo, LocalDate date, Long materialNo, Double quantity, String reason, String note) {}

    // 재고 실사
    public record StocktakeReq(Long storeNo, LocalDate date, String note, List<CountLine> lines) {}

    public record CountLine(Long materialNo, Double countedQty) {}

    // 발주서 (공급업체 1곳)
    public record PoCreate(Long storeNo, Long clientNo, LocalDate expectedDate, String note, List<PoLine> lines) {}

    public record PoLine(Long materialNo, Integer packs) {}

    // 입고 검수: 줄마다 실제로 받은 포장 수
    public record PoReceive(LocalDate receivedDate, String note, List<ReceiveLine> lines) {}

    public record ReceiveLine(Long lineNo, Integer receivedPacks) {}

    // 근무 1건 (직원 1명 · 하루)
    public record ShiftReq(Long storeNo, Long userNo, LocalDate workDate, java.time.LocalTime startTime,
                           java.time.LocalTime endTime, Integer breakMinutes, String position, String note) {}

    // 지난주 근무표 복사
    public record CopyWeek(Long storeNo, LocalDate fromWeek, LocalDate toWeek) {}
}

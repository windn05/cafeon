package com.example.docksystem_erp.service.Sales;

import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Closing.DailyClosingRepository;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.example.docksystem_erp.repository.Monthly.MonthlyClosingRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

// 판매·재고 기록 전에 공통으로 확인하는 것들
@Component
@RequiredArgsConstructor
public class OpsGuard {
    public static final Set<String> SHOP_TYPES = Set.of("직영점", "가맹점");
    // 일 마감 때 기준으로 쓰는 현금 준비금
    public static final long CASH_FLOAT = 200_000;

    private final StoreRepository storeRepository;
    private final DailyClosingRepository closingRepository;
    private final MonthlyClosingRepository monthlyRepository;

    public Store store(Long storeNo) {
        if (storeNo == null) throw new IllegalArgumentException("매장을 선택해 주세요.");
        return storeRepository.findById(storeNo)
                .orElseThrow(() -> new EntityNotFoundException("해당 매장을 찾을 수 없습니다: " + storeNo));
    }

    public Store shop(Long storeNo) {
        Store store = store(storeNo);
        if (!SHOP_TYPES.contains(store.getStoreType())) {
            throw new IllegalArgumentException(store.getStoreName() + "은(는) 판매 매장이 아닙니다 (" + store.getStoreType() + ").");
        }
        return store;
    }

    public boolean isClosed(Long storeNo, LocalDate date) {
        return closingRepository.existsByStore_StoreNoAndClosingDate(storeNo, date);
    }

    public boolean isMonthClosed(Long storeNo, LocalDate date) {
        return monthlyRepository.existsByStore_StoreNoAndYearMonth(storeNo, YearMonth.from(date).toString());
    }

    // 월 마감한 달의 기록(일 마감 취소 · 실사 · 입고 등)은 바꿀 수 없다
    public void requireOpenMonth(Store store, LocalDate date) {
        if (isMonthClosed(store.getStoreNo(), date)) {
            throw new IllegalStateException(store.getStoreName() + " " + YearMonth.from(date) + "은(는) 월 마감했습니다. 월 마감을 취소한 뒤 수정하세요.");
        }
    }

    // 마감한 날은 판매·폐기를 고칠 수 없다
    public void requireOpenDay(Store store, LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("미래 날짜에는 기록할 수 없습니다.");
        }
        requireOpenMonth(store, date);
        if (isClosed(store.getStoreNo(), date)) {
            throw new IllegalStateException(store.getStoreName() + " " + date + "은(는) 이미 마감했습니다. 마감을 취소한 뒤 수정하세요.");
        }
    }
}

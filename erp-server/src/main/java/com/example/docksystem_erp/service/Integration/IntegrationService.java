package com.example.docksystem_erp.service.Integration;

import com.example.docksystem_erp.entity.Integration.IngestLog;
import com.example.docksystem_erp.entity.Store.Store;
import com.example.docksystem_erp.repository.Store.StoreRepository;
import com.example.docksystem_erp.service.Sales.OpsGuard;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

// 본사: 매장 데이터 수집 현황 · 연동 키 관리
@Service
@Transactional
@RequiredArgsConstructor
public class IntegrationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> TYPES = List.of(IngestLog.SALES, IngestLog.CLOSING, IngestLog.WASTE,
            IngestLog.STOCKTAKE, IngestLog.ORDER, IngestLog.DELIVERY);

    private final JdbcTemplate jdbc;
    private final StoreRepository storeRepository;

    public static String newKey() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder("sk_");
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    // 연동 키가 없는 매장에 키 발급 (가상 데이터 생성 · 수집 현황 조회 때 호출)
    public void ensureKeys() {
        for (Store s : storeRepository.findAll()) {
            if (s.getIntegrationKey() == null) s.setIntegrationKey(newKey());
        }
    }

    public Map<String, Object> reissue(Long storeNo) {
        Store s = storeRepository.findById(storeNo).orElseThrow(() -> new EntityNotFoundException("해당 매장을 찾을 수 없습니다: " + storeNo));
        s.setIntegrationKey(newKey());
        return Map.of("storeNo", storeNo, "integrationKey", s.getIntegrationKey());
    }

    // 매장별 데이터 종류마다 마지막으로 받은 시각 · 최근 실패 · 어제 마감 수신 여부
    public List<Map<String, Object>> status() {
        ensureKeys();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        Map<Long, Map<String, Object>> last = new HashMap<>();
        jdbc.queryForList("select store_no, data_type, max(received_at) as last_at from ingest_logs where status = '성공' group by store_no, data_type")
                .forEach(r -> last.computeIfAbsent(((Number) r.get("store_no")).longValue(), k -> new HashMap<>())
                        .put((String) r.get("data_type"), r.get("last_at")));
        Map<Long, Long> fails = new HashMap<>();
        jdbc.queryForList("select store_no, count(*) as cnt from ingest_logs where status = '실패' and received_at >= ? group by store_no",
                        Timestamp.valueOf(today.minusDays(7).atStartOfDay()))
                .forEach(r -> fails.put(((Number) r.get("store_no")).longValue(), ((Number) r.get("cnt")).longValue()));
        Map<Long, Long> todayCount = new HashMap<>();
        jdbc.queryForList("select store_no, sum(record_count) as cnt from ingest_logs where status = '성공' and received_at >= ? group by store_no",
                        Timestamp.valueOf(today.atStartOfDay()))
                .forEach(r -> todayCount.put(((Number) r.get("store_no")).longValue(), ((Number) r.get("cnt")).longValue()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Store s : storeRepository.findAll().stream().sorted(Comparator.comparing(Store::getStoreCode)).toList()) {
            boolean shop = OpsGuard.SHOP_TYPES.contains(s.getStoreType());
            Map<String, Object> l = last.getOrDefault(s.getStoreNo(), Map.of());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("storeNo", s.getStoreNo());
            m.put("storeCode", s.getStoreCode());
            m.put("storeName", s.getStoreName());
            m.put("storeType", s.getStoreType());
            m.put("status", s.getStatus());
            m.put("integrationKey", s.getIntegrationKey());
            Map<String, Object> lastAt = new LinkedHashMap<>();
            TYPES.forEach(t -> lastAt.put(t, l.get(t)));
            m.put("lastReceived", lastAt);
            m.put("todayRecords", todayCount.getOrDefault(s.getStoreNo(), 0L));
            m.put("failures7d", fails.getOrDefault(s.getStoreNo(), 0L));
            // 영업 중인 판매 매장인데 어제 마감 데이터가 안 들어왔으면 누락
            boolean expectClosing = shop && "영업중".equals(s.getStatus());
            Long gotClosing = jdbc.queryForObject("select count(*) from daily_closings where store_no = ? and closing_date = ?",
                    Long.class, s.getStoreNo(), Date.valueOf(yesterday));
            m.put("yesterdayClosingReceived", !expectClosing || (gotClosing != null && gotClosing > 0));
            Object lastSales = l.get(IngestLog.SALES);
            // MySQL 드라이버는 DATETIME을 LocalDateTime으로 줄 때가 있어 둘 다 처리
            java.time.LocalDateTime lastSalesAt = lastSales instanceof Timestamp ts ? ts.toLocalDateTime()
                    : lastSales instanceof java.time.LocalDateTime ldt ? ldt : null;
            m.put("salesToday", lastSalesAt != null && lastSalesAt.toLocalDate().equals(today));
            m.put("expectsData", expectClosing);
            rows.add(m);
        }
        return rows;
    }

    // 최근 수집 기록
    @Transactional(readOnly = true)
    public List<Map<String, Object>> logs(Long storeNo, String status, int limit) {
        StringBuilder sql = new StringBuilder("select l.log_no as logNo, l.store_no as storeNo, s.store_name as storeName, l.data_type as dataType, " +
                "l.business_date as businessDate, l.received_at as receivedAt, l.record_count as recordCount, l.status, l.message " +
                "from ingest_logs l join stores s on s.store_no = l.store_no where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (storeNo != null) {
            sql.append(" and l.store_no = ?");
            args.add(storeNo);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and l.status = ?");
            args.add(status);
        }
        sql.append(" order by l.received_at desc, l.log_no desc limit ").append(Math.max(1, Math.min(limit, 500)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}

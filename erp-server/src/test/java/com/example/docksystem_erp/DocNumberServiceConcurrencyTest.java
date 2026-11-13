package com.example.docksystem_erp;

import com.example.docksystem_erp.service.Common.DocNumberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

// 그날 첫 문서 번호를 여러 요청이 동시에 받을 때 데드락 없이 서로 다른 번호가 나오는지 (개발 DB 사용, 끝나면 시험 행 삭제)
@SpringBootTest
class DocNumberServiceConcurrencyTest {
    private static final String PREFIX = "TEST-CONC-";

    @Autowired
    DocNumberService docNumbers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager tm;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from doc_sequences where prefix = ?", PREFIX);
    }

    @Test
    void 첫_번호를_동시에_받아도_겹치지_않는다() throws Exception {
        jdbc.update("delete from doc_sequences where prefix = ?", PREFIX);
        int n = 8;
        TransactionTemplate tx = new TransactionTemplate(tm);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                start.await();
                // 문서 저장 트랜잭션 안에서 번호를 받고, 저장에 시간이 걸린다고 치고 잠깐 쥐고 있는다
                return tx.execute(s -> {
                    long no = docNumbers.next(PREFIX, () -> 0L);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return no;
                });
            }));
        }
        start.countDown();
        Set<Long> numbers = new java.util.TreeSet<>();
        for (Future<Long> f : results) numbers.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(numbers).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }
}

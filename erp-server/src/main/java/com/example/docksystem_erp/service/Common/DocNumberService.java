package com.example.docksystem_erp.service.Common;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.LongSupplier;

// 문서 번호 순번 발급. 순번 행을 잠그고 올리므로, 여러 매장이 같은 순간에 요청해도 번호가 겹치지 않는다
// (잠금은 문서를 저장하는 트랜잭션이 끝날 때 풀린다)
@Service
@RequiredArgsConstructor
public class DocNumberService {
    private final JdbcTemplate jdbc;

    // existingMax: 순번 행이 아직 없을 때, 이미 있는 문서의 가장 큰 순번 (이어서 매기려고)
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String prefix, LongSupplier existingMax) {
        if (jdbc.update("update doc_sequences set last_no = last_no + 1 where prefix = ?", prefix) == 0) {
            try {
                jdbc.update("insert into doc_sequences (prefix, last_no) values (?, ?)", prefix, existingMax.getAsLong() + 1);
            } catch (DuplicateKeyException e) {
                // 다른 요청이 방금 행을 만들었다: 그 뒤 번호로
                jdbc.update("update doc_sequences set last_no = last_no + 1 where prefix = ?", prefix);
            }
        }
        return jdbc.queryForObject("select last_no from doc_sequences where prefix = ?", Long.class, prefix);
    }
}

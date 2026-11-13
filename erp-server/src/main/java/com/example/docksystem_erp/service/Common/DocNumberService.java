package com.example.docksystem_erp.service.Common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.LongSupplier;

// 문서 번호 순번 발급. 순번 행을 잠그고 올리므로, 여러 요청이 같은 순간에 와도 번호가 겹치지 않는다
// (잠금은 문서를 저장하는 트랜잭션이 끝날 때 풀린다)
//
// 그날 첫 번호(순번 행이 없을 때)는 행을 먼저 '별도 트랜잭션'으로 만들어 둔다.
// 없는 행을 UPDATE하면 MySQL이 빈 구간(gap)을 잠그는데, 두 요청이 같은 구간을 잠근 채 서로 INSERT하려 하면
// 데드락이 난다 (예: 자동화가 발주서 여러 장을 동시에 만들 때). 행이 있으면 행 잠금만 걸려 차례로 처리된다.
@Service
public class DocNumberService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate separate;

    public DocNumberService(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.separate = new TransactionTemplate(tm);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // existingMax: 순번 행이 아직 없을 때, 이미 있는 문서의 가장 큰 순번 (이어서 매기려고)
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String prefix, LongSupplier existingMax) {
        // 잠그지 않는 조회로 행이 있는지만 본다 (없는 행을 잠그는 UPDATE를 피하려고)
        Integer rows = jdbc.queryForObject("select count(*) from doc_sequences where prefix = ?", Integer.class, prefix);
        if (rows == null || rows == 0) {
            long start = existingMax.getAsLong();
            // 동시에 만들어도 하나만 들어가고 나머지는 무시된다. 바로 커밋되어 다른 요청도 곧 행을 본다
            separate.executeWithoutResult(s ->
                    jdbc.update("insert ignore into doc_sequences (prefix, last_no) values (?, ?)", prefix, start));
        }
        jdbc.update("update doc_sequences set last_no = last_no + 1 where prefix = ?", prefix);
        return jdbc.queryForObject("select last_no from doc_sequences where prefix = ?", Long.class, prefix);
    }
}

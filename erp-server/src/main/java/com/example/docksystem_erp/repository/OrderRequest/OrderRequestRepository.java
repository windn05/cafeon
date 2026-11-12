package com.example.docksystem_erp.repository.OrderRequest;

import com.example.docksystem_erp.entity.OrderRequest.OrderRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OrderRequestRepository extends JpaRepository<OrderRequest, Long> {
    List<OrderRequest> findByRequestDateBetweenOrderByRequestDateDescRequestNoDesc(LocalDate from, LocalDate to);
    List<OrderRequest> findByStatusOrderByRequestDateAscRequestNoAsc(String status);
    java.util.Optional<OrderRequest> findTopByRequestNumberStartingWithOrderByRequestNumberDesc(String prefix);
}

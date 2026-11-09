package com.example.docksystem_erp.repository.Inventory;

import com.example.docksystem_erp.entity.Inventory.Stocktake;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {
    List<Stocktake> findByStore_StoreNoOrderByCountDateDescStocktakeNoDesc(Long storeNo);
}

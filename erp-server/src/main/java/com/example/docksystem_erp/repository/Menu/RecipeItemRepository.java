package com.example.docksystem_erp.repository.Menu;

import com.example.docksystem_erp.entity.Menu.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {
    List<RecipeItem> findByMaterial_MaterialNo(Long materialNo);
    long countByMaterial_MaterialNo(Long materialNo);
}

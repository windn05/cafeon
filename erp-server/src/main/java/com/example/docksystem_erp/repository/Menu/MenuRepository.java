package com.example.docksystem_erp.repository.Menu;

import com.example.docksystem_erp.entity.Menu.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    boolean existsByMenuCode(String menuCode);
    java.util.Optional<Menu> findByMenuCode(String menuCode);
}

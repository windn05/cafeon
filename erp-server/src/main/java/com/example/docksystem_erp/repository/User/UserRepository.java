package com.example.docksystem_erp.repository.User;

import com.example.docksystem_erp.entity.User.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByUserId(String userId);
    long countByStore_StoreNo(Long storeNo);
}

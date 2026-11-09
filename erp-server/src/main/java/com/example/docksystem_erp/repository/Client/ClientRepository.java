package com.example.docksystem_erp.repository.Client;

import com.example.docksystem_erp.entity.Client.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client,Long> {
}

package com.example.docksystem_erp.controller.Client;

import com.example.docksystem_erp.dto.Client.ClientCreateRequestDto;
import com.example.docksystem_erp.dto.Client.ClientResponseDto;
import com.example.docksystem_erp.dto.Client.ClientUpdateRequestDto;
import com.example.docksystem_erp.entity.Client.Client;
import com.example.docksystem_erp.service.Client.ClientService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp/v1/clients")
public class ClientController {
    private final ClientService clientService;

    //거래처 전체 조회
    @GetMapping
    public ResponseEntity<List<ClientResponseDto>> getAllClients(){
        List<ClientResponseDto> clients = clientService.getAllClients();
        return ResponseEntity.ok(clients);
    }

    // 거래처 생성 Create
    @PostMapping
    public Client createClient(@RequestBody ClientCreateRequestDto requestDto){
        return clientService.createClient(requestDto);
    }

    //거래처 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClient(@PathVariable("id")Long clientNo){
        try{
            clientService.deleteClient(clientNo);
            return ResponseEntity.ok(Map.of("message","거래처를 삭제하였습니다."));
        }catch (EntityNotFoundException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message",e.getMessage()));
        }
    }

    //거래처수정
    @PutMapping("/{clientNo}")
    public ResponseEntity<?> updateClient(
            @PathVariable("clientNo") Long clientNo,
            @RequestBody ClientUpdateRequestDto requestDto){

        try{
            clientService.updateClient(clientNo, requestDto);
            return ResponseEntity.ok(Map.of("message","거래처를 수정하였습니다."));
        }catch (EntityNotFoundException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message",e.getMessage()));
        }


    }
}

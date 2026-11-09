package com.example.docksystem_erp.service.Client;

import com.example.docksystem_erp.util.BizNumber;
import com.example.docksystem_erp.dto.Client.ClientCreateRequestDto;
import com.example.docksystem_erp.dto.Client.ClientResponseDto;
import com.example.docksystem_erp.dto.Client.ClientUpdateRequestDto;
import com.example.docksystem_erp.entity.Client.Client;
import com.example.docksystem_erp.repository.Client.ClientRepository;
import com.example.docksystem_erp.repository.Material.MaterialRepository;
import com.example.docksystem_erp.repository.PurchaseOrder.PurchaseOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 공급업체 (원두·유제품·포장재 등 식자재 공급처)
@Service
@Transactional
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;
    private final MaterialRepository materialRepository;
    private final PurchaseOrderRepository poRepository;

    public Client createClient (ClientCreateRequestDto requestDto){
        Client client = new Client();
        client.setClientName(requestDto.getClientName());
        client.setType(requestDto.getType());
        client.setClientBrn(BizNumber.digitsOnly(requestDto.getClientBrn()));
        client.setClientCeo(requestDto.getClientCeo());
        client.setClientManager(requestDto.getClientManager());
        client.setClientPhone(requestDto.getClientPhone());
        client.setLeadTimeDays(requestDto.getLeadTimeDays());
        return clientRepository.save(client);
    }

    @Transactional(readOnly = true)
    public List<ClientResponseDto> getAllClients(){
        return clientRepository.findAll().stream().map(client -> {
            ClientResponseDto dto = ClientResponseDto.fromEntity(client);
            dto.setMaterialCount(materialRepository.countByClient_ClientNo(client.getClientNo()));
            return dto;
        }).toList();
    }

    public void deleteClient(Long clientNo){
        if(!clientRepository.existsById(clientNo)){
            throw new EntityNotFoundException("해당 공급업체를 찾을 수 없습니다: " + clientNo);
        }
        long materials = materialRepository.countByClient_ClientNo(clientNo);
        if (materials > 0) {
            throw new IllegalStateException("이 업체를 주 공급업체로 둔 식자재가 " + materials + "개 있어 삭제할 수 없습니다.");
        }
        if (poRepository.existsByClient_ClientNo(clientNo)) {
            throw new IllegalStateException("발주 기록이 있는 공급업체는 삭제할 수 없습니다.");
        }
        clientRepository.deleteById(clientNo);
    }

    public void updateClient (Long clientNo, ClientUpdateRequestDto requestDto){
        Client existingClient = clientRepository.findById(clientNo)
                .orElseThrow(()-> new EntityNotFoundException("해당 공급업체를 찾을 수 없습니다: " + clientNo));
        existingClient.updateClient(requestDto);
    }
}

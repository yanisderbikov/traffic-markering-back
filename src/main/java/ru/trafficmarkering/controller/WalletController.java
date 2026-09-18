package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.dto.wallet.WalletDTO;
import ru.trafficmarkering.service.wallet.WalletService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Кошелёк заказчика: свободные деньги и история операций")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Мой кошелёк",
            description = "Свободный остаток, сумма бюджетов объявлений и сколько уже начислено криаторам; суммы в копейках",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<WalletDTO> myWallet() {
        return ResponseEntity.ok(walletService.myWallet());
    }

    @Operation(summary = "Операции по моему кошельку",
            description = "Короткие строки: что за операция, откуда → куда, сумма и статус; подробности — по id",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/operations")
    public ResponseEntity<List<OperationRowDTO>> myWalletOperations() {
        return ResponseEntity.ok(walletService.myOperations());
    }

    @Operation(summary = "Операция по моему кошельку целиком",
            description = "Проводка с объявлением, комментарием, кто провёл и остатком после; для пополнения и вывода — "
                    + "ещё перевод: адрес TRON, номер транзакции, скриншоты финансиста",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/operations/{id}")
    public ResponseEntity<OperationDetailDTO> myWalletOperation(@PathVariable("id") Long id) {
        return ResponseEntity.ok(walletService.myOperation(id));
    }

    @Operation(summary = "Подтвердить пополнение или вывод",
            description = "Только для операции в статусе SENT: заказчик сверил перевод финансиста и подтверждает его",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/operations/{id}/confirm")
    public ResponseEntity<OperationDetailDTO> confirmWalletOperation(@PathVariable("id") Long id) {
        return ResponseEntity.ok(walletService.confirm(id));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }
}

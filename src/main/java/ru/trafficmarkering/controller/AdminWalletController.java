package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.trafficmarkering.dto.wallet.AdminWalletAdjustmentRequestDTO;
import ru.trafficmarkering.dto.wallet.AdminWalletDTO;
import ru.trafficmarkering.dto.wallet.WalletTransactionDTO;
import ru.trafficmarkering.service.wallet.AdminWalletService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/wallets")
@RequiredArgsConstructor
@Tag(name = "Admin wallets", description = "Ручное управление кошельками менеджером")
public class AdminWalletController {

    private final AdminWalletService adminWalletService;

    @Operation(summary = "Все кошельки заказчиков и криаторов",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<List<AdminWalletDTO>> getWallets() {
        return ResponseEntity.ok(adminWalletService.getWallets());
    }

    @Operation(summary = "История кошелька пользователя",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<WalletTransactionDTO>> getTransactions(@PathVariable Long userId) {
        return ResponseEntity.ok(adminWalletService.getTransactions(userId));
    }

    @Operation(summary = "Ручная корректировка баланса",
            description = "Положительная сумма пополняет баланс, отрицательная — списывает. Итоговый баланс не может быть отрицательным.",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{userId}/adjustments")
    public ResponseEntity<AdminWalletDTO> adjust(
            @PathVariable Long userId,
            @Valid @RequestBody AdminWalletAdjustmentRequestDTO request) {
        return ResponseEntity.ok(adminWalletService.adjust(userId, request));
    }
}

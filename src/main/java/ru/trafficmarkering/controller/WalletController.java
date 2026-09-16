package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.trafficmarkering.dto.wallet.WalletDTO;
import ru.trafficmarkering.dto.wallet.WalletTransactionDTO;
import ru.trafficmarkering.service.wallet.WalletService;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Кошелёк заказчика или криатора")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Мой кошелёк", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<WalletDTO> getCurrent() {
        return ResponseEntity.ok(walletService.getCurrent());
    }

    @Operation(summary = "История моего кошелька", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/transactions")
    public ResponseEntity<List<WalletTransactionDTO>> getTransactions() {
        return ResponseEntity.ok(walletService.getCurrentTransactions());
    }
}

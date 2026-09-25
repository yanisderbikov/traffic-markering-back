package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.dto.wallet.TopUpCreateRequestDTO;
import ru.trafficmarkering.dto.wallet.TopUpPaidRequestDTO;
import ru.trafficmarkering.dto.wallet.WalletDTO;
import ru.trafficmarkering.service.transfer.TransferService;
import ru.trafficmarkering.service.wallet.WalletService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Кошелёк заказчика: свободные деньги и история операций")
public class WalletController {

    private final WalletService walletService;
    private final TransferService transferService;

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
                    + "ещё перевод: адрес TRON, номер транзакции, скриншоты и файлы",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/operations/{id}")
    public ResponseEntity<OperationDetailDTO> myWalletOperation(@PathVariable("id") Long id) {
        return ResponseEntity.ok(walletService.myOperation(id));
    }

    @Operation(summary = "Подтвердить вывод",
            description = "Только для вывода в статусе SENT: заказчик проверил поступление USDT и подтверждает его",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/operations/{id}/confirm")
    public ResponseEntity<OperationDetailDTO> confirmWalletOperation(@PathVariable("id") Long id) {
        return ResponseEntity.ok(walletService.confirm(id));
    }

    @Operation(summary = "Заявка на пополнение",
            description = "Заказчик сам заводит пополнение на сумму в копейках и получает адрес TRON платформы, "
                    + "куда перевести USDT. Баланс не меняется, пока финансист не подтвердит поступление. "
                    + "503 — адрес для пополнения не настроен",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/top-ups")
    public ResponseEntity<OperationDetailDTO> requestTopUp(@Valid @RequestBody TopUpCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.requestTopUp(request));
    }

    @Operation(summary = "Отметить заявку на пополнение оплаченной",
            description = "Только из PENDING: скриншоты или файлы перевода обязательны, номер транзакции — по желанию. "
                    + "Заявка переходит в SENT и ждёт проверки финансиста",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/top-ups/{id}/paid")
    public ResponseEntity<OperationDetailDTO> markTopUpPaid(@PathVariable("id") Long id,
                                                            @Valid @RequestBody TopUpPaidRequestDTO request) {
        return ResponseEntity.ok(transferService.markTopUpPaid(id, request));
    }

    @Operation(summary = "Отменить заявку на пополнение",
            description = "Только пока заявка не оплачена (PENDING)",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/top-ups/{id}/cancel")
    public ResponseEntity<OperationDetailDTO> cancelTopUp(@PathVariable("id") Long id) {
        return ResponseEntity.ok(transferService.cancelTopUp(id));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Map.of("message", message.isBlank() ? "Некорректный запрос" : message));
    }
}

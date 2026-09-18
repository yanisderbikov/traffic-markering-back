package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ru.trafficmarkering.dto.transfer.TransferRejectRequestDTO;
import ru.trafficmarkering.dto.transfer.TransferSentRequestDTO;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.dto.wallet.WalletDTO;
import ru.trafficmarkering.dto.wallet.WalletOperationRequestDTO;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.service.transfer.TransferService;
import ru.trafficmarkering.service.wallet.WalletService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
@Tag(name = "Finance", description = "Кабинет менеджера финансов: кошельки заказчиков, пополнения, выводы и выплаты криаторам")
public class FinanceController {

    private final WalletService walletService;
    private final TransferService transferService;

    @Operation(summary = "Кошельки заказчиков",
            description = "Все заказчики с кошельком: свободный остаток, сумма бюджетов объявлений и начислено криаторам",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/customers")
    public ResponseEntity<List<WalletDTO>> financeCustomers() {
        return ResponseEntity.ok(walletService.customers());
    }

    @Operation(summary = "Кошелёк заказчика",
            description = "404 — учётки нет; 400 — у пользователя роль без кошелька",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/customers/{userId}")
    public ResponseEntity<WalletDTO> financeCustomer(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(walletService.customer(userId));
    }

    @Operation(summary = "Все операции по всем кошелькам",
            description = "Одна таблица: пополнения, резервы, начисления криаторам и выводы; у каждой строки "
                    + "откуда → куда, сумма и статус. Фильтры необязательны; новые сверху",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/operations")
    public ResponseEntity<List<OperationRowDTO>> financeOperations(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "type", required = false) WalletTransactionType type,
            @RequestParam(value = "status", required = false) WalletTransactionStatus status) {
        return ResponseEntity.ok(walletService.operations(userId, type, status));
    }

    @Operation(summary = "Операция целиком",
            description = "Любая проводка любого кошелька; для пополнения, вывода и выплаты — ещё перевод: "
                    + "адрес TRON, номер транзакции, скриншоты и комментарии",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/operations/{id}")
    public ResponseEntity<OperationDetailDTO> financeOperation(@PathVariable("id") Long id) {
        return ResponseEntity.ok(walletService.operation(id));
    }

    @Operation(summary = "Отклонить пополнение, вывод или выплату",
            description = "Пока операция открыта (PENDING или SENT): деньги возвращаются туда, откуда ушли, "
                    + "владелец кошелька видит причину. 409, если пополнение уже разошлось по объявлениям",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/operations/{id}/reject")
    public ResponseEntity<OperationDetailDTO> rejectOperation(@PathVariable("id") Long id,
                                                              @Valid @RequestBody TransferRejectRequestDTO request) {
        return ResponseEntity.ok(transferService.reject(id, request));
    }

    @Operation(summary = "Пополнить кошелёк заказчика",
            description = "Заказчик уже перевёл USDT: сумма в копейках, номер транзакции и скриншоты обязательны. "
                    + "Деньги сразу доступны, операция в SENT ждёт подтверждения заказчика",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/customers/{userId}/top-up")
    public ResponseEntity<OperationDetailDTO> topUpWallet(@PathVariable("userId") Long userId,
                                                          @Valid @RequestBody WalletOperationRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.topUp(userId, request));
    }

    @Operation(summary = "Вывести заказчику из кошелька",
            description = "Только из свободного остатка (409, если не хватает): USDT уже отправлены на адрес TRON "
                    + "заказчика, номер транзакции и скриншоты обязательны. Операция в SENT ждёт подтверждения заказчика",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/customers/{userId}/withdrawal")
    public ResponseEntity<OperationDetailDTO> withdrawFromWallet(@PathVariable("userId") Long userId,
                                                                 @Valid @RequestBody WalletOperationRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.withdraw(userId, request));
    }

    @Operation(summary = "Заявки криаторов на выплату",
            description = "Короткие строки: кто, сколько, статус; открытые (PENDING, SENT) сверху",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/payouts")
    public ResponseEntity<List<OperationRowDTO>> financePayouts() {
        return ResponseEntity.ok(transferService.payouts());
    }

    @Operation(summary = "Отметить выплату отправленной",
            description = "Только из PENDING. Номер транзакции и скриншоты обязательны; "
                    + "заявка переходит в SENT и ждёт подтверждения криатора",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/payouts/{id}/sent")
    public ResponseEntity<OperationDetailDTO> markPayoutSent(@PathVariable("id") Long id,
                                                             @Valid @RequestBody TransferSentRequestDTO request) {
        return ResponseEntity.ok(transferService.markPayoutSent(id, request));
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

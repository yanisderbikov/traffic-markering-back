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
import ru.trafficmarkering.dto.earnings.CreatorWalletDTO;
import ru.trafficmarkering.dto.earnings.PayoutCreateRequestDTO;
import ru.trafficmarkering.dto.wallet.OperationDetailDTO;
import ru.trafficmarkering.dto.wallet.OperationRowDTO;
import ru.trafficmarkering.service.earnings.EarningsService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/earnings")
@RequiredArgsConstructor
@Tag(name = "Earnings", description = "Кошелёк криатора: начисления за просмотры и заявки на вывод USDT")
public class EarningsController {

    private final EarningsService earningsService;

    @Operation(summary = "Мой заработок",
            description = "Доступно к выводу, зарезервировано в заявках, выведено, зачислено всего и сколько ещё ждёт зачисления",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<CreatorWalletDTO> myEarnings() {
        return ResponseEntity.ok(earningsService.myWallet());
    }

    @Operation(summary = "Мои операции",
            description = "Короткие строки: что за операция, сумма и статус; подробности — по id",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/operations")
    public ResponseEntity<List<OperationRowDTO>> myOperations() {
        return ResponseEntity.ok(earningsService.myOperations());
    }

    @Operation(summary = "Операция целиком",
            description = "Проводка и, для вывода, заявка: адрес, скриншоты и комментарий финансиста, причина отказа",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/operations/{id}")
    public ResponseEntity<OperationDetailDTO> myOperation(@PathVariable("id") Long id) {
        return ResponseEntity.ok(earningsService.myOperation(id));
    }

    @Operation(summary = "Заявка на вывод",
            description = "Сумма резервируется сразу, заявка уходит финансисту в статусе PENDING; "
                    + "400 — адрес не TRC-20, 409 — не хватает доступных денег",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/payouts")
    public ResponseEntity<OperationDetailDTO> requestPayout(@Valid @RequestBody PayoutCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(earningsService.requestPayout(request));
    }

    @Operation(summary = "Подтвердить получение",
            description = "Только для заявки в статусе SENT: криатор увидел USDT на своём кошельке",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/payouts/{id}/confirm")
    public ResponseEntity<OperationDetailDTO> confirmPayout(@PathVariable("id") Long id) {
        return ResponseEntity.ok(earningsService.confirmPayout(id));
    }

    @Operation(summary = "Отменить заявку",
            description = "Только пока финансист её не отправил (PENDING); деньги возвращаются в доступные",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/payouts/{id}/cancel")
    public ResponseEntity<OperationDetailDTO> cancelPayout(@PathVariable("id") Long id) {
        return ResponseEntity.ok(earningsService.cancelPayout(id));
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

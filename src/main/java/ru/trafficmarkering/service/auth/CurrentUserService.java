package ru.trafficmarkering.service.auth;

import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;

/**
 * Достаёт из контекста безопасности того, кто пришёл с запросом.
 * Фильтр кладёт в принципал только логин из токена, а сервисам почти всегда
 * нужна сама учётка (id для связей, имя для DTO) — этот поиск и живёт здесь,
 * чтобы не размазывать SecurityContextHolder по всем сервисам.
 */
public interface CurrentUserService {

    /**
     * Текущий пользователь.
     *
     * @throws org.springframework.web.server.ResponseStatusException 401, если запрос анонимный
     *                                                               или учётки из токена уже нет
     */
    User require();

    /**
     * Текущий пользователь с проверкой роли. ADMIN проходит любую проверку:
     * ему открыты и кабинет заказчика, и кабинет криатора.
     *
     * @throws org.springframework.web.server.ResponseStatusException 401 — не авторизован, 403 — не та роль
     */
    User require(Role role);
}

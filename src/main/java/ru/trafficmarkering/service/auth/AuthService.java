package ru.trafficmarkering.service.auth;

import ru.trafficmarkering.dto.CurrentUserDTO;
import ru.trafficmarkering.dto.LoginRequestDTO;
import ru.trafficmarkering.dto.LoginResponseDTO;
import ru.trafficmarkering.dto.RegisterRequestDTO;

public interface AuthService {

    /**
     * Регистрация заказчика или криатора. Сразу заводит пустой профиль нужного типа
     * и возвращает токен: человек попадает в кабинет без второго похода на форму входа.
     */
    LoginResponseDTO register(RegisterRequestDTO request);

    LoginResponseDTO login(LoginRequestDTO request);

    /** Кто я: фронт зовёт это после перезагрузки страницы, когда в руках только токен. */
    CurrentUserDTO me();
}

package ru.trafficmarkering.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.CabinetTab;

@Schema(description = "Вкладка левого меню личного кабинета")
public record CabinetTabDTO(
        @Schema(description = "Код вкладки, по нему фронт закрывает страницы раздела") String key,
        @Schema(description = "Адрес раздела во фронте") String path,
        @Schema(description = "Название в боковом меню") String label,
        @Schema(description = "Короткое название для нижней панели на телефоне") String shortLabel,
        @Schema(description = "Имя иконки из набора фронта") String icon,
        @Schema(description = "Подсвечивать только на точном совпадении адреса, без вложенных страниц") boolean exact
) {
    public static CabinetTabDTO from(CabinetTab tab) {
        return new CabinetTabDTO(
                tab.name(),
                tab.getPath(),
                tab.getLabel(),
                tab.getShortLabel(),
                tab.getIcon(),
                tab.isExact());
    }
}

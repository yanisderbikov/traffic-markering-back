package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Отклик креатора на объявление")
public class ApplicationCreateRequestDTO {

    @NotNull(message = "Объявление обязательно")
    @Schema(description = "ID объявления", required = true)
    private UUID campaignId;

    @Size(max = 1024, message = "Ссылка не длиннее 1024 символов")
    @Schema(description = "Ссылка на выложенный ролик; площадка определяется по ней: YouTube, TikTok или Instagram. "
            + "Без ссылки оффер берётся в работу (IN_PROGRESS), ролик прикладывается позже",
            example = "https://www.tiktok.com/@demo/video/123")
    private String videoUrl;

    @Schema(description = "Комментарий заказчику: что сняли и почему так")
    private String comment;

    @Schema(description = "Объявление «ссылка и промокод»: беру надбавку за рекламную ссылку в профиле")
    private Boolean withLink;

    @Schema(description = "Объявление «ссылка и промокод»: беру надбавку за промокод в закреплённом комментарии")
    private Boolean withPromo;

    @Size(max = 64, message = "Тематика не длиннее 64 символов")
    @Schema(description = "Тематика ролика из contentRates объявления; обязательна, если заказчик задал ставки по тематикам",
            example = "CS и Dota 2")
    private String contentTopic;
}

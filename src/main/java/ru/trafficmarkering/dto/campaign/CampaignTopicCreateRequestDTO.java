package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Новая тематика, если подходящей не нашлось")
public class CampaignTopicCreateRequestDTO {

    @NotBlank(message = "Введите название тематики")
    @Size(max = 48, message = "Название тематики не длиннее 48 символов")
    @Schema(description = "Название: от 2 до 48 символов — буквы, цифры, пробелы и знаки . , - & + / ( ) « » ' \"",
            required = true, example = "Кулинария")
    private String name;
}

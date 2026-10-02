package ru.trafficmarkering.dto.campaign;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.campaign.CampaignMaterial;

@Schema(description = "Материал для криатора: файл со временной ссылкой на скачивание или внешняя ссылка")
public record CampaignMaterialDTO(
        @Schema(description = "FILE или LINK") String kind,
        @Schema(description = "Подпись: имя файла или название ссылки") String title,
        @Schema(description = "Куда вести: для файла — временная ссылка на хранилище, для ссылки — сам адрес") String url,
        @Schema(description = "Ключ файла в хранилище; отправляется обратно при обновлении объявления") String fileKey,
        @Schema(description = "MIME-тип файла; null у ссылки") String contentType,
        @Schema(description = "Размер файла в байтах; null у ссылки") Long sizeBytes,
        @Schema(description = "Файл откроется во вкладке браузера (картинка, PDF, видео), а не скачается") boolean opensInBrowser
) {
    public static CampaignMaterialDTO from(CampaignMaterial material, String fileUrl) {
        boolean file = material.isFile();
        return new CampaignMaterialDTO(
                material.getKind() != null ? material.getKind().name() : null,
                material.getTitle(),
                file ? fileUrl : material.getUrl(),
                file ? material.getFileKey() : null,
                file ? material.getContentType() : null,
                file ? material.getSizeBytes() : null,
                file && material.opensInBrowser());
    }
}

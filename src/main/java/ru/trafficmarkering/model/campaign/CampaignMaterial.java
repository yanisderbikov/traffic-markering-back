package ru.trafficmarkering.model.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class CampaignMaterial {

    private static final Set<String> BROWSER_VIEWABLE_TYPES = Set.of("application/pdf");

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private MaterialKind kind;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "url", length = 2048)
    private String url;

    @Column(name = "file_key", length = 512)
    private String fileKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    public boolean isFile() {
        return kind == MaterialKind.FILE;
    }

    public boolean opensInBrowser() {
        if (contentType == null) {
            return false;
        }
        String type = contentType.toLowerCase();
        return BROWSER_VIEWABLE_TYPES.contains(type)
                || (type.startsWith("image/") && !type.contains("svg"))
                || type.startsWith("video/")
                || type.startsWith("audio/");
    }
}

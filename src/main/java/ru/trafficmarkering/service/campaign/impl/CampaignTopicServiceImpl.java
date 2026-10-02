package ru.trafficmarkering.service.campaign.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.campaign.CampaignTopicCreateRequestDTO;
import ru.trafficmarkering.dto.campaign.CampaignTopicDTO;
import ru.trafficmarkering.model.campaign.CampaignTopic;
import ru.trafficmarkering.repository.GetterCampaignTopic;
import ru.trafficmarkering.repository.SaverCampaignTopic;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.campaign.CampaignTopicService;
import ru.trafficmarkering.util.PublicIdGenerator;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
class CampaignTopicServiceImpl implements CampaignTopicService {

    static final int POPULAR_LIMIT = 10;
    static final int SEARCH_LIMIT_MAX = 20;
    static final int NAME_MIN_LENGTH = 2;

    /** Без эмодзи, ссылок и прочего мусора: тематику увидят другие заказчики в поиске. */
    private static final Pattern ALLOWED_NAME = Pattern.compile("[\\p{L}\\p{N} .,&+/()«»'\"-]+");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private final GetterCampaignTopic getterCampaignTopic;
    private final SaverCampaignTopic saverCampaignTopic;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional(readOnly = true)
    public List<CampaignTopicDTO> getPopular() {
        return find("", POPULAR_LIMIT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignTopicDTO> search(String query, int limit) {
        String normalized = query != null ? CampaignTopic.normalize(query) : "";
        return find(normalized, Math.max(1, Math.min(limit, SEARCH_LIMIT_MAX)));
    }

    @Override
    @Transactional
    public CampaignTopicDTO create(CampaignTopicCreateRequestDTO request) {
        String name = displayName(request.getName());
        String normalized = CampaignTopic.normalize(name);
        CampaignTopic existing = getterCampaignTopic.getByNormalizedName(normalized).orElse(null);
        if (existing != null) {
            return CampaignTopicDTO.from(existing);
        }
        try {
            return CampaignTopicDTO.from(saverCampaignTopic.save(CampaignTopic.builder()
                    .code(PublicIdGenerator.generateUnique(getterCampaignTopic::existsByCode))
                    .name(name)
                    .normalizedName(normalized)
                    .createdBy(currentUserService.require().getId())
                    .build()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Такую тематику только что добавили — найдите её поиском");
        }
    }

    private List<CampaignTopicDTO> find(String normalizedQuery, int limit) {
        return getterCampaignTopic.search(normalizedQuery, limit).stream()
                .map(CampaignTopicDTO::from)
                .toList();
    }

    /**
     * Схлопывает пробелы и делает первую букву заглавной, чтобы «кулинария» из строки поиска
     * встала в один ряд с базовыми тематиками. «iPhone» и «eSports» не трогаем: вторая буква
     * заглавная — значит, регистр выбран намеренно.
     */
    private String displayName(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.length() < NAME_MIN_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Название тематики — хотя бы " + NAME_MIN_LENGTH + " символа");
        }
        if (!ALLOWED_NAME.matcher(name).matches() || !LETTER.matcher(name).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "В названии тематики — буквы, цифры, пробелы и знаки . , - & + / ( ) « » ' \"");
        }
        if (Character.isLowerCase(name.charAt(0)) && !Character.isUpperCase(name.charAt(1))) {
            return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        }
        return name;
    }
}

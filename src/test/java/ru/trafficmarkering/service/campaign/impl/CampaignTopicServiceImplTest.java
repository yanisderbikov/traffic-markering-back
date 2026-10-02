package ru.trafficmarkering.service.campaign.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.campaign.CampaignTopicCreateRequestDTO;
import ru.trafficmarkering.dto.campaign.CampaignTopicDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.CampaignTopic;
import ru.trafficmarkering.repository.GetterCampaignTopic;
import ru.trafficmarkering.repository.SaverCampaignTopic;
import ru.trafficmarkering.service.auth.CurrentUserService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignTopicServiceImplTest {

    private final GetterCampaignTopic getterCampaignTopic = mock(GetterCampaignTopic.class);
    private final SaverCampaignTopic saverCampaignTopic = mock(SaverCampaignTopic.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);

    private final CampaignTopicServiceImpl service =
            new CampaignTopicServiceImpl(getterCampaignTopic, saverCampaignTopic, currentUserService);

    private final User customer = User.builder().id(7L).username("brand@traffic.ru").name("Бренд").role(Role.CUSTOMER).build();

    @BeforeEach
    void setUp() {
        when(currentUserService.require()).thenReturn(customer);
        when(getterCampaignTopic.getByNormalizedName(anyString())).thenReturn(Optional.empty());
        when(getterCampaignTopic.search(anyString(), anyInt())).thenReturn(List.of());
        when(saverCampaignTopic.save(any(CampaignTopic.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CampaignTopicCreateRequestDTO named(String name) {
        return CampaignTopicCreateRequestDTO.builder().name(name).build();
    }

    private CampaignTopic savedTopic() {
        ArgumentCaptor<CampaignTopic> saved = ArgumentCaptor.forClass(CampaignTopic.class);
        verify(saverCampaignTopic).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void popularAreTopTenWithoutFilter() {
        when(getterCampaignTopic.search("", 10)).thenReturn(List.of(
                CampaignTopic.builder().code("TECH").name("Технологии и гаджеты").averageRatePerThousandKopecks(200_00L).build()));

        List<CampaignTopicDTO> popular = service.getPopular();

        assertThat(popular).containsExactly(new CampaignTopicDTO("TECH", "Технологии и гаджеты", 200_00L));
    }

    @Test
    void searchNormalizesQueryAndClampsLimit() {
        service.search("  Ёлочные   ИГРУШКИ ", 500);
        verify(getterCampaignTopic).search("елочные игрушки", 20);

        service.search(null, 0);
        verify(getterCampaignTopic).search("", 1);
    }

    @Test
    void createCapitalizesAndCollapsesSpaces() {
        CampaignTopicDTO created = service.create(named("  домашняя   выпечка "));

        CampaignTopic topic = savedTopic();
        assertThat(topic.getName()).isEqualTo("Домашняя выпечка");
        assertThat(topic.getNormalizedName()).isEqualTo("домашняя выпечка");
        assertThat(topic.getCreatedBy()).isEqualTo(7L);
        assertThat(topic.getAverageRatePerThousandKopecks()).isNull();
        assertThat(topic.getCode()).matches("[A-Z0-9]{8}");
        assertThat(created.code()).isEqualTo(topic.getCode());
        assertThat(created.description()).isEqualTo("Домашняя выпечка");
    }

    @Test
    void createKeepsDeliberateCase() {
        service.create(named("iPhone-обзоры"));

        assertThat(savedTopic().getName()).isEqualTo("iPhone-обзоры");
    }

    @Test
    void createReturnsExistingInsteadOfDuplicate() {
        CampaignTopic games = CampaignTopic.builder().code("GAMING").name("Игры").averageRatePerThousandKopecks(100_00L).build();
        when(getterCampaignTopic.getByNormalizedName("игры")).thenReturn(Optional.of(games));

        CampaignTopicDTO result = service.create(named("ИГРЫ"));

        assertThat(result.code()).isEqualTo("GAMING");
        verify(saverCampaignTopic, never()).save(any());
    }

    @Test
    void createRejectsJunk() {
        for (String junk : List.of("я", "   ", "123", "🔥🔥🔥", "https://spam.example")) {
            assertThatThrownBy(() -> service.create(named(junk)))
                    .as(junk)
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(saverCampaignTopic, never()).save(any());
    }

    @Test
    void concurrentDuplicateBecomesConflict() {
        when(saverCampaignTopic.save(any(CampaignTopic.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(named("Кулинария")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}

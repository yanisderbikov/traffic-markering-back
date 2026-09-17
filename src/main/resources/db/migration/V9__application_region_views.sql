-- Просмотры отклика, прошедшие проверку региона оффера (TASK-6).
-- NULL — гео ролика ещё не подтверждено геоаналитикой: CampaignAccrualService
-- в этом случае начисление по региональному офферу не делает, а не считает его нулевым навсегда.
ALTER TABLE application
    ADD COLUMN region_views BIGINT CHECK (region_views >= 0);

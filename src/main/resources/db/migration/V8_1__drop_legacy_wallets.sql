-- V8__wallets.sql (ветка feature/wallets-admin) успела уехать на dev и создать там
-- первую версию кошельков: UUID-ключи и единственный тип операции ADMIN_ADJUSTMENT.
-- Эта реализация отброшена в пользу V10/V11 (BIGSERIAL-ключи, TOP_UP/ALLOCATION/
-- EARNING/PAYOUT, выплаты), поэтому старые таблицы убираем целиком — в них лежали
-- только ручные корректировки баланса админом, переносить нечего.
-- Сам V8__wallets.sql оставлен как есть: он уже применён, и Flyway сверяет его checksum.
DROP TABLE IF EXISTS wallet_transaction;
DROP TABLE IF EXISTS wallet;

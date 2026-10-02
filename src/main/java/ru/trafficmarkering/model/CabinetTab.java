package ru.trafficmarkering.model;

public enum CabinetTab {
    OVERVIEW("/app", "Обзор", "Обзор", "chart", true),
    BOARD("/app/board", "Офферы", "Офферы", "grid", false),
    CAMPAIGNS("/app/campaigns", "Мои кампании", "Кампании", "briefcase", false),
    APPLICATIONS("/app/applications", "Мои работы", "Работы", "briefcase", false),
    WALLET("/app/wallet", "Финансы", "Финансы", "wallet", false),
    EARNINGS("/app/earnings", "Финансы", "Финансы", "wallet", false),
    CUSTOMER_WALLETS("/app/finance", "Кошельки заказчиков", "Кошельки", "wallet", true),
    TOP_UPS("/app/finance/top-ups", "Пополнения", "Пополнения", "plus", false),
    PAYOUTS("/app/finance/payouts", "Выплаты", "Выплаты", "download", false),
    OPERATIONS("/app/finance/operations", "Все операции", "Операции", "chart", false),
    MODERATION("/app/admin/moderation", "Модерация", "Модерация", "check", false),
    FRAUD("/app/admin/fraud", "Антифрод", "Антифрод", "shield", true),
    CREATOR_TRUST("/app/admin/fraud/creators", "Репутация креаторов", "Репутация", "users", false),
    USERS("/app/admin/users", "Пользователи", "Люди", "users", false),
    REFERRAL("/app/referral", "Рефералка", "Рефералка", "gift", false),
    PROFILE("/app/profile", "Профиль", "Профиль", "user", true),
    SOCIALS("/app/profile/socials", "Соцсети", "Соцсети", "link", false);

    private final String path;
    private final String label;
    private final String shortLabel;
    private final String icon;
    private final boolean exact;

    CabinetTab(String path, String label, String shortLabel, String icon, boolean exact) {
        this.path = path;
        this.label = label;
        this.shortLabel = shortLabel;
        this.icon = icon;
        this.exact = exact;
    }

    public String getPath() {
        return path;
    }

    public String getLabel() {
        return label;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isExact() {
        return exact;
    }
}

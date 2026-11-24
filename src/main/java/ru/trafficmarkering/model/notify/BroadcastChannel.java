package ru.trafficmarkering.model.notify;

/** TELEGRAM — только привязавшим бота; EMAIL — только с почтой; ANY — в Telegram, а у кого его нет — на почту */
public enum BroadcastChannel {
    TELEGRAM,
    EMAIL,
    ANY
}

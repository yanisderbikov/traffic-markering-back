package ru.trafficmarkering.model.notify;

import ru.trafficmarkering.model.Role;

import java.util.EnumSet;
import java.util.Set;

/** Кому рассылка: всем креаторам и рекламодателям или только одной стороне */
public enum BroadcastAudience {
    ALL(EnumSet.of(Role.CREATOR, Role.CUSTOMER)),
    CREATOR(EnumSet.of(Role.CREATOR)),
    CUSTOMER(EnumSet.of(Role.CUSTOMER));

    private final Set<Role> roles;

    BroadcastAudience(Set<Role> roles) {
        this.roles = roles;
    }

    public Set<Role> roles() {
        return roles;
    }
}

package com.pvpbot;

import java.util.Locale;

public enum BotMode {
    SWORD, POT, MACE, CART, SMP;

    public static BotMode parse(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

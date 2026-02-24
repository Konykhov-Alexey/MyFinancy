package org.example.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private DataSeeder() {}

    public static void seed() {
        log.debug("DataSeeder: проверка начальных данных (реализация — Phase 2)");
    }
}

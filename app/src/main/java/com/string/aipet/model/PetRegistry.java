package com.string.aipet.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PetRegistry {
    private static final Map<String, PetDefinition> SPECIES = new LinkedHashMap<>();
    public static final String SNAKE = "little_snake";
    static {
        register(new PetDefinition(SNAKE, "小蛇", "芽芽", "一条喜欢晒太阳、听故事的小蛇。", 0xFF97B883, 22, 18));
    }
    private PetRegistry() {}
    public static void register(PetDefinition pet) {
        if (SPECIES.containsKey(pet.id)) throw new IllegalArgumentException("Duplicate pet: " + pet.id);
        SPECIES.put(pet.id, pet);
    }
    public static PetDefinition get(String id) {
        PetDefinition pet = SPECIES.get(id);
        if (pet == null) throw new IllegalArgumentException("Unknown pet: " + id);
        return pet;
    }
    public static Collection<PetDefinition> all() { return Collections.unmodifiableCollection(SPECIES.values()); }
}

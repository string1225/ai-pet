package com.string.aipet.model;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import com.string.aipet.learning.Subject;

/** Plain Java state, reusable by other screens/renderers and independently testable. */
public final class PetState {
    public String speciesId = PetRegistry.SNAKE;
    public String name = "芽芽";
    public int warmth, fullness = 72, happiness = 80, energy = 86, stars, snacks = 5;
    public int affection, completedLessons;
    public boolean sleeping, sound = true;
    public long adoptedAt, lastUpdated = System.currentTimeMillis();
    public final EnumMap<Subject, Integer> learned = new EnumMap<>(Subject.class);
    public final Set<String> appliedGifts = new HashSet<>();
    public boolean hatched() { return warmth >= 100; }
    public int level() { return 1 + affection / 30; }
    public int learnedCount(Subject subject) { return learned.getOrDefault(subject, 0); }
    public int totalLearned() { return learned.values().stream().mapToInt(Integer::intValue).sum(); }
    public int days(long now) { return adoptedAt == 0 ? 1 : (int) Math.max(1, (now - adoptedAt) / 86400000L + 1); }
    public void clamp() {
        warmth = limit(warmth); fullness = limit(fullness); happiness = limit(happiness); energy = limit(energy);
        stars = Math.max(0, stars); snacks = Math.max(0, snacks); affection = Math.max(0, affection);
    }
    private static int limit(int value) { return Math.max(0, Math.min(100, value)); }
}

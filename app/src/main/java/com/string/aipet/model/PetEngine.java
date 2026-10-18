package com.string.aipet.model;

import com.string.aipet.learning.Subject;

/** Care and learning rules. There is no death, payment, or penalty for a wrong answer. */
public final class PetEngine {
    public final PetState state;
    public PetEngine(PetState state) { this.state = state; }
    public void tick(long now) {
        long hours = Math.max(0, now - state.lastUpdated) / 3600000L;
        if (hours < 1) return;
        int elapsed = (int) Math.min(8, hours);
        if (state.hatched()) {
            state.fullness = Math.max(20, state.fullness - elapsed * 3);
            state.happiness = Math.max(25, state.happiness - elapsed * 2);
            state.cleanliness = Math.max(25, state.cleanliness - elapsed * 2);
            state.energy = state.sleeping ? state.energy + elapsed * 12 : Math.max(20, state.energy - elapsed * 2);
        }
        state.lastUpdated = now;
        state.clamp();
    }
    public boolean feed() {
        if (!state.hatched() || state.sleeping || state.snacks < 1 || state.fullness >= 100) return false;
        state.snacks--; state.fullness += PetRegistry.get(state.speciesId).foodGain;
        state.happiness += 3; state.affection += 2; state.clamp(); return true;
    }
    public boolean water() {
        if (!state.hatched() || state.sleeping) return false;
        state.happiness += 2; state.energy += 3; state.clamp(); return true;
    }
    /** A correct care answer can prepare a snack even when the basket is empty. */
    public boolean feedAfterQuiz() {
        if (!state.hatched() || state.sleeping || state.fullness >= 100) return false;
        if (state.snacks < 1) state.snacks = 1;
        return feed();
    }
    public boolean clean() {
        if (!state.hatched() || state.sleeping || state.cleanliness >= 100) return false;
        state.cleanliness += 25; state.happiness += 3; state.affection += 2; state.clamp(); return true;
    }
    public boolean pet() {
        if (!state.hatched() || state.sleeping) return false;
        state.happiness += 1; state.clamp(); return true;
    }
    public void correct(Subject subject, long now) {
        state.stars++; state.learned.put(subject, state.learnedCount(subject) + 1);
        if (!state.hatched()) {
            state.warmth += 25;
            if (state.hatched()) { state.adoptedAt = now; state.affection += 5; }
        }
        state.clamp();
    }
    public void finishLesson() {
        state.completedLessons++; state.snacks += 2; state.affection += 4;
        state.happiness += 5; state.clamp();
    }
    public boolean play() {
        if (!state.hatched() || state.sleeping || state.energy < 10) return false;
        state.happiness += PetRegistry.get(state.speciesId).playGain;
        state.energy -= 6; state.affection += 4; state.clamp(); return true;
    }
    public void sleep() { if (state.hatched()) { state.sleeping = true; state.lastUpdated = System.currentTimeMillis(); } }
    public void wake(long now) { tick(now); state.sleeping = false; }
}

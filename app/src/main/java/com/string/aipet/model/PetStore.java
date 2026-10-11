package com.string.aipet.model;

import android.content.Context;
import android.content.SharedPreferences;
import com.string.aipet.learning.Subject;
import org.json.JSONException;
import org.json.JSONObject;

/** Versioned local save; no network account or permissions required. */
public final class PetStore {
    private final SharedPreferences preferences;
    public PetStore(Context context) { preferences = context.getSharedPreferences("pet_home", Context.MODE_PRIVATE); }
    public PetState load() {
        String active = preferences.getString("active_species", PetRegistry.SNAKE);
        try { PetRegistry.get(active); } catch (IllegalArgumentException e) { active = PetRegistry.SNAKE; }
        return loadSpecies(active);
    }
    public PetState activate(String speciesId) {
        PetRegistry.get(speciesId);
        preferences.edit().putString("active_species", speciesId).apply();
        return loadSpecies(speciesId);
    }
    private PetState loadSpecies(String speciesId) {
        PetState state = new PetState();
        state.speciesId = speciesId; state.name = PetRegistry.get(speciesId).defaultName;
        try {
            String legacy = speciesId.equals(PetRegistry.SNAKE) ? preferences.getString("state", "{}") : "{}";
            JSONObject j = new JSONObject(preferences.getString("pet." + speciesId, legacy));
            state.name = j.optString("name", PetRegistry.get(state.speciesId).defaultName);
            state.warmth = j.optInt("warmth", 0); state.fullness = j.optInt("fullness", 72);
            state.happiness = j.optInt("happiness", 80); state.energy = j.optInt("energy", 86);
            state.stars = j.optInt("stars", 0); state.snacks = j.optInt("snacks", 5);
            state.affection = j.optInt("affection", 0); state.completedLessons = j.optInt("lessons", 0);
            state.sleeping = j.optBoolean("sleeping", false); state.sound = j.optBoolean("sound", true);
            state.adoptedAt = j.optLong("adoptedAt", 0); state.lastUpdated = j.optLong("lastUpdated", System.currentTimeMillis());
            for (Subject subject : Subject.values()) state.learned.put(subject, Math.max(0, j.optInt(subject.name(), 0)));
        } catch (JSONException ignored) { /* A damaged save falls back to a safe new egg. */ }
        state.clamp(); return state;
    }
    public void save(PetState s) {
        try {
            JSONObject j = new JSONObject();
            j.put("version", 1); j.put("species", s.speciesId); j.put("name", s.name);
            j.put("warmth", s.warmth); j.put("fullness", s.fullness); j.put("happiness", s.happiness);
            j.put("energy", s.energy); j.put("stars", s.stars); j.put("snacks", s.snacks);
            j.put("affection", s.affection); j.put("lessons", s.completedLessons);
            j.put("sleeping", s.sleeping); j.put("sound", s.sound); j.put("adoptedAt", s.adoptedAt);
            j.put("lastUpdated", s.lastUpdated);
            for (Subject subject : Subject.values()) j.put(subject.name(), s.learnedCount(subject));
            preferences.edit().putString("pet." + s.speciesId, j.toString()).apply();
        } catch (JSONException e) { throw new IllegalStateException("Cannot save pet", e); }
    }
}

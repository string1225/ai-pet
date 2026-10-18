package com.string.aipet.model;

import android.content.Context;
import android.content.SharedPreferences;
import com.string.aipet.learning.Subject;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

/** Versioned local pet save, independent of the manual-update network and preferences. */
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
            state.cleanliness = j.optInt("cleanliness", 80);
            state.stars = j.optInt("stars", 0); state.snacks = j.optInt("snacks", 5);
            state.affection = j.optInt("affection", 0); state.completedLessons = j.optInt("lessons", 0);
            state.sleeping = j.optBoolean("sleeping", false); state.sound = j.optBoolean("sound", true);
            state.adoptedAt = j.optLong("adoptedAt", 0); state.lastUpdated = j.optLong("lastUpdated", System.currentTimeMillis());
            for (Subject subject : Subject.values()) state.learned.put(subject, Math.max(0, j.optInt(subject.name(), 0)));
            JSONArray gifts=j.optJSONArray("appliedGifts");if(gifts!=null)for(int i=0;i<gifts.length();i++)state.appliedGifts.add(gifts.getString(i));
            readSet(j,"decorations",state.decorations);readSet(j,"equippedDecorations",state.equippedDecorations);
        } catch (JSONException ignored) { /* A damaged save falls back to a safe new egg. */ }
        state.clamp(); return state;
    }
    public void save(PetState s) {
        write(s,false);
    }
    public void saveNow(PetState s) { write(s,true); }
    private void write(PetState s,boolean synchronous){
        try {
            JSONObject j = new JSONObject();
            j.put("version", 2); j.put("species", s.speciesId); j.put("name", s.name);
            j.put("warmth", s.warmth); j.put("fullness", s.fullness); j.put("happiness", s.happiness);
            j.put("energy", s.energy); j.put("stars", s.stars); j.put("snacks", s.snacks);
            j.put("cleanliness",s.cleanliness);j.put("decorations",new JSONArray(s.decorations));j.put("equippedDecorations",new JSONArray(s.equippedDecorations));
            j.put("affection", s.affection); j.put("lessons", s.completedLessons);
            j.put("sleeping", s.sleeping); j.put("sound", s.sound); j.put("adoptedAt", s.adoptedAt);
            j.put("lastUpdated", s.lastUpdated);
            for (Subject subject : Subject.values()) j.put(subject.name(), s.learnedCount(subject));
            j.put("appliedGifts",new JSONArray(s.appliedGifts));
            String key="pet."+s.speciesId;String previous=preferences.getString(key,null);
            SharedPreferences.Editor edit=preferences.edit().putString(key,j.toString());
            if(synchronous){
                if(!edit.commit()){
                    // commit() changes the preference memory map even when the disk write fails.
                    SharedPreferences.Editor rollback=preferences.edit();
                    if(previous==null)rollback.remove(key);else rollback.putString(key,previous);
                    rollback.apply();
                    throw new IllegalStateException("宠物保存失败，请检查存储空间");
                }
            }else edit.apply();
        } catch (JSONException e) { throw new IllegalStateException("Cannot save pet", e); }
    }
    private static void readSet(JSONObject j,String key,java.util.Set<String> target) throws JSONException {
        JSONArray values=j.optJSONArray(key);if(values!=null)for(int i=0;i<values.length();i++)target.add(values.getString(i));
    }
}

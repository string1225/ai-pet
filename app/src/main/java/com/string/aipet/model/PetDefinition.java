package com.string.aipet.model;

/** Species configuration: care balancing and artwork are independent of saved state. */
public final class PetDefinition {
    public final String id, species, defaultName, introduction;
    public final int color, foodGain, playGain;

    public PetDefinition(String id, String species, String defaultName, String introduction,
                         int color, int foodGain, int playGain) {
        this.id = id; this.species = species; this.defaultName = defaultName;
        this.introduction = introduction; this.color = color;
        this.foodGain = foodGain; this.playGain = playGain;
    }
}

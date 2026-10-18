package com.string.aipet.family;

import com.string.aipet.model.PetState;
import com.string.aipet.model.RewardCatalog;

/** A persisted delivery ID lets a committed gift retry after interruption without duplicate rewards. */
public final class GiftDelivery {
    private GiftDelivery(){}
    public static boolean apply(FamilyState.Gift gift,PetState pet){
        if(gift.delivered||!gift.speciesId.equals(pet.speciesId)||pet.appliedGifts.contains(gift.id))return false;
        RewardCatalog.apply(pet,RewardCatalog.get(gift.kind));
        pet.appliedGifts.add(gift.id);pet.clamp();return true;
    }
}

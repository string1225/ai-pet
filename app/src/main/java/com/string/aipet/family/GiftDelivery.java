package com.string.aipet.family;

import com.string.aipet.model.PetState;

/** A persisted delivery ID lets a committed gift retry after interruption without duplicate rewards. */
public final class GiftDelivery {
    private GiftDelivery(){}
    public static boolean apply(FamilyState.Gift gift,PetState pet){
        if(gift.delivered||!gift.speciesId.equals(pet.speciesId)||pet.appliedGifts.contains(gift.id))return false;
        if(gift.kind.equals("snacks"))pet.snacks+=2;
        else if(gift.kind.equals("party")){pet.affection+=12;pet.happiness+=10;}
        else throw new IllegalArgumentException("未知礼物");
        pet.appliedGifts.add(gift.id);pet.clamp();return true;
    }
}

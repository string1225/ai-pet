package com.string.aipet.family;

import com.string.aipet.model.PetState;
import org.junit.Test;
import static org.junit.Assert.*;

public class GiftAndPinTest {
    @Test public void growthStarsBuySameDecorationWithoutSpendingKnowledge(){
        FamilyState s=funded();PetState p=new PetState();p.stars=17;
        FamilyState.Gift gift=s.redeem(p.speciesId,"star_garland",1);
        assertTrue(GiftDelivery.apply(gift,p));assertFalse(GiftDelivery.apply(gift,p));
        assertEquals(4,s.balance());assertEquals(17,p.stars);assertTrue(p.equippedDecorations.contains("star_garland"));
    }
    @Test public void growthFruitDeliveryRetriesWithoutDoubleLeveling(){
        FamilyState s=funded();PetState p=new PetState();p.affection=12;
        FamilyState.Gift gift=s.redeem(p.speciesId,"growth_fruit",1);
        assertTrue(GiftDelivery.apply(gift,p));assertFalse(GiftDelivery.apply(gift,p));assertEquals(42,p.affection);assertEquals(0,s.balance());
    }
    private FamilyState funded(){FamilyState s=new FamilyState();s.ledger.add(new FamilyState.Entry(s.day(1),"任务","task",10,1));return s;}
    @Test public void redemptionDebitsGrowthWalletWithoutTouchingKnowledgeStars(){FamilyState s=funded();PetState p=new PetState();p.stars=7;FamilyState.Gift g=s.redeem(p.speciesId,"snacks",1);GiftDelivery.apply(g,p);assertEquals(7,s.balance());assertEquals(7,p.stars);assertEquals(7,p.snacks);}
    @Test public void interruptedDeliveryRetriesWithoutDuplicatingFood(){FamilyState s=funded();PetState p=new PetState();FamilyState.Gift g=s.redeem(p.speciesId,"snacks",1);assertTrue(GiftDelivery.apply(g,p));assertFalse(GiftDelivery.apply(g,p));assertEquals(7,p.snacks);assertTrue(p.appliedGifts.contains(g.id));}
    @Test public void giftBelongsToThePetItWasBoughtFor(){FamilyState s=funded();PetState p=new PetState();FamilyState.Gift g=s.redeem("another_pet","party",1);assertFalse(GiftDelivery.apply(g,p));assertEquals(0,p.affection);}
    @Test(expected=IllegalArgumentException.class) public void negativeWalletCannotBuyGift(){FamilyState s=new FamilyState();s.ledger.add(new FamilyState.Entry(s.day(1),"用量","usage",-3,1));s.redeem("little_snake","snacks",1);}
    @Test public void completedDeliveryCannotBeGrantedToNewSaveAgain(){PetState p=new PetState();FamilyState.Gift g=new FamilyState.Gift(p.speciesId,"party");g.delivered=true;assertFalse(GiftDelivery.apply(g,p));assertEquals(0,p.affection);}
    @Test public void saltedPinRejectsWrongValueAndDoesNotStorePlainText(){FamilyState s=funded(),other=funded();ParentPin.set(s,"1357");ParentPin.set(other,"1357");assertTrue(ParentPin.verify(s,"1357"));assertFalse(ParentPin.verify(s,"1234"));assertFalse(s.pinHash.contains("1357"));assertNotEquals(s.pinSalt,other.pinSalt);assertNotEquals(s.pinHash,other.pinHash);}
}

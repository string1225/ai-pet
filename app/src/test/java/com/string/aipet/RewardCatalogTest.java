package com.string.aipet;

import com.string.aipet.learning.Subject;
import com.string.aipet.model.PetState;
import com.string.aipet.model.RewardCatalog;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewardCatalogTest {
    private PetState funded(){PetState p=new PetState();p.warmth=100;p.stars=30;p.learned.put(Subject.MATH,30);return p;}
    @Test public void knowledgePurchaseKeepsLearningHistory(){
        PetState p=funded();RewardCatalog.redeemKnowledge(p,"star_garland");
        assertEquals(24,p.stars);assertEquals(30,p.totalLearned());
        assertTrue(p.decorations.contains("star_garland"));assertTrue(p.equippedDecorations.contains("star_garland"));
    }
    @Test public void ownedDecorationCannotChargeAgain(){
        PetState p=funded();RewardCatalog.redeemKnowledge(p,"cloud_rug");
        try{RewardCatalog.redeemKnowledge(p,"cloud_rug");fail("duplicate purchase");}catch(IllegalArgumentException expected){}
        assertEquals(22,p.stars);
    }
    @Test public void insufficientKnowledgeBalanceCannotApplyReward(){
        PetState p=funded();p.stars=5;
        try{RewardCatalog.redeemKnowledge(p,"star_garland");fail("insufficient balance");}catch(IllegalArgumentException expected){}
        assertEquals(5,p.stars);assertTrue(p.decorations.isEmpty());
    }
    @Test public void growthFruitAdvancesExactlyOneLevelAndCanRepeat(){
        PetState p=funded();p.affection=17;int level=p.level();
        RewardCatalog.redeemKnowledge(p,"growth_fruit");assertEquals(level+1,p.level());assertEquals(47,p.affection);
        RewardCatalog.redeemKnowledge(p,"growth_fruit");assertEquals(level+2,p.level());assertEquals(10,p.stars);
    }
    @Test public void eggCannotBuyLevelBoost(){
        PetState p=funded();p.warmth=0;
        try{RewardCatalog.redeemKnowledge(p,"growth_fruit");fail("unhatched pet");}catch(IllegalArgumentException expected){}
        assertEquals(30,p.stars);assertEquals(0,p.affection);
    }
    @Test public void unknownRewardCannotDebitWallet(){
        PetState p=funded();
        try{RewardCatalog.redeemKnowledge(p,"unknown");fail("unknown reward");}catch(IllegalArgumentException expected){}
        assertEquals(30,p.stars);
    }
}

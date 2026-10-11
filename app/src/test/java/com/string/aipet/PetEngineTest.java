package com.string.aipet;

import com.string.aipet.learning.Subject;
import com.string.aipet.model.PetDefinition;
import com.string.aipet.model.PetEngine;
import com.string.aipet.model.PetRegistry;
import com.string.aipet.model.PetState;
import org.junit.Test;
import static org.junit.Assert.*;

public class PetEngineTest {
    private PetState born() { PetState s=new PetState();s.warmth=100;return s; }
    @Test public void fourSubjectsHatchEggAndPersistAdoptionTime() {
        PetState s=new PetState();PetEngine e=new PetEngine(s);
        for(Subject subject:Subject.values())e.correct(subject,12345);
        assertTrue(s.hatched());assertEquals(100,s.warmth);assertEquals(4,s.stars);
        assertEquals(4,s.totalLearned());assertEquals(12345,s.adoptedAt);
        e.correct(Subject.MATH,99999);assertEquals(12345,s.adoptedAt);
    }
    @Test public void foodConsumesStockAndClampsCareAt100() {
        PetState s=born();s.fullness=92;PetEngine e=new PetEngine(s);
        assertTrue(e.feed());assertEquals(4,s.snacks);assertEquals(100,s.fullness);
        assertFalse(e.feed());assertEquals(4,s.snacks);
    }
    @Test public void emptyStockDoesNotFeed() {PetState s=born();s.snacks=0;assertFalse(new PetEngine(s).feed());assertEquals(72,s.fullness);}
    @Test public void eggCannotBeFedPlayedOrPetted() {
        PetEngine e=new PetEngine(new PetState());assertFalse(e.feed());assertFalse(e.play());assertFalse(e.pet());assertFalse(e.water());
    }
    @Test public void sleepingBlocksCareAndPlay() {
        PetState s=born();s.sleeping=true;PetEngine e=new PetEngine(s);assertFalse(e.feed());assertFalse(e.play());assertFalse(e.pet());assertFalse(e.water());
    }
    @Test public void playRaisesMoodAndConsumesOnlySixEnergy() {
        PetState s=born();s.happiness=40;s.energy=40;PetEngine e=new PetEngine(s);
        assertTrue(e.play());assertEquals(58,s.happiness);assertEquals(34,s.energy);assertEquals(4,s.affection);
    }
    @Test public void lessonRewardsSnacksAndBond() {
        PetState s=born();new PetEngine(s).finishLesson();assertEquals(7,s.snacks);assertEquals(1,s.completedLessons);assertEquals(4,s.affection);
    }
    @Test public void sleepRecoversEnergyWhileAppIsClosed() {
        PetState s=born();s.energy=20;s.sleeping=true;s.lastUpdated=1000;
        new PetEngine(s).wake(1000+2*3600000L);assertEquals(44,s.energy);assertFalse(s.sleeping);
    }
    @Test public void longAbsenceIsGentleAndBounded() {
        PetState s=born();s.lastUpdated=1000;new PetEngine(s).tick(1000+100*3600000L);
        assertEquals(48,s.fullness);assertEquals(64,s.happiness);assertEquals(70,s.energy);
        s.lastUpdated=1000;s.fullness=21;s.happiness=26;s.energy=21;
        new PetEngine(s).tick(1000+100*3600000L);assertEquals(20,s.fullness);assertEquals(25,s.happiness);assertEquals(20,s.energy);
    }
    @Test public void clockGoingBackDoesNotChangeCare() {
        PetState s=born();s.lastUpdated=10000;new PetEngine(s).tick(1);assertEquals(72,s.fullness);assertEquals(10000,s.lastUpdated);
    }
    @Test public void subHourTicksDoNotDiscardElapsedTime() {
        PetState s=born();s.lastUpdated=1000;PetEngine e=new PetEngine(s);e.tick(1000+1800000);assertEquals(1000,s.lastUpdated);
        e.tick(1000+3600000);assertEquals(69,s.fullness);
    }
    @Test public void additionalSpeciesUsesItsOwnCareConfiguration() {
        PetRegistry.register(new PetDefinition("test_turtle","测试龟","团团","测试",0,10,8));
        PetState s=born();s.speciesId="test_turtle";s.fullness=30;s.happiness=30;
        PetEngine e=new PetEngine(s);e.feed();e.play();assertEquals(40,s.fullness);assertEquals(41,s.happiness);
    }
    @Test(expected=IllegalArgumentException.class) public void duplicateSpeciesIsRejected(){PetRegistry.register(PetRegistry.get(PetRegistry.SNAKE));}
    @Test public void progressAndCareNeverGoNegative(){PetState s=new PetState();s.warmth=-2;s.fullness=300;s.snacks=-1;s.stars=-5;s.clamp();assertEquals(0,s.warmth);assertEquals(100,s.fullness);assertEquals(0,s.snacks);assertEquals(0,s.stars);}
}

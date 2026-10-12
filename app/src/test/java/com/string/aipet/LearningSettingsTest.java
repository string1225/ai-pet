package com.string.aipet;

import com.string.aipet.learning.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LearningSettingsTest {
    @Test public void tenQuestionDefaultAllocatesTwoChineseModesFourMathAndTwoEnglish(){
        Map<Subject,Integer> a=new LearningSettings().allocations(10,new Random(1));
        assertEquals(Integer.valueOf(4),a.get(Subject.MATH));assertEquals(Integer.valueOf(2),a.get(Subject.PINYIN));assertEquals(Integer.valueOf(2),a.get(Subject.LITERACY));assertEquals(Integer.valueOf(2),a.get(Subject.ENGLISH));
    }
    @Test public void everyValidPercentagePlanPreservesTotalAndNeverUsesDisabledSubjects(){
        Random random=new Random(34);
        for(int cn=0;cn<=100;cn+=10)for(int math=0;math<=100-cn;math+=10)for(int pin:new int[]{0,25,50,75,100})for(int count:new int[]{1,3,4,5,10,20}){
            LearningSettings l=new LearningSettings();l.chinesePercent=cn;l.mathPercent=math;l.englishPercent=100-cn-math;l.pinyinPercent=pin;
            Map<Subject,Integer> a=l.allocations(count,random);assertEquals(count,a.values().stream().mapToInt(Integer::intValue).sum());
            for(Subject s:Subject.values()){assertTrue(a.get(s)>=0);if(l.weight(s)==0)assertEquals(Integer.valueOf(0),a.get(s));}
        }
    }
    @Test public void oneQuestionActivitiesSampleTheConfiguredMixture(){
        LearningSettings l=new LearningSettings();l.chinesePercent=0;l.mathPercent=80;l.englishPercent=20;Random random=new Random(22);int math=0;
        for(int i=0;i<5000;i++)math+=l.allocations(1,random).get(Subject.MATH);
        assertTrue(math>3850&&math<4150);
    }
    @Test public void smallNonzeroPercentagesAreNotPermanentlyRoundedOutOfTwentyQuestionRounds(){
        LearningSettings l=new LearningSettings();l.chinesePercent=1;l.mathPercent=98;l.englishPercent=1;Random random=new Random(70);int chinese=0,english=0;
        for(int i=0;i<5000;i++){Map<Subject,Integer> a=l.allocations(20,random);chinese+=a.get(Subject.PINYIN)+a.get(Subject.LITERACY);english+=a.get(Subject.ENGLISH);}
        assertTrue(chinese>800&&chinese<1200);assertTrue(english>800&&english<1200);
    }
    @Test(expected=IllegalArgumentException.class) public void incompletePercentagePlanRejected(){LearningSettings l=new LearningSettings();l.englishPercent=10;l.validate();}
    @Test(expected=IllegalArgumentException.class) public void allDisabledPlanRejected(){LearningSettings l=new LearningSettings();l.chinesePercent=0;l.mathPercent=0;l.englishPercent=0;l.validate();}
    @Test(expected=IllegalArgumentException.class) public void unsupportedDifficultyRejected(){LearningSettings l=new LearningSettings();l.mathDifficulty=3;l.validate();}
    @Test public void allMathLevelsHaveCorrectBoundedResultsAndFourDistinctChoices(){
        QuestionBank bank=new QuestionBank(new Random(61));
        for(int d=0;d<3;d++){LearningSettings l=new LearningSettings();l.mathDifficulty=d;boolean upperSeen=false;
            for(int run=0;run<25;run++)for(Question q:bank.session(Subject.MATH,20,l)){
                String[] parts=q.prompt.split(" ");int a=Integer.parseInt(parts[0]),b=Integer.parseInt(parts[2]),ans=Integer.parseInt(q.answer);
                assertTrue(a>=0&&a<=l.mathLimit()&&b>=0&&b<=l.mathLimit()&&ans>=0&&ans<=l.mathLimit());
                assertEquals(parts[1].equals("+")?a+b:a-b,ans);assertEquals(4,new HashSet<>(q.options).size());
                upperSeen|=ans==l.mathLimit();
            }assertTrue(upperSeen);
        }
    }
    @Test public void challengeModesActuallyChangeTheQuestionTask(){
        LearningSettings l=new LearningSettings();l.chineseDifficulty=2;l.englishDifficulty=2;QuestionBank bank=new QuestionBank(new Random(10));
        for(Question q:bank.session(Subject.PINYIN,12,l)){assertTrue(q.prompt.contains("声调"));assertEquals(4,new HashSet<>(q.options).size());assertTrue(q.options.contains(q.answer));}
        for(Question q:bank.session(Subject.LITERACY,12,l)){assertTrue(q.prompt.contains("（　）"));assertEquals(1,q.options.stream().filter(q.answer::equals).count());}
        for(Question q:bank.session(Subject.ENGLISH,16,l)){assertTrue(q.prompt.matches("[a-z]+"));assertFalse(q.answer.matches("[a-z]+"));assertEquals(4,new HashSet<>(q.options).size());}
    }
    @Test public void twentyQuestionSmallVocabularySessionsCompleteAndReviewRatherThanHang(){
        LearningSettings l=new LearningSettings();l.chinesePercent=100;l.mathPercent=0;l.englishPercent=0;l.pinyinPercent=0;l.chineseDifficulty=0;
        List<Question> q=new QuestionBank(new Random(9)).mixed(20,l);assertEquals(20,q.size());assertTrue(q.stream().allMatch(x->x.subject==Subject.LITERACY));
        assertEquals(8,q.stream().map(x->x.prompt).distinct().count());
    }
    @Test public void defaultIncubationKeepsFourSubjectsAndResumesItsRemainingSteps(){
        QuestionBank bank=new QuestionBank(new Random(99));LearningSettings l=new LearningSettings();
        List<Question> q=bank.incubation(50,l);assertEquals(2,q.size());assertEquals(Subject.LITERACY,q.get(0).subject);assertEquals(Subject.ENGLISH,q.get(1).subject);
        assertTrue(bank.incubation(100,l).isEmpty());
    }
    @Test public void customIncubationUsesOnlyConfiguredTopics(){
        LearningSettings l=new LearningSettings();l.chinesePercent=0;l.mathPercent=0;l.englishPercent=100;
        assertTrue(new QuestionBank().incubation(0,l).stream().allMatch(q->q.subject==Subject.ENGLISH));
    }
}

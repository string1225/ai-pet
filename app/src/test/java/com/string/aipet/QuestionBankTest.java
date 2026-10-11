package com.string.aipet;

import com.string.aipet.learning.*;
import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

public class QuestionBankTest {
    @Test public void generatedMathAlwaysStaysWithinTenAndHasCorrectAnswer() {
        QuestionBank bank=new QuestionBank(new Random(57));
        for(int i=0;i<5000;i++){
            Question q=bank.next(Subject.MATH);String[] parts=q.prompt.split(" ");
            int a=Integer.parseInt(parts[0]),b=Integer.parseInt(parts[2]),answer=Integer.parseInt(q.answer);
            assertTrue(a>=0&&a<=10&&b>=0&&b<=10&&answer>=0&&answer<=10);
            assertEquals(parts[1].equals("+")?a+b:a-b,answer);
            assertEquals(4,new HashSet<>(q.options).size());assertTrue(q.options.contains(q.answer));
        }
    }
    @Test public void everySubjectHasFourDistinctChoicesIncludingOneAnswer() {
        QuestionBank bank=new QuestionBank(new Random(87));
        for(Subject s:Subject.values())for(int i=0;i<500;i++){
            Question q=bank.next(s);assertEquals(s,q.subject);assertEquals(4,q.options.size());
            assertEquals(4,new HashSet<>(q.options).size());assertTrue(q.options.contains(q.answer));
            assertTrue(q.matches(q.answer));assertFalse(q.matches("definitely wrong"));assertFalse(q.speech.isEmpty());
        }
    }
    @Test public void shortSessionsDoNotRepeatPrompts() {
        QuestionBank bank=new QuestionBank(new Random(99));
        for(Subject s:Subject.values()){List<Question> qs=bank.session(s,3);assertEquals(3,qs.size());assertEquals(3,qs.stream().map(q->q.prompt).distinct().count());}
    }
    @Test public void canceledIncubationResumesOnlyRemainingSubjects() {
        QuestionBank bank=new QuestionBank(new Random(42));List<Question> qs=bank.incubation(50);
        assertEquals(2,qs.size());assertEquals(Subject.LITERACY,qs.get(0).subject);assertEquals(Subject.ENGLISH,qs.get(1).subject);
        assertTrue(bank.incubation(100).isEmpty());assertEquals(4,bank.incubation(0).size());
    }
    @Test(expected=IllegalArgumentException.class) public void oversizedSessionsAreRejectedInsteadOfLoopingForever(){new QuestionBank().session(Subject.PINYIN,100);}
    @Test public void choicesCannotBeModifiedByScreens(){Question q=new QuestionBank().next(Subject.ENGLISH);try{q.options.add("test");fail();}catch(UnsupportedOperationException expected){assertEquals(4,q.options.size());}}
}

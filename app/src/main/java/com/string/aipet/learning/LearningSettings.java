package com.string.aipet.learning;

import java.util.EnumMap;
import java.util.Random;

/** Parent-selected curriculum. Percentages affect every mixed activity; zero disables a topic. */
public final class LearningSettings {
    public int chineseDifficulty=1, mathDifficulty=1, englishDifficulty=1;
    public int chinesePercent=40, mathPercent=40, englishPercent=20, pinyinPercent=50, sessionLength=10;
    public void validate() {
        for(int n:new int[]{chineseDifficulty,mathDifficulty,englishDifficulty}) if(n<0||n>2) throw new IllegalArgumentException("难度应为启蒙、基础或挑战");
        for(int n:new int[]{chinesePercent,mathPercent,englishPercent,pinyinPercent}) if(n<0||n>100) throw new IllegalArgumentException("比例须在 0–100 之间");
        if(chinesePercent+mathPercent+englishPercent!=100) throw new IllegalArgumentException("语文、数学、英语的比例合计须为 100%");
        if(sessionLength!=5&&sessionLength!=10&&sessionLength!=20) throw new IllegalArgumentException("综合练习可选 5、10 或 20 题");
    }
    public int difficulty(Subject s) { return s==Subject.MATH?mathDifficulty:s==Subject.ENGLISH?englishDifficulty:chineseDifficulty; }
    public int mathLimit() { return mathDifficulty==0?5:mathDifficulty==1?10:20; }
    public double weight(Subject s) {
        if(s==Subject.MATH)return mathPercent;
        if(s==Subject.ENGLISH)return englishPercent;
        return chinesePercent*(s==Subject.PINYIN?pinyinPercent:100-pinyinPercent)/100.0;
    }
    public String description(Subject s) {
        int d=difficulty(s);
        if(s==Subject.MATH)return mathLimit()+" 以内加减法";
        if(s==Subject.PINYIN)return d==0?"单韵母拼读":d==1?"复韵母、鼻韵母与翘舌音":"四声辨读";
        if(s==Subject.LITERACY)return d==0?"常用字入门":d==1?"看拼音辨字形":"词语补字";
        return d==0?"常见词启蒙":d==1?"动物、颜色与日常词":"看英文选中文";
    }
    public EnumMap<Subject,Integer> allocations(int count,Random random) {
        validate();if(count<1||count>20)throw new IllegalArgumentException("回合应为 1–20 题");
        EnumMap<Subject,Integer> result=new EnumMap<>(Subject.class);
        for(Subject s:Subject.values())result.put(s,0);
        if(count==1){double pick=random.nextDouble()*100, upto=0;for(Subject s:Subject.values()){upto+=weight(s);if(pick<upto){result.put(s,1);return result;}}}
        int[] weights={chinesePercent,mathPercent,englishPercent}, quota=new int[3];double[] rem=new double[3];int assigned=0;
        for(int i=0;i<3;i++){double exact=count*weights[i]/100.0;quota[i]=(int)exact;rem[i]=exact-quota[i];assigned+=quota[i];}
        // Systematic randomized rounding retains exact quotas when possible, and makes
        // small nonzero percentages appear across rounds instead of rounding them away.
        double target=random.nextDouble(), upto=0;
        for(int i=0;i<3&&assigned<count;i++){upto+=rem[i];if(target<upto){quota[i]++;target+=1;assigned++;}}
        if(assigned<count){for(int i=2;i>=0;i--)if(weights[i]>0){quota[i]+=count-assigned;break;}}
        double pinExact=quota[0]*pinyinPercent/100.0;int pin=(int)pinExact;
        if(random.nextDouble()<pinExact-pin)pin++;
        result.put(Subject.PINYIN,pin);result.put(Subject.LITERACY,quota[0]-pin);
        result.put(Subject.MATH,quota[1]);result.put(Subject.ENGLISH,quota[2]);return result;
    }
}

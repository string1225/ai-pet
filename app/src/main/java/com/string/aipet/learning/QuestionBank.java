package com.string.aipet.learning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Small, reviewed first-grade bank; math is generated with all operands/results in [0, 10]. */
public final class QuestionBank {
    private final Random random;
    private static final String[][] PINYIN = {
        {"b + ā", "bā", "mā", "pā", "dā", "八"},
        {"m + ā", "mā", "bā", "tā", "lā", "妈"},
        {"d + à", "dà", "bà", "pà", "mà", "大"},
        {"t + ù", "tù", "dù", "nù", "lù", "兔"},
        {"m + ǐ", "mǐ", "nǐ", "lǐ", "bǐ", "米"},
        {"h + é", "hé", "kē", "gé", "mē", "河"},
        {"sh + ān", "shān", "sān", "chān", "zhān", "山"},
        {"sh + uǐ", "shuǐ", "suǐ", "chuǐ", "zhuǐ", "水"},
        {"ch + ē", "chē", "shē", "cē", "zhē", "车"},
        {"r + ì", "rì", "lì", "nì", "mì", "日"},
        {"l + ǜ", "lǜ", "lù", "nǜ", "lǘ", "绿"},
        {"n + iú", "niú", "liú", "jiǔ", "miú", "牛"}
    };
    private static final String[][] LITERACY = {
        {"rì", "太阳每天升起。", "日", "月", "目", "田"},
        {"yuè", "夜空中弯弯的月亮。", "月", "日", "用", "目"},
        {"shān", "高高的，有山峰。", "山", "川", "出", "口"},
        {"shuǐ", "口渴时喝一杯。", "水", "木", "火", "米"},
        {"huǒ", "红红的，能带来温暖。", "火", "人", "水", "大"},
        {"mù", "大树的树干。", "木", "本", "米", "禾"},
        {"rén", "你和我都是。", "人", "入", "八", "大"},
        {"kǒu", "用它吃饭、说话。", "口", "日", "田", "目"},
        {"shǒu", "我们用它拿东西。", "手", "毛", "牛", "午"},
        {"tián", "农民在这里种庄稼。", "田", "日", "目", "甲"},
        {"niú", "在草地吃草，哞哞叫。", "牛", "午", "羊", "手"},
        {"mǐ", "可以煮成香香的米饭。", "米", "木", "来", "禾"},
        {"dà", "小的反义词。", "大", "太", "天", "人"},
        {"xiǎo", "大的反义词。", "小", "少", "水", "木"},
        {"shàng", "下的反义词。", "上", "下", "土", "工"},
        {"xià", "上的反义词。", "下", "上", "不", "丁"}
    };
    private static final String[][] ENGLISH = {
        {"苹果", "apple", "banana", "cat", "dog"},
        {"香蕉", "banana", "apple", "fish", "bird"},
        {"猫", "cat", "dog", "bird", "fish"},
        {"狗", "dog", "cat", "sun", "moon"},
        {"鸟", "bird", "fish", "cat", "apple"},
        {"鱼", "fish", "bird", "dog", "book"},
        {"太阳", "sun", "moon", "star", "tree"},
        {"月亮", "moon", "sun", "book", "ball"},
        {"书", "book", "ball", "bird", "tree"},
        {"球", "ball", "book", "dog", "cat"},
        {"红色", "red", "blue", "green", "yellow"},
        {"蓝色", "blue", "red", "yellow", "green"},
        {"绿色", "green", "yellow", "red", "blue"},
        {"黄色", "yellow", "green", "blue", "red"},
        {"蛇", "snake", "fish", "bird", "cat"},
        {"水", "water", "milk", "apple", "book"}
    };
    public QuestionBank() { this(new Random()); }
    public QuestionBank(Random random) { this.random = random; }
    public List<Question> session(Subject subject, int count) {
        int capacity = subject == Subject.MATH ? 132 : subject == Subject.PINYIN ? PINYIN.length : subject == Subject.LITERACY ? LITERACY.length : ENGLISH.length;
        if (count < 0 || count > capacity) throw new IllegalArgumentException("Session exceeds question bank");
        List<Question> questions = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        while (questions.size() < count) {
            Question q = next(subject);
            if (seen.add(q.prompt)) questions.add(q);
        }
        return questions;
    }
    public List<Question> incubation(int warmth) {
        List<Question> questions = new ArrayList<>();
        for (int i = Math.min(4, warmth / 25); i < 4; i++) questions.add(next(Subject.values()[i]));
        return questions;
    }
    public Question next(Subject subject) {
        if (subject == Subject.MATH) {
            boolean add = random.nextBoolean();
            int a = random.nextInt(11), b = add ? random.nextInt(11 - a) : random.nextInt(a + 1);
            int result = add ? a + b : a - b;
            Set<String> choices = new LinkedHashSet<>(); choices.add("" + result);
            while (choices.size() < 4) choices.add("" + random.nextInt(11));
            List<String> options = new ArrayList<>(choices); Collections.shuffle(options, random);
            String symbol = add ? "+" : "−";
            return new Question(subject, a + " " + symbol + " " + b + " = ?", "帮小伙伴数一数", "" + result,
                options, "先数 " + a + " 个，再" + (add ? "添上 " : "拿走 ") + b + " 个，剩下多少个？",
                a + (add ? "加" : "减") + b + "等于几？");
        }
        String[][] bank = subject == Subject.PINYIN ? PINYIN : subject == Subject.LITERACY ? LITERACY : ENGLISH;
        String[] row = bank[random.nextInt(bank.length)];
        if (subject == Subject.PINYIN) return new Question(subject, row[0] + " → ?", "让声母和韵母成为朋友", row[1],
            shuffled(row, 1), "先轻轻读前面，再连着读后面。试试拼出「" + row[5] + "」的读音。", row[5]);
        if (subject == Subject.LITERACY) return new Question(subject, row[0], row[1], row[2], shuffled(row, 2),
            "读音是 " + row[0] + "。" + row[1] + "仔细看看每个字的形状。", row[2]);
        return new Question(subject, row[0], "选出它的英文单词", row[1], shuffled(row, 1),
            "「" + row[0] + "」的英文是 " + row[1] + "。读一读，再点一次。", row[1]);
    }
    private List<String> shuffled(String[] row, int start) {
        List<String> list = new ArrayList<>(Arrays.asList(row).subList(start, start + 4));
        Collections.shuffle(list, random); return list;
    }
}

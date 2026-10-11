package com.string.aipet.learning;

public enum Subject {
    MATH("小小数学家", "10 以内加减法", "算一算", 0xFFEAB976),
    PINYIN("拼音小火车", "声母 + 韵母，拼一拼", "拼一拼", 0xFF9BAED3),
    LITERACY("汉字花园", "看拼音、认汉字", "认一认", 0xFF91B995),
    ENGLISH("英语小世界", "动物、颜色与日常单词", "读一读", 0xFFD89C8E);
    public final String title, description, shortName;
    public final int color;
    Subject(String title, String description, String shortName, int color) {
        this.title = title; this.description = description; this.shortName = shortName; this.color = color;
    }
}

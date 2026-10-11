package com.string.aipet.learning;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public final class Question {
    public final Subject subject;
    public final String prompt, cue, answer, explanation, speech;
    public final List<String> options;
    public Question(Subject subject, String prompt, String cue, String answer, List<String> options,
                    String explanation, String speech) {
        this.subject = subject; this.prompt = prompt; this.cue = cue; this.answer = answer;
        this.options = Collections.unmodifiableList(new ArrayList<>(options)); this.explanation = explanation; this.speech = speech;
    }
    public boolean matches(String option) { return answer.equals(option); }
}

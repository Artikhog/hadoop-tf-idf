package ru.lab.tfidf.common;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Tokenizer {

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z]+");

    public static List<String> tokenize(String text) {
        return WORD_PATTERN.matcher(text)
                .results()
                .map(result -> result.group().toLowerCase(Locale.ROOT))
                .toList();
    }
}
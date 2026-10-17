package com.fongmi.android.tv.player.lrc;

import android.text.TextUtils;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcParser {

    private static final Pattern KTV_LINE_PATTERN = Pattern.compile("^\\[(\\d+),(\\d+)\\](.*)");
    private static final Pattern KTV_WORD_PATTERN = Pattern.compile("<(\\d+),(\\d+),\\d+>([^<>]*)");
    private static final Pattern LRC_TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\]");

    public static List<LrcRow> parse(String rawText) {
        List<LrcRow> rows = new ArrayList<>();
        if (TextUtils.isEmpty(rawText)) {
            return rows;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(rawText))) {
            String line;
            boolean isKtv = false;
            List<String> lines = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                lines.add(line);
                if (KTV_LINE_PATTERN.matcher(line).find()) {
                    isKtv = true;
                }
            }

            if (isKtv) {
                parseKtv(lines, rows);
            } else {
                parseLrc(lines, rows);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(rows);

        for (int i = 0; i < rows.size(); i++) {
            LrcRow current = rows.get(i);
            if (current.getDuration() <= 0) {
                if (i < rows.size() - 1) {
                    current.setDuration(rows.get(i + 1).getStartTime() - current.getStartTime());
                } else {
                    current.setDuration(5000);
                }
            }
        }

        return rows;
    }

    private static void parseKtv(List<String> lines, List<LrcRow> rows) {
        for (String line : lines) {
            if (line.startsWith("[language:")) {
                continue;
            }

            Matcher matcher = KTV_LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                long startTime = Long.parseLong(matcher.group(1));
                long duration = Long.parseLong(matcher.group(2));
                String contentPart = matcher.group(3);

                List<LrcRow.Word> words = new ArrayList<>();
                StringBuilder sb = new StringBuilder();
                Matcher wordMatcher = KTV_WORD_PATTERN.matcher(contentPart);
                while (wordMatcher.find()) {
                    long offset = Long.parseLong(wordMatcher.group(1));
                    long wordDur = Long.parseLong(wordMatcher.group(2));
                    String text = wordMatcher.group(3);
                    words.add(new LrcRow.Word(offset, wordDur, text));
                    sb.append(text);
                }

                String content = sb.length() > 0 ? sb.toString() : contentPart;
                rows.add(new LrcRow(startTime, duration, content, words));
            }
        }
    }

    private static void parseLrc(List<String> lines, List<LrcRow> rows) {
        for (String line : lines) {
            Matcher matcher = LRC_TIME_PATTERN.matcher(line);
            List<Long> times = new ArrayList<>();
            int lastIndex = 0;
            while (matcher.find()) {
                long min = Long.parseLong(matcher.group(1));
                long sec = Long.parseLong(matcher.group(2));
                long mil = Long.parseLong(matcher.group(3));
                if (matcher.group(3).length() == 2) {
                    mil *= 10;
                }
                long time = min * 60 * 1000 + sec * 1000 + mil;
                times.add(time);
                lastIndex = matcher.end();
            }

            if (!times.isEmpty()) {
                String content = line.substring(lastIndex).trim();
                for (long time : times) {
                    rows.add(new LrcRow(time, 0, content, null));
                }
            }
        }
    }
}

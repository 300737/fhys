package com.fongmi.android.tv.player.lrc;

import java.util.List;

public class LrcRow implements Comparable<LrcRow> {

    private long startTime;
    private long duration;
    private String content;
    private List<Word> words;

    public LrcRow(long startTime, long duration, String content, List<Word> words) {
        this.startTime = startTime;
        this.duration = duration;
        this.content = content;
        this.words = words;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getContent() {
        return content != null ? content : "";
    }

    public List<Word> getWords() {
        return words;
    }

    @Override
    public int compareTo(LrcRow other) {
        return Long.compare(this.startTime, other.startTime);
    }

    public static class Word {
        private long offset;
        private long duration;
        private String text;

        public Word(long offset, long duration, String text) {
            this.offset = offset;
            this.duration = duration;
            this.text = text;
        }

        public long getOffset() {
            return offset;
        }

        public long getDuration() {
            return duration;
        }

        public String getText() {
            return text != null ? text : "";
        }
    }
}

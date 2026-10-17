package com.fongmi.android.tv.utils;

import android.text.TextUtils;
import com.fongmi.android.tv.bean.Episode;
import java.util.*;
import java.util.regex.*;

public class EpisodeNameSimplifier {
    
    private static final List<Pattern> MEDIA_KEYWORDS_RE = Arrays.asList(
        Pattern.compile("(?i)^(19|20)\\d{2}$"),
        Pattern.compile("(?i)^\\d+(p|i|k)$"),
        Pattern.compile("(?i)^\\d+bit$"),
        Pattern.compile("(?i)^\\d+audio$"),
        Pattern.compile("(?i)^(h|x|hevc|avc)?\\d{3,4}$"),
        Pattern.compile("(?i)^(aac|ac3|dd|dts|truehd|flac|mp3|eac3).*$"),
        Pattern.compile("(?i)^(web[-_]?dl|bluray|blu[-_]?ray|hdtv|web[-_]?rip|dvdrip|web)$"),
        Pattern.compile("(?i)^(mp4|mkv|avi|flv|wmv)$")
    );
    
    private static final Set<String> NOISE_WORDS = new HashSet<>(Arrays.asList("5", "1", "ch", "dts"));
    
    private static final List<String> ALLOWED_EXTS = Arrays.asList(
        ".mp4", ".mkv", ".avi", ".wmv", ".flv", ".mov", ".ts", ".strm", ".srt", ".ass", ".nfo", ".jpg", ".png"
    );
    
    private static boolean isMediaMetadata(String token) {
        String lower = token.toLowerCase();
        if (NOISE_WORDS.contains(lower)) return true;
        for (Pattern p : MEDIA_KEYWORDS_RE) {
            if (p.matcher(lower).matches()) return true;
        }
        return false;
    }
    
    private static String[] extractExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx == -1) return new String[]{filename, ""};
        String name = filename.substring(0, idx);
        String ext = filename.substring(idx);
        if (ALLOWED_EXTS.contains(ext.toLowerCase())) {
            return new String[]{name, ext};
        }
        return new String[]{filename, ""};
    }
    
    private static class AnchorInfo {
        String anchor;
        int start;
        int end;
        AnchorInfo(String anchor, int start, int end) {
            this.anchor = anchor;
            this.start = start;
            this.end = end;
        }
    }
    
    private static AnchorInfo findEpisodeAnchor(String filename) {
        // 1. SxxExx
        Matcher m = Pattern.compile("(?i)S\\d+[\\s\\._\\-]?E(?:p|isode)?\\s*\\d+").matcher(filename);
        if (m.find()) return new AnchorInfo(m.group(), m.start(), m.end());
        // 2. Exx
        m = Pattern.compile("(?i)E(?:p|isode)?\\s*\\d+").matcher(filename);
        if (m.find()) return new AnchorInfo(m.group(), m.start(), m.end());
        // 3. 中文集数
        m = Pattern.compile("第\\s*[0-9零一二三四五六七八九十百千两壹贰叁肆伍陆柒捌玖拾佰仟]+\\s*[集期话章回]").matcher(filename);
        if (m.find()) return new AnchorInfo(m.group(), m.start(), m.end());
        // 4. 独立数字
        m = Pattern.compile("(?:[^a-zA-Z0-9\\u4e00-\\u9fa5]|^)(\\d+)(?:[^a-zA-Z0-9\\u4e00-\\u9fa5]|$)").matcher(filename);
        if (m.find()) return new AnchorInfo(m.group(1), m.start(1), m.end(1));
        // 5. 行尾数字
        m = Pattern.compile("\\d+$").matcher(filename);
        if (m.find()) return new AnchorInfo(m.group(), m.start(), m.end());
        return null;
    }
    
    private static List<String> tokenizeSuffix(String suffixStr) {
        String[] split = suffixStr.split("[^a-zA-Z0-9\\u4e00-\\u9fa5]+");
        List<String> list = new ArrayList<>();
        for (String s : split) {
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }
    
    public static void simplify(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return;
        
        class Parsed {
            Episode ep;
            String original;
            String anchor;
            String ext;
            List<String> tokens;
        }
        
        List<Parsed> parsedList = new ArrayList<>();
        List<List<String>> suffixTokenLists = new ArrayList<>();
        
        for (Episode ep : episodes) {
            if (ep == null) continue;
            String orig = ep.getName();
            if (TextUtils.isEmpty(orig)) continue;
            
            String[] extRes = extractExtension(orig);
            String baseName = extRes[0];
            String ext = extRes[1];
            
            AnchorInfo anchorInfo = findEpisodeAnchor(baseName);
            Parsed p = new Parsed();
            p.ep = ep;
            p.original = orig;
            p.ext = ext;
            
            if (anchorInfo == null) {
                p.anchor = "";
                p.tokens = tokenizeSuffix(baseName);
            } else {
                p.anchor = anchorInfo.anchor;
                String suffix = baseName.substring(anchorInfo.end);
                p.tokens = tokenizeSuffix(suffix);
                suffixTokenLists.add(p.tokens);
            }
            parsedList.add(p);
        }
        
        Set<String> commonSuffixTokens = new HashSet<>();
        if (!suffixTokenLists.isEmpty()) {
            Set<String> commonCandidates = new HashSet<>(suffixTokenLists.get(0));
            for (int i = 1; i < suffixTokenLists.size(); i++) {
                commonCandidates.retainAll(suffixTokenLists.get(i));
            }
            for (String t : commonCandidates) {
                if (!isMediaMetadata(t)) {
                    commonSuffixTokens.add(t);
                }
            }
        }
        
        for (Parsed p : parsedList) {
            if (p.anchor.isEmpty()) {
                p.ep.setCleanName(p.original);
                continue;
            }
            List<String> titleTokens = new ArrayList<>();
            for (String t : p.tokens) {
                if (isMediaMetadata(t) || commonSuffixTokens.contains(t)) {
                    continue;
                }
                titleTokens.add(t);
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < titleTokens.size(); i++) {
                if (i > 0) sb.append(" ");
                sb.append(titleTokens.get(i));
            }
            String title = sb.toString().trim();
            String simplified = title.isEmpty() ? p.anchor + p.ext : p.anchor + " - " + title + p.ext;
            p.ep.setCleanName(simplified);
        }
    }
}

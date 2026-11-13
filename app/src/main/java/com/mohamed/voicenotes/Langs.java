package com.mohamed.voicenotes;

import com.google.mlkit.nl.translate.TranslateLanguage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** قائمة اللغات وأسمائها وأكوادها. */
public class Langs {

    private static List<String> cache;

    public static synchronized List<String> all() {
        if (cache == null) {
            List<String> l = new ArrayList<String>(TranslateLanguage.getAllLanguages());
            Collections.sort(l, new Comparator<String>() {
                public int compare(String a, String b) {
                    int ra = rank(a), rb = rank(b);
                    if (ra != rb) return ra - rb;
                    return arName(a).compareTo(arName(b));
                }
            });
            cache = l;
        }
        return cache;
    }

    private static int rank(String c) {
        if ("ar".equals(c)) return 0;
        if ("en".equals(c)) return 1;
        return 2;
    }

    public static int indexOf(String code) {
        int i = all().indexOf(code);
        return i < 0 ? 0 : i;
    }

    public static String arName(String code) {
        Locale l = Locale.forLanguageTag(code);
        String n = l.getDisplayLanguage(new Locale("ar"));
        if (n == null || n.length() == 0) n = code;
        return n;
    }

    /** الاسم بالعربي + الاسم بلغته الأصلية */
    public static String name(String code) {
        Locale l = Locale.forLanguageTag(code);
        String ar = arName(code);
        String nat = l.getDisplayLanguage(l);
        if (nat == null || nat.length() == 0 || nat.equals(ar)) return ar;
        return ar + " — " + nat;
    }

    /** كود اللغة اللي بيفهمه التعرف على الكلام والنطق */
    public static String sttTag(String code) {
        if ("zh".equals(code)) return "zh-CN";
        if ("pt".equals(code)) return "pt-BR";
        return code;
    }

    public static Locale locale(String code) {
        return Locale.forLanguageTag(sttTag(code));
    }
}

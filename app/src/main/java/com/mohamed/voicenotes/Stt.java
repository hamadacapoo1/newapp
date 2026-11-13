package com.mohamed.voicenotes;

/** واجهة موحّدة لمحركات تحويل الصوت لنص (محرك الجهاز / موديل Vosk). كل الدوال بتتنادى من الـ UI thread. */
public interface Stt {

    interface Listener {
        /** المايك جاهز، اتكلم */
        void onReady();

        /** نص مؤقت أثناء الكلام */
        void onPartial(String text);

        /** جملة نهائية */
        void onFinal(String text);

        /** حصلت مشكلة والجلسة وقفت */
        void onError(String message);
    }

    void start(String langCode, Listener l);

    /** إيقاف هادي: آخر نتيجة لسه ممكن توصل بعد الإيقاف */
    void stop();

    /** إغلاق فوري وتنضيف كل حاجة */
    void release();

    boolean isActive();
}

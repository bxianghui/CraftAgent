package com.selfagent.selfcheck;

public class VerdictParser {
    public enum Verdict { PASS, FAIL, PARTIAL, UNKNOWN }

    public static Verdict parse(String output) {
        if (output == null) return Verdict.UNKNOWN;
        if (output.contains("VERDICT: PASS")) return Verdict.PASS;
        if (output.contains("VERDICT: FAIL")) return Verdict.FAIL;
        if (output.contains("VERDICT: PARTIAL")) return Verdict.PARTIAL;
        return Verdict.UNKNOWN;
    }

    public static String formatTaskNotification(String result, Verdict verdict) {
        return "<task-notification>\n" +
            "<type>verification</type>\n" +
            "<verdict>" + verdict.name() + "</verdict>\n" +
            "<result>\n" + result + "\n</result>\n" +
            "</task-notification>\n\n" +
            (verdict == Verdict.FAIL
                ? "[Verification FAILED. Review the issues above and fix them before proceeding.]"
                : "");
    }
}

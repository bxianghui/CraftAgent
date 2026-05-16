package com.selfagent.rag;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class DocumentLoader {
    private static final OkHttpClient http = new OkHttpClient();
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\n{3,}");

    public String loadFile(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            return loadPdf(path);
        }
        return Files.readString(path);
    }

    public String loadUrl(String url) throws IOException {
        Request req = new Request.Builder().url(url)
            .addHeader("User-Agent", "coding-agent/1.0").build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + ": " + url);
            String body = resp.body().string();
            String text = HTML_TAGS.matcher(body).replaceAll(" ");
            return MULTI_NEWLINE.matcher(text).replaceAll("\n\n").trim();
        }
    }

    private String loadPdf(Path path) throws IOException {
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(doc).trim();
        } catch (Exception e) {
            throw new IOException("PDF parse failed: " + e.getMessage(), e);
        }
    }
}

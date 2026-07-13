package com.dropai.rewrite.service.writing;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class GbT7714Formatter {
    public String format(int index, ReferenceCandidate candidate, String style) {
        String authors = formatAuthors(candidate.authors());
        String mark = documentMark(candidate.documentType());
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(index).append("] ")
                .append(authors)
                .append(authors.isBlank() ? "" : ". ")
                .append(candidate.title())
                .append("[")
                .append(mark)
                .append("]. ");
        if ("JOURNAL".equalsIgnoreCase(candidate.documentType())) {
            builder.append(candidate.container());
            if (candidate.year() != null) builder.append(", ").append(candidate.year());
            if (!blank(candidate.volume())) builder.append(", ").append(candidate.volume());
            if (!blank(candidate.issue())) builder.append("(").append(candidate.issue()).append(")");
            if (!blank(candidate.pages())) builder.append(":").append(candidate.pages());
            builder.append(".");
        } else if ("THESIS".equalsIgnoreCase(candidate.documentType())) {
            builder.append(candidate.container());
            if (candidate.year() != null) builder.append(", ").append(candidate.year());
            builder.append(".");
        } else if ("ONLINE".equalsIgnoreCase(candidate.documentType())) {
            builder.append(candidate.year() == null ? "" : candidate.year())
                    .append("[")
                    .append(LocalDate.now())
                    .append("]. ")
                    .append(candidate.url())
                    .append(".");
        } else {
            builder.append(candidate.container());
            if (candidate.year() != null) builder.append(", ").append(candidate.year());
            builder.append(".");
        }
        if (!blank(candidate.doi())) builder.append(" DOI: ").append(candidate.doi()).append(".");
        if (!blank(candidate.url()) && !"ONLINE".equalsIgnoreCase(candidate.documentType())) builder.append(" ").append(candidate.url()).append(".");
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    public boolean formatIncomplete(ReferenceCandidate candidate) {
        if (blank(candidate.title()) || candidate.authors() == null || candidate.authors().isEmpty()
                || candidate.year() == null || blank(candidate.container()) || blank(candidate.url())) return true;
        return "JOURNAL".equalsIgnoreCase(candidate.documentType()) && (blank(candidate.volume()) || blank(candidate.issue()) || blank(candidate.pages()));
    }

    private String formatAuthors(List<String> authors) {
        if (authors == null || authors.isEmpty()) return "";
        return String.join(", ", authors);
    }

    private String documentMark(String documentType) {
        if ("THESIS".equalsIgnoreCase(documentType)) return "D";
        if ("BOOK".equalsIgnoreCase(documentType)) return "M";
        if ("CONFERENCE".equalsIgnoreCase(documentType)) return "C";
        if ("REPORT".equalsIgnoreCase(documentType)) return "R";
        if ("STANDARD".equalsIgnoreCase(documentType)) return "S";
        if ("PATENT".equalsIgnoreCase(documentType)) return "P";
        if ("ONLINE".equalsIgnoreCase(documentType)) return "EB/OL";
        return "J";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

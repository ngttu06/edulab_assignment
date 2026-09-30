package vn.edu.khtn8.content.dto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record CatalogFilter(Long subjectId, Integer grade, Long courseId, String q) {
    public CatalogFilter {
        q = q == null ? "" : q.trim();
        if (subjectId != null && subjectId <= 0 || courseId != null && courseId <= 0
                || grade != null && (grade < 1 || grade > 12) || q.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc không hợp lệ.");
    }
    public static CatalogFilter all() { return new CatalogFilter(null, null, null, ""); }
    // Escape LIKE wildcards: search text is literal, not SQL pattern syntax.
    public String pattern() { return "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"; }
    public Object[] parameters() { return new Object[]{subjectId, subjectId, grade, grade, courseId, courseId, pattern()}; }
    public static final String SQL = """
            AND (? IS NULL OR s.id=?) AND (? IS NULL OR s.grade_level=?)
            AND (? IS NULL OR c.id=?)
            """;
}
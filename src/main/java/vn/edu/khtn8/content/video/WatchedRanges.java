package vn.edu.khtn8.content.video;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.content.video.VideoModels.Range;

public final class WatchedRanges {
    private WatchedRanges() { }
    public static List<Range> merge(List<Range> existing, List<Range> incoming, long durationMs) {
        var sorted = new ArrayList<Range>();
        sorted.addAll(existing); sorted.addAll(incoming);
        for (var range : sorted) {
            if (range == null || range.startMs() < 0 || range.endMs() <= range.startMs() || range.endMs() > durationMs)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng thời gian xem không hợp lệ.");
        }
        sorted.sort(Comparator.comparingLong(Range::startMs));
        var merged = new ArrayList<Range>();
        for (var range : sorted) {
            if (!merged.isEmpty() && range.startMs() <= merged.getLast().endMs()) {
                var last = merged.removeLast();
                merged.add(new Range(last.startMs(), Math.max(last.endMs(), range.endMs())));
            } else merged.add(range);
        }
        if (merged.size() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Có quá nhiều đoạn xem rời rạc. Hãy xem nối tiếp các đoạn còn thiếu.");
        return List.copyOf(merged);
    }
    public static long duration(List<Range> ranges) {
        return ranges.stream().mapToLong(r -> r.endMs() - r.startMs()).sum();
    }
    public static int percent(List<Range> ranges, long durationMs) {
        return (int) Math.min(100, duration(ranges) * 100 / durationMs);
    }
}
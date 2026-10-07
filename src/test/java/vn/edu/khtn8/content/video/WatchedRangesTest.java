package vn.edu.khtn8.content.video;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.khtn8.content.video.VideoModels.*;

class WatchedRangesTest {
    @Test void disjointSegmentsDoNotFillSeekGaps() {
        var ranges=WatchedRanges.merge(List.of(new Range(0,120000)),List.of(new Range(300000,420000)),600000);
        assertEquals(List.of(new Range(0,120000),new Range(300000,420000)),ranges);
        assertEquals(240000,WatchedRanges.duration(ranges));
        assertEquals(40,WatchedRanges.percent(ranges,600000));
    }
    @Test void overlappingRepeatedAndAdjacentSegmentsAreUnionedExactly() {
        var ranges=WatchedRanges.merge(List.of(new Range(0,1000),new Range(3000,4000)),
                List.of(new Range(500,1500),new Range(1500,3000),new Range(500,1500)),10000);
        assertEquals(List.of(new Range(0,4000)),ranges);
        assertEquals(40,WatchedRanges.percent(ranges,10000));
    }
    @Test void rejectsNegativeReversedAndOutOfBoundsSegments() {
        for (var range:List.of(new Range(-1,10),new Range(10,10),new Range(20,10),new Range(0,10001)))
            assertThrows(ResponseStatusException.class,()->WatchedRanges.merge(List.of(),List.of(range),10000));
        assertEquals(0,WatchedRanges.percent(List.of(),10000));
    }
}
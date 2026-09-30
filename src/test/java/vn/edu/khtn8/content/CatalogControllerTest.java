package vn.edu.khtn8.content;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import vn.edu.khtn8.content.dto.CatalogFilter;
import vn.edu.khtn8.content.service.CatalogService;
import vn.edu.khtn8.content.service.LearningPathService;
import vn.edu.khtn8.content.web.CatalogController;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogControllerTest {
    private final CatalogService catalog = mock(CatalogService.class);
    private final LearningPathService path = mock(LearningPathService.class);
    private final CatalogController controller = new CatalogController(catalog, path);

    @Test void anonymousListUsesPublicProgressAndPassesFiltersToView() {
        var filter = new CatalogFilter(2L, 8, null, "Toán");
        var courses = List.<Map<String,Object>>of(Map.of("id", 3L, "title", "Toán 8"));
        when(catalog.courses(0, filter)).thenReturn(courses);
        when(catalog.subjects()).thenReturn(List.of(Map.of("id", 2L, "name", "Toán", "grade_level", 8)));
        when(catalog.grades()).thenReturn(List.of(8, 10));
        var model = new ExtendedModelMap();
        assertEquals("layout/default", controller.list(filter, model, null));
        assertEquals("/WEB-INF/jsp/catalog/list.jsp", model.get("contentPage"));
        assertSame(courses, model.get("courses"));
        assertEquals(List.of(8, 10), model.get("grades"));
        verify(catalog).courses(0, filter);
    }

    @Test void detailLoadsOnlyRequestedCourseCurriculum() {
        var course = Map.<String,Object>of("id", 3L, "title", "Toán 8");
        var steps = List.<Map<String,Object>>of(Map.of("id", 9L, "title", "Bài học", "unlocked", true));
        when(catalog.course(0, 3)).thenReturn(course);
        when(path.steps(0, new CatalogFilter(null, null, 3L, ""))).thenReturn(steps);
        var model = new ExtendedModelMap();
        assertEquals("layout/default", controller.detail(3, model, null));
        assertEquals("/WEB-INF/jsp/catalog/detail.jsp", model.get("contentPage"));
        assertSame(course, model.get("course"));
        assertSame(steps, model.get("steps"));
    }

    @Test void filtersTreatWildcardsAsLiteralTextAndRejectInvalidValues() {
        assertEquals("%100!% !_ !!=%", new CatalogFilter(null, null, null, " 100% _ != ").pattern());
        assertEquals("", CatalogFilter.all().q());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> new CatalogFilter(0L, 8, null, ""));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> new CatalogFilter(null, 13, null, ""));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> new CatalogFilter(null, null, -1L, ""));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> new CatalogFilter(null, null, null, "x".repeat(101)));
    }
}
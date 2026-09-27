package vn.career.catalog.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.career.catalog.api.dto.MajorCompareResponse;
import vn.career.catalog.api.dto.MajorDetailResponse;
import vn.career.catalog.api.dto.MajorSummaryResponse;
import vn.career.catalog.api.dto.UniversityResponse;
import vn.career.catalog.application.CatalogService;
import vn.career.common.api.ApiResponse;
import vn.career.common.api.PageResponse;

/** Public catalog: no login needed. Data flagged {@code sampleData} is made up and must not be shown as real. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Catalog (public)")
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/majors")
    public ApiResponse<PageResponse<MajorSummaryResponse>> majors(@RequestParam(required = false) String group,
                                                                  @RequestParam(required = false) String combo,
                                                                  @RequestParam(required = false) String q,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(catalogService.searchMajors(group, combo, q, page, size));
    }

    /** Compares up to 3 majors: {@code /majors/compare?ids=a,b,c}. */
    @GetMapping("/majors/compare")
    public ApiResponse<MajorCompareResponse> compare(@RequestParam List<UUID> ids) {
        return ApiResponse.ok(catalogService.compare(ids));
    }

    @GetMapping("/majors/{id}")
    public ApiResponse<MajorDetailResponse> major(@PathVariable UUID id) {
        return ApiResponse.ok(catalogService.getMajor(id));
    }

    @GetMapping("/universities")
    public ApiResponse<List<UniversityResponse>> universities(@RequestParam(required = false) String region) {
        return ApiResponse.ok(catalogService.listUniversities(region));
    }
}

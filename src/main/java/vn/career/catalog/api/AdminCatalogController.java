package vn.career.catalog.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.career.catalog.api.dto.MajorAdminResponse;
import vn.career.catalog.api.dto.MajorRequest;
import vn.career.catalog.api.dto.ProfileRequest;
import vn.career.catalog.api.dto.UniversityMajorRequest;
import vn.career.catalog.api.dto.UniversityMajorResponse;
import vn.career.catalog.api.dto.UniversityRequest;
import vn.career.catalog.api.dto.UniversityResponse;
import vn.career.catalog.application.CatalogAdminService;
import vn.career.common.api.ApiResponse;
import vn.career.common.api.PageRequests;
import vn.career.common.api.PageResponse;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - catalog")
public class AdminCatalogController {

    private final CatalogAdminService service;

    // ---------------------------------------------------------------- majors

    @GetMapping("/majors")
    public ApiResponse<PageResponse<MajorAdminResponse>> listMajors(@RequestParam(required = false) String q,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.listMajors(q, PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/majors/{id}")
    public ApiResponse<MajorAdminResponse> getMajor(@PathVariable UUID id) {
        return ApiResponse.ok(service.getMajor(id));
    }

    @PostMapping("/majors")
    public ResponseEntity<ApiResponse<MajorAdminResponse>> createMajor(@Valid @RequestBody MajorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createMajor(request)));
    }

    @PutMapping("/majors/{id}")
    public ApiResponse<MajorAdminResponse> updateMajor(@PathVariable UUID id, @Valid @RequestBody MajorRequest request) {
        return ApiResponse.ok(service.updateMajor(id, request));
    }

    /** Deactivates the major (it disappears from the public catalog and from recommendations). */
    @DeleteMapping("/majors/{id}")
    public ApiResponse<Void> deactivateMajor(@PathVariable UUID id) {
        service.deactivateMajor(id);
        return ApiResponse.ok(null);
    }

    /** Replaces the dimension weights (0..1) the matcher uses for this major. */
    @PutMapping("/majors/{id}/profile")
    public ApiResponse<MajorAdminResponse> setProfile(@PathVariable UUID id, @Valid @RequestBody ProfileRequest request) {
        return ApiResponse.ok(service.setProfile(id, request));
    }

    // ---------------------------------------------------------------- universities

    @GetMapping("/universities")
    public ApiResponse<PageResponse<UniversityResponse>> listUniversities(@RequestParam(required = false) Integer page,
                                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.listUniversities(PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/universities")
    public ResponseEntity<ApiResponse<UniversityResponse>> createUniversity(@Valid @RequestBody UniversityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createUniversity(request)));
    }

    @PutMapping("/universities/{id}")
    public ApiResponse<UniversityResponse> updateUniversity(@PathVariable UUID id,
                                                            @Valid @RequestBody UniversityRequest request) {
        return ApiResponse.ok(service.updateUniversity(id, request));
    }

    @DeleteMapping("/universities/{id}")
    public ApiResponse<Void> deleteUniversity(@PathVariable UUID id) {
        service.deleteUniversity(id);
        return ApiResponse.ok(null);
    }

    // ---------------------------------------------------------------- admission data

    @GetMapping("/university-majors")
    public ApiResponse<PageResponse<UniversityMajorResponse>> listUniversityMajors(
            @RequestParam(required = false) UUID majorId, @RequestParam(required = false) UUID universityId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.listUniversityMajors(majorId, universityId,
                PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/university-majors")
    public ResponseEntity<ApiResponse<UniversityMajorResponse>> createUniversityMajor(
            @Valid @RequestBody UniversityMajorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createUniversityMajor(request)));
    }

    @PutMapping("/university-majors/{id}")
    public ApiResponse<UniversityMajorResponse> updateUniversityMajor(@PathVariable UUID id,
                                                                      @Valid @RequestBody UniversityMajorRequest request) {
        return ApiResponse.ok(service.updateUniversityMajor(id, request));
    }

    @DeleteMapping("/university-majors/{id}")
    public ApiResponse<Void> deleteUniversityMajor(@PathVariable UUID id) {
        service.deleteUniversityMajor(id);
        return ApiResponse.ok(null);
    }
}

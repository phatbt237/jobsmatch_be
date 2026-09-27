package vn.career.catalog.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

/** Replaces the whole profile: dimension code to weight 0..1. Dimensions left out get no weight. */
public record ProfileRequest(
        @NotNull @Size(max = 40) Map<String, @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal> weights) {
}

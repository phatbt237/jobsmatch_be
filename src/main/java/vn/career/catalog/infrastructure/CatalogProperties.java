package vn.career.catalog.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.catalog")
public record CatalogProperties(@DefaultValue("1h") Duration cacheTtl) {
}

package vn.career.scoring.infrastructure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.career.scoring.application.DimensionResult;
import vn.career.scoring.domain.DimensionScore;

/** Reads and writes the attempt_scores table with plain SQL (a composite-key table with no behaviour of its own). */
@Repository
@RequiredArgsConstructor
public class AttemptScoreStore {

    private final JdbcTemplate jdbc;

    /** Replaces all stored scores of the attempt. */
    public void replace(UUID attemptId, List<DimensionScore> scores) {
        jdbc.update("delete from attempt_scores where attempt_id = ?", attemptId);
        jdbc.batchUpdate("insert into attempt_scores (attempt_id, dimension_code, raw, normalized) values (?, ?, ?, ?)",
                scores, scores.size(), (ps, score) -> {
                    ps.setObject(1, attemptId);
                    ps.setString(2, score.dimension());
                    ps.setBigDecimal(3, BigDecimal.valueOf(score.raw()).setScale(4, RoundingMode.HALF_UP));
                    ps.setBigDecimal(4, BigDecimal.valueOf(score.normalized()).setScale(6, RoundingMode.HALF_UP));
                });
    }

    public List<DimensionResult> find(UUID attemptId) {
        return jdbc.query("select dimension_code, raw, normalized from attempt_scores "
                        + "where attempt_id = ? order by dimension_code",
                (rs, i) -> new DimensionResult(rs.getString(1), rs.getDouble(2), rs.getDouble(3)), attemptId);
    }
}

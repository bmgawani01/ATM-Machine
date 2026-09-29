package atm.core.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of {@code atm_cash}: how many notes of each denomination the machine holds. */
public record AtmCash(int denom, int notes, LocalDateTime updatedAt) {

    public BigDecimal value() {
        return BigDecimal.valueOf((long) denom * notes).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}

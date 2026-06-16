package com.qzcy.backend.dto.relay;

import java.math.BigDecimal;

public record RelayCostBreakdown(
        BigDecimal input,
        BigDecimal output,
        BigDecimal cacheRead,
        BigDecimal cacheCreation,
        BigDecimal request,
        BigDecimal total
) {
    public boolean billable() {
        return total != null && total.compareTo(BigDecimal.ZERO) > 0;
    }
}

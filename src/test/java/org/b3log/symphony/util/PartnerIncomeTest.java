package org.b3log.symphony.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public final class PartnerIncomeTest {
    private static final long HOUR = 3600000L, MIDNIGHT = 16 * HOUR;

    @Test public void offlineAcrossSeveralDays() {
        var m = PartnerIncome.meter(MIDNIGHT - HOUR).put("rate", 40);
        long now = MIDNIGHT + 49 * HOUR;
        Assert.assertEquals(PartnerIncome.settle(m, now), (1 + 8 + 8 + 1) * 40);
        Assert.assertEquals(m.getLong("today"), 40);
        Assert.assertEquals(m.getLong("used"), HOUR);
        Assert.assertEquals(PartnerIncome.settle(m, now), 0);
    }

    @Test public void slotBudgetSurvivesChangingPartnerAndIdleTime() {
        var m = PartnerIncome.meter(MIDNIGHT).put("rate", 40);
        Assert.assertEquals(PartnerIncome.settle(m, MIDNIGHT + 7 * HOUR), 280);
        m.put("rate", 0);
        Assert.assertEquals(PartnerIncome.settle(m, MIDNIGHT + 10 * HOUR), 0);
        m.put("rate", 50);
        Assert.assertEquals(PartnerIncome.settle(m, MIDNIGHT + 15 * HOUR), 50);
        Assert.assertEquals(m.getLong("today"), 330);
        Assert.assertEquals(PartnerIncome.settle(m, MIDNIGHT + 25 * HOUR), 50);
        Assert.assertEquals(m.getLong("today"), 50);
    }

    @Test public void repeatedReadsKeepFractionalCoins() {
        var m = PartnerIncome.meter(MIDNIGHT).put("rate", 2);
        Assert.assertEquals(PartnerIncome.settle(m, MIDNIGHT + 10 * 60000), 0);
        Assert.assertEquals(PartnerIncome.settle(m, MIDNIGHT + 30 * 60000), 1);
        Assert.assertThrows(ApiV3Exception.class, () -> PartnerIncome.settle(m, MIDNIGHT));
    }
}

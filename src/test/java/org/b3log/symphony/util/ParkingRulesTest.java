/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.util;

import org.testng.Assert;
import org.testng.annotations.Test;

import static org.b3log.symphony.util.ParkingRules.*;

public final class ParkingRulesTest {
    @Test
    public void catalogAndOfflineIncome() {
        Assert.assertEquals(CARS.size(), 10);
        Assert.assertEquals(CARS.stream().map(Car::id).distinct().count(), 10L);
        Assert.assertEquals(CARS.stream().filter(Car::starter).count(), 3L);
        for (Car car : CARS) {
            Assert.assertTrue(car.rate() > 0 && car.price() > 0);
            final long firstCoin = (HOUR + car.rate() - 1) / car.rate();
            Assert.assertEquals(payout(car.rate(), 0, firstCoin - 1), 0);
            Assert.assertEquals(payout(car.rate(), 0, firstCoin), 1);
            Assert.assertEquals(payout(car.rate(), 0, MAX_DURATION), car.rate() * 8);
            Assert.assertEquals(payout(car.rate(), 0, 30 * 24 * HOUR), car.rate() * 8);
        }
        Assert.expectThrows(ApiV3Exception.class, () -> payout(24, 100, 99));
    }
}

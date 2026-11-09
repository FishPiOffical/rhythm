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

import java.util.Base64;
import static org.b3log.symphony.util.ApiV3AppLoginProtocol.*;

public final class ApiV3AppLoginProtocolTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static void reject(Reason reason, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected rejection " + reason);
        }
        catch (Rejected e) { check(e.reason() == reason); }
    }
    public static void main(String[] args) {
        final long now = 1_700_000_000_000L;
        final String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        final byte[] otherBytes = new byte[32]; otherBytes[0] = 1;
        final String other = Base64.getUrlEncoder().withoutPadding().encodeToString(otherBytes);
        check(canonical32(verifier)); check(!canonical32(verifier.substring(0,42)+"B"));
        check(parseQr(qr(verifier)).equals(verifier));
        for(String payload : new String[]{"login:"+verifier,"web:"+verifier,"https://fishpi.cn/"+verifier,qr(verifier)+" ",qr(verifier)+"?x=1",qr(verifier)+"\n"}) reject(Reason.INVALID,()->parseQr(payload));
        check(challenge(verifier).equals("DwBzhbb51LfusnSGBa_hqYSgo7-j8BTQnip4TOnlzRo"));
        check(!handleHash(verifier).equals(verifier) && !handleHash(verifier).equals(challenge(verifier)));
        System.out.println("PASS app login 1 exact canonical QR payload, PKCE SHA-256 vector and domain-separated stored handle hash");
        final Snapshot waiting=create(now), claimed=claim(waiting,challenge(verifier),now+1);
        check(claimed.state()==State.CLAIMED && claim(claimed,challenge(verifier),now+2).equals(claimed));
        reject(Reason.BOUND_TO_OTHER_APP,()->claim(claimed,challenge(other),now+3));
        reject(Reason.WRONG_STATE,()->approveWeb(waiting,now+1));
        reject(Reason.WRONG_STATE,()->approveWeb(claimed,now+1));
        reject(Reason.WRONG_STATE,()->consume(claimed,verifier,now+1));
        reject(Reason.BAD_PROOF,()->confirmApp(claimed,other,now+1));
        final Snapshot confirmed=confirmApp(claimed,verifier,now+2);
        reject(Reason.WRONG_STATE,()->consume(confirmed,verifier,now+3));
        final Snapshot approved=approveWeb(confirmed,now+3);
        check(confirmApp(approved,verifier,now+4).equals(approved));
        check(approveWeb(approved,now+4).equals(approved));
        check(claim(approved,challenge(verifier),now+4).equals(approved));
        check(approved.createdAt()==now && approved.expiresAt()==now+120_000);
        reject(Reason.BAD_PROOF,()->consume(approved,other,now+4));
        final Snapshot consumed=consume(approved,verifier,now+4);
        reject(Reason.ALREADY_USED,()->consume(consumed,verifier,now+5));
        reject(Reason.ALREADY_USED,()->approveWeb(consumed,now+5));
        System.out.println("PASS app login 2 claim binding, both ordered explicit confirmations, immutable expiry and exactly-once terminal consume");
        for(Snapshot state:new Snapshot[]{waiting,claimed,confirmed,approved}) {
            reject(Reason.EXPIRED,()->claim(state,challenge(verifier),now+120_000));
            reject(Reason.EXPIRED,()->approveWeb(state,now+120_000));
            reject(Reason.INVALID,()->approveWeb(state,now-1));
            final Snapshot denied=denyWeb(state,now+10);
            reject(Reason.DENIED,()->claim(denied,challenge(verifier),now+11));
            reject(Reason.DENIED,()->approveWeb(denied,now+11));
        }
        final Snapshot denied=denyApp(claimed,verifier,now+10);
        reject(Reason.DENIED,()->confirmApp(denied,verifier,now+11));
        reject(Reason.BAD_PROOF,()->denyApp(claimed,other,now+10));
        System.out.println("PASS app login 3 two-minute boundary, clock rollback fail-closed, web/app rejection cannot revive or reset a request");
        reject(Reason.INVALID,()->create(Long.MAX_VALUE));
        reject(Reason.INVALID,()->new Snapshot(State.CLAIMED,now,now+120_000,""));
        reject(Reason.INVALID,()->new Snapshot(State.WAITING,now,now+120_000,challenge(verifier)));
        reject(Reason.INVALID,()->new Snapshot(State.WAITING,now,now+120_001,""));
        System.out.println("PASS app login 4 malformed persistence snapshots and overflow rejected without carrying raw credentials");
    }
}

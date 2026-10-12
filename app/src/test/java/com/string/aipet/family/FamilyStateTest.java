package com.string.aipet.family;

import org.junit.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.Assert.*;

public class FamilyStateTest {
    private static final long NOW = Instant.parse("2026-10-11T08:00:00Z").toEpochMilli();
    private FamilyState state() { FamilyState s = new FamilyState(); s.zone = "Asia/Shanghai"; s.cursor = NOW; return s; }
    private FamilyState.Rule rule(FamilyState s, int free, int unit, int stars) {
        FamilyState.Rule r = new FamilyState.Rule("game", "游戏", free, unit, stars, NOW); s.rules.add(r); return r;
    }
    private UsageTimeline.Event event(long minutes, int type, String activity) { return new UsageTimeline.Event(NOW + minutes * 60_000, type, "game", activity); }
    @Test public void submissionNeedsApprovalAndIsOncePerDay() {
        FamilyState s = state(); FamilyState.Task t = new FamilyState.Task("读书", "📚", 3); s.tasks.add(t);
        assertTrue(s.submit(t, NOW)); assertFalse(s.submit(t, NOW)); assertEquals(0, s.balance());
        FamilyState.Completion c = s.completion(t.id, s.day(NOW)); assertTrue(s.approve(c, NOW));
        assertFalse(s.approve(c, NOW)); assertEquals(3, s.balance()); assertTrue(s.submit(t, NOW + 24 * 60 * 60_000L));
    }
    @Test public void submittedRewardIsASnapshot() {
        FamilyState s = state(); FamilyState.Task t = new FamilyState.Task("读书", "📚", 3); s.submit(t, NOW); t.stars = 99; t.title = "changed";
        FamilyState.Completion c = s.completions.get(0); s.approve(c, NOW); assertEquals(3, s.balance()); assertEquals("读书", c.title);
    }
    @Test public void undoRefundsOnlyAwardedStarsAndLeavesAudit() {
        FamilyState s = state(); FamilyState.Task t = new FamilyState.Task("读书", "📚", 3); s.submit(t, NOW);
        FamilyState.Completion c = s.completions.get(0); s.approve(c, NOW); assertTrue(s.undo(c, NOW)); assertFalse(s.undo(c, NOW));
        assertEquals(0, s.balance()); assertEquals(2, s.ledger.size()); assertTrue(s.submit(t, NOW));
    }
    @Test public void rejectsDisabledTask() { FamilyState s = state(); FamilyState.Task t = new FamilyState.Task("读书", "📚", 3); t.enabled = false; assertFalse(s.submit(t, NOW)); }
    @Test public void freeAllowanceAndFullUnitBoundary() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 5, 10, 2);
        assertEquals(0, FamilyState.charge(14 * 60_000 + 59_999, r)); assertEquals(2, FamilyState.charge(15 * 60_000, r)); assertEquals(4, FamilyState.charge(25 * 60_000, r));
    }
    @Test public void repeatSettlementDoesNotChargeTwice() {
        FamilyState s = state(); rule(s, 0, 10, 1); s.addInterval("game", NOW, NOW + 20 * 60_000); s.settle(NOW); s.settle(NOW);
        assertEquals(-2, s.balance()); assertEquals(1, s.ledger.size()); s.addInterval("game", NOW + 20 * 60_000, NOW + 30 * 60_000); s.settle(NOW); assertEquals(-3, s.balance());
    }
    @Test public void openSessionContinuesAcrossQueries() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 0, 10, 1);
        UsageTimeline.consume(s, List.of(event(0, 1, "a")), NOW + 6 * 60_000);
        UsageTimeline.consume(s, List.of(event(13, 2, "a")), NOW + 15 * 60_000);
        assertEquals(13 * 60_000, s.usageToday(r, s.day(NOW))); assertEquals(-1, s.balance()); assertTrue(s.active.isEmpty());
    }
    @Test public void multipleActivitiesAreNotDoubleCounted() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 0, 10, 1);
        UsageTimeline.consume(s, List.of(event(0, 1, "a"), event(1, 1, "b"), event(5, 2, "a"), event(10, 2, "b")), NOW + 15 * 60_000);
        assertEquals(10 * 60_000, s.usageToday(r, s.day(NOW))); assertEquals(-1, s.balance());
    }
    @Test public void duplicateResumeAndUnknownPauseAreSafe() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 0, 10, 1);
        UsageTimeline.consume(s, List.of(event(0, 1, "a"), event(2, 1, "a"), event(3, 2, "unknown"), event(10, 2, "a")), NOW + 12 * 60_000);
        assertEquals(10 * 60_000, s.usageToday(r, s.day(NOW)));
    }
    @Test public void lockAndShutdownStopCounting() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 0, 1, 1);
        UsageTimeline.consume(s, List.of(event(0, 1, "a"), event(3, 17, ""), event(5, 1, "a"), event(7, 26, "")), NOW + 30 * 60_000);
        assertEquals(5 * 60_000, s.usageToday(r, s.day(NOW)));
    }
    @Test public void screenOffUsesAndroidEvent16AndScreenOnDoesNotStopSession() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 0, 1, 1);
        UsageTimeline.consume(s, List.of(event(0, 1, "a"), event(1, 15, ""), event(3, 16, "")), NOW + 20 * 60_000);
        assertEquals(3 * 60_000, s.usageToday(r, s.day(NOW))); assertTrue(s.active.isEmpty());
    }
    @Test public void unmatchedSessionBeforeStartupDoesNotCountPoweredOffTime() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 0, 1, 1); s.active.put("game|a", "game");
        UsageTimeline.consume(s, List.of(event(1, 5, ""), event(60, 27, ""), event(61, 1, "a"), event(63, 2, "a")), NOW + 65 * 60_000);
        assertEquals(2 * 60_000, s.usageToday(r, s.day(NOW))); assertEquals(-2, s.balance());
    }
    @Test public void shutdownStateIsPreservedAcrossQueries() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 0, 1, 1);
        UsageTimeline.consume(s, List.of(event(0, 1, "a"), event(3, 26, "")), NOW + 4 * 60_000);
        UsageTimeline.consume(s, List.of(event(5, 1, "a"), event(60, 27, ""), event(61, 1, "a"), event(63, 2, "a")), NOW + 65 * 60_000);
        assertEquals(5 * 60_000, s.usageToday(r, s.day(NOW))); assertFalse(s.runtimeDown);
    }
    @Test public void midnightSplitsAndResetsAllowance() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 5, 10, 1);
        long midnight = Instant.parse("2026-10-11T16:00:00Z").toEpochMilli();
        s.addInterval("game", midnight - 15 * 60_000, midnight + 15 * 60_000); s.settle(midnight);
        assertEquals(15 * 60_000, s.usageToday(r, "2026-10-11")); assertEquals(15 * 60_000, s.usageToday(r, "2026-10-12")); assertEquals(-2, s.balance());
    }
    @Test public void ruleStartAndEndAreClipped() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 0, 1, 1); r.start = NOW + 5 * 60_000; r.end = NOW + 10 * 60_000;
        s.addInterval("game", NOW, NOW + 20 * 60_000); s.settle(NOW); assertEquals(-5, s.balance());
    }
    @Test public void changedRuleKeepsPriorDeductions() {
        FamilyState s = state(); FamilyState.Rule first = rule(s, 0, 10, 1); first.end = NOW + 20 * 60_000;
        FamilyState.Rule next = new FamilyState.Rule("game", "游戏", 0, 5, 2, first.end); s.rules.add(next);
        s.addInterval("game", NOW, NOW + 30 * 60_000); s.settle(NOW); s.settle(NOW); assertEquals(-6, s.balance()); assertEquals(2, s.billed.size());
    }
    @Test public void missingBoundaryEventWaitsForNextQuery() {
        FamilyState s = state(); FamilyState.Rule r = rule(s, 0, 1, 1);
        UsageTimeline.consume(s, List.of(event(0, 1, "a"), event(5, 2, "a")), NOW + 5 * 60_000);
        UsageTimeline.consume(s, List.of(event(5, 2, "a")), NOW + 7 * 60_000); assertEquals(5 * 60_000, s.usageToday(r, s.day(NOW)));
    }
    @Test public void dstDayDoesNotAssume24Hours() {
        FamilyState s = state(); s.zone = "America/New_York";
        long start = Instant.parse("2026-11-01T04:00:00Z").toEpochMilli(), end = Instant.parse("2026-11-02T05:00:00Z").toEpochMilli();
        FamilyState.Rule r = new FamilyState.Rule("game", "游戏", 0, 60, 1, start); s.rules.add(r); s.addInterval("game", start, end); s.settle(end);
        assertEquals(25 * 60 * 60_000L, s.usageToday(r, "2026-11-01")); assertEquals(-25, s.balance());
    }
    @Test(expected = IllegalArgumentException.class) public void zeroBillingUnitIsRejected() { new FamilyState.Rule("game", "游戏", 0, 0, 1, NOW); }
}

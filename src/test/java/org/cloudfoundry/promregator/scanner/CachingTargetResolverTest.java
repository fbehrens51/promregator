package org.cloudfoundry.promregator.scanner;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.cloudfoundry.promregator.JUnitTestUtils;
import org.cloudfoundry.promregator.config.Target;
import org.cloudfoundry.promregator.scanner.MockedCachingTargetResolverSpringApplication.MockedTargetResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = MockedCachingTargetResolverSpringApplication.class)
public class CachingTargetResolverTest {
	@AfterAll
	static void cleanupEnvironment() {
		JUnitTestUtils.cleanUpAll();
	}

	@Autowired
	private TargetResolver targetResolver;

	@Autowired
	private CachingTargetResolver cachingTargetResolver;

	@Autowired
	private Clock clock;

	@AfterEach
	void resetFlags() {
		this.cachingTargetResolver.invalidateCache();
		this.cachingTargetResolver.setClock(this.clock);

		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		mtr.resetRequestFlags();
		mtr.setTargetsToReportAsApiLocked(Collections.emptySet());
	}
	@Test
	void testTwoPlainTargets() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		list.add(MockedTargetResolver.target2);
		
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list).getResolvedTargets();
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertTrue(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assertions.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(2, actualList.size());
		
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt : actualList) {
			if (rt == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assertions.fail("Unexpected target provided");
			}
		}
		Assertions.assertTrue(target1Found);
		Assertions.assertTrue(target2Found);
	}
	
	@Test
	void testTargetMissingApplicationName() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.targetAllInSpace);
		
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list).getResolvedTargets();
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertFalse(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertTrue(mtr.isRequestForTargetAllInSpace());
		Assertions.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(2, actualList.size());
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt : actualList) {
			if (rt == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assertions.fail("Unexpected target provided");
			}
		}
		Assertions.assertTrue(target1Found);
		Assertions.assertTrue(target2Found);
	}
	
	@Test
	void testRepeatedRequestIsCached() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		
		// fill the cache
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list).getResolvedTargets();
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assertions.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(MockedTargetResolver.rTarget1, rt);
		
		
		mtr.resetRequestFlags();
		
		actualList = this.cachingTargetResolver.resolveTargets(list).getResolvedTargets();
		Assertions.assertFalse(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		
		Assertions.assertEquals(1, actualList.size());
		
		rt = actualList.get(0);
		Assertions.assertEquals(MockedTargetResolver.rTarget1, rt);

	}
	
	@Test
	void testRepeatedRequestIsCachedAlsoSelectively() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		
		// fill the cache
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list).getResolvedTargets();
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assertions.assertFalse(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(1, actualList.size());
		
		ResolvedTarget rt = actualList.get(0);
		Assertions.assertEquals(MockedTargetResolver.rTarget1, rt);
		
		
		mtr.resetRequestFlags();
		
		list.add(MockedTargetResolver.target2);
		
		actualList = this.cachingTargetResolver.resolveTargets(list).getResolvedTargets();
		Assertions.assertFalse(mtr.isRequestForTarget1());
		Assertions.assertTrue(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		
		Assertions.assertEquals(2, actualList.size());
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt2 : actualList) {
			if (rt2 == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt2 == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assertions.fail("Unexpected target provided");
			}
		}
		Assertions.assertTrue(target1Found);
		Assertions.assertTrue(target2Found);
	}
	
	@Test
	void testTargetDuplicateRequestDistincts() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);
		list.add(MockedTargetResolver.targetRegex);
		
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list).getResolvedTargets();
		
		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertFalse(mtr.isRequestForTarget2());
		Assertions.assertFalse(mtr.isRequestForTargetAllInSpace());
		Assertions.assertTrue(mtr.isRequestForTargetWithRegex());
		
		Assertions.assertEquals(2, actualList.size());
		
		boolean target1Found = false;
		boolean target2Found = false;
		for (ResolvedTarget rt : actualList) {
			if (rt == MockedTargetResolver.rTarget1) {
				target1Found = true;
			} else if (rt == MockedTargetResolver.rTarget2) {
				target2Found = true;
			} else {
				Assertions.fail("Unexpected target provided");
			}
		}
		Assertions.assertTrue(target1Found);
		Assertions.assertTrue(target2Found);
	}

	@Test
	void testStaleResolutionIsKeptWhileApiIsLocked() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);

		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;

		// fill the cache
		List<ResolvedTarget> actualList = this.cachingTargetResolver.resolveTargets(list).getResolvedTargets();
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertEquals(1, actualList.size());
		Assertions.assertEquals(MockedTargetResolver.rTarget1, actualList.get(0));

		mtr.resetRequestFlags();

		// advance the clock past the default 300s TTL and have the parent resolver
		// report the target as API-locked (simulating a CF backup window) instead of
		// actually resolving it
		this.cachingTargetResolver.setClock(Clock.offset(this.clock, Duration.ofSeconds(301)));
		mtr.setTargetsToReportAsApiLocked(Set.of(MockedTargetResolver.target1));

		TargetResolutionResult result = this.cachingTargetResolver.resolveTargets(list);
		Assertions.assertTrue(mtr.isRequestForTarget1(), "expired entry should still trigger a refresh attempt");
		Assertions.assertEquals(1, result.getResolvedTargets().size(), "stale value should still be served");
		Assertions.assertEquals(MockedTargetResolver.rTarget1, result.getResolvedTargets().get(0));
		Assertions.assertTrue(result.getApiLockedTargets().isEmpty(), "a fallback was available, so this isn't reported as locked with no data");

		mtr.resetRequestFlags();

		// without advancing the clock further, the entry is still considered expired
		// (its timestamp was never refreshed while locked) and is retried again -
		// no backoff
		this.cachingTargetResolver.resolveTargets(list);
		Assertions.assertTrue(mtr.isRequestForTarget1(), "locked entries are retried on every call, without backoff");
	}

	@Test
	void testApiLockedWithoutPriorDataReturnsNothing() {
		List<Target> list = new LinkedList<>();
		list.add(MockedTargetResolver.target1);

		MockedTargetResolver mtr = (MockedTargetResolver) targetResolver;
		mtr.setTargetsToReportAsApiLocked(Set.of(MockedTargetResolver.target1));

		TargetResolutionResult result = this.cachingTargetResolver.resolveTargets(list);
		Assertions.assertTrue(mtr.isRequestForTarget1());
		Assertions.assertTrue(result.getResolvedTargets().isEmpty(), "no data was ever cached, so there is nothing to fall back to");
		Assertions.assertEquals(Set.of(MockedTargetResolver.target1), result.getApiLockedTargets());
	}
}

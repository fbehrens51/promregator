package org.cloudfoundry.promregator.scanner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.cloudfoundry.promregator.config.Target;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class CachingTargetResolver implements TargetResolver {
	@Value("${cf.cache.timeout.resolver:300}")
	private int timeoutCacheResolverLevel;

	@Autowired
	private Clock clock;

	private TargetResolver parentTargetResolver;

	private static final class CacheEntry {
		private final List<ResolvedTarget> resolvedTargets;
		private final Instant timestamp;

		private CacheEntry(List<ResolvedTarget> resolvedTargets, Instant timestamp) {
			this.resolvedTargets = resolvedTargets;
			this.timestamp = timestamp;
		}
	}

	/* Note this is intentionally NOT a PassiveExpiringMap: that map destructively evicts an
	 * entry the moment it is read past its TTL, which makes it impossible to keep serving a
	 * stale-but-still-valid value as a fallback when the Cloud Foundry API is locked (see
	 * TargetResolutionResult#getApiLockedTargets()). Expiry here is checked manually without
	 * ever removing an entry except via explicit overwrite or invalidateCache().
	 */
	private final Map<Target, CacheEntry> cache = new ConcurrentHashMap<>();

	public CachingTargetResolver(TargetResolver parentTargetResolver) {
		this.parentTargetResolver = parentTargetResolver;
	}

	public TargetResolver getNativeTargetResolver() {
		return parentTargetResolver;
	}

	public void setClock(Clock clock) {
		this.clock = clock;
	}

	@Override
	public TargetResolutionResult resolveTargets(List<Target> configTargets) {
		List<Target> toBeLoaded = new LinkedList<>();

		List<ResolvedTarget> result = new LinkedList<>();
		Set<Target> apiLockedTargets = new HashSet<>();

		for (Target configTarget : configTargets) {
			CacheEntry entry = this.cache.get(configTarget);
			if (entry != null && Duration.between(entry.timestamp, Instant.now(this.clock)).getSeconds() < this.timeoutCacheResolverLevel) {
				result.addAll(entry.resolvedTargets);
			} else {
				toBeLoaded.add(configTarget);
			}
		}

		if (!toBeLoaded.isEmpty()) {
			TargetResolutionResult resolutionResult = this.parentTargetResolver.resolveTargets(toBeLoaded);
			List<ResolvedTarget> newlyResolvedTargets = resolutionResult.getResolvedTargets();

			result.addAll(newlyResolvedTargets);

			for (Target lockedTarget : resolutionResult.getApiLockedTargets()) {
				/* the entry, if any, was deliberately left untouched above, so it is
				 * still available here as a fallback; its timestamp is intentionally
				 * NOT refreshed, so it will be retried again on the next call rather
				 * than being cached indefinitely.
				 */
				CacheEntry stale = this.cache.get(lockedTarget);
				if (stale != null) {
					result.addAll(stale.resolvedTargets);
				} else {
					apiLockedTargets.add(lockedTarget);
				}
			}

			updateTargetResolutionCache(newlyResolvedTargets);
		}

		/* see also issue #75: the list here might include duplicates, which we need to eliminate */
		HashSet<ResolvedTarget> hset = new HashSet<>(result);
		return new TargetResolutionResult(new LinkedList<>(hset), apiLockedTargets);
	}

	private void updateTargetResolutionCache(List<ResolvedTarget> newlyResolvedTargets) {
		Map<Target, List<ResolvedTarget>> map = new HashMap<>();
		for (ResolvedTarget rtarget : newlyResolvedTargets) {
			List<ResolvedTarget> list = map.get(rtarget.getOriginalTarget());
			if (list == null) {
				list = new LinkedList<>();
			}
			list.add(rtarget);
			map.put(rtarget.getOriginalTarget(), list);
		}

		Instant now = Instant.now(this.clock);
		for (Map.Entry<Target, List<ResolvedTarget>> mapEntry : map.entrySet()) {
			this.cache.put(mapEntry.getKey(), new CacheEntry(mapEntry.getValue(), now));
		}
	}

	public void invalidateCache() {
		this.cache.clear();
	}
}

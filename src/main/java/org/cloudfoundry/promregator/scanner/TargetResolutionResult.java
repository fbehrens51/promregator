package org.cloudfoundry.promregator.scanner;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.cloudfoundry.promregator.config.Target;

/**
 * The outcome of a {@link TargetResolver#resolveTargets(List)} call.
 *
 * Besides the list of successfully resolved targets, this also carries the subset of the
 * requested config {@link Target}s which could not be (re-)resolved because the Cloud Foundry
 * API reported itself as locked (HTTP 503, e.g. while its backing database is being backed up).
 * A locked API implies the underlying org/space/app configuration has not changed, so callers
 * (e.g. {@link CachingTargetResolver}) may treat these targets differently from targets which
 * failed to resolve for any other reason.
 */
public class TargetResolutionResult {
	private final List<ResolvedTarget> resolvedTargets;
	private final Set<Target> apiLockedTargets;

	public TargetResolutionResult(List<ResolvedTarget> resolvedTargets, Set<Target> apiLockedTargets) {
		this.resolvedTargets = resolvedTargets;
		this.apiLockedTargets = apiLockedTargets;
	}

	public TargetResolutionResult(List<ResolvedTarget> resolvedTargets) {
		this(resolvedTargets, Collections.emptySet());
	}

	public List<ResolvedTarget> getResolvedTargets() {
		return resolvedTargets;
	}

	/**
	 * @return the config targets from the requested batch which could not be resolved because
	 * the Cloud Foundry API reported itself as locked.
	 */
	public Set<Target> getApiLockedTargets() {
		return apiLockedTargets;
	}
}

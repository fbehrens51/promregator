package org.cloudfoundry.promregator.scanner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.cloudfoundry.promregator.cfaccessor.CFAccessor;
import org.cloudfoundry.promregator.cfaccessor.CFAccessorMock;
import org.cloudfoundry.promregator.config.Target;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockedCachingTargetResolverSpringApplication {
	
	public static class MockedTargetResolver implements TargetResolver {
		public static final Target target1 = new Target();
		public static final Target target2 = new Target();
		public static final Target targetAllInSpace = new Target();
		public static final Target targetRegex = new Target();
		
		public static ResolvedTarget rTarget1;
		public static ResolvedTarget rTarget2;
		
		private boolean requestForTarget1 = false;
		private boolean requestForTarget2 = false;
		private boolean requestForTargetAllInSpace = false;
		private boolean requestForTargetWithRegex = false;
		
		static {
			target1.setOrgName("unittestorg");
			target1.setSpaceName("unittestspace");
			target1.setApplicationName("testapp");
			target1.setPath("path");
			target1.setProtocol("https");
			
			target2.setOrgName("unittestorg");
			target2.setSpaceName("unittestspace");
			target2.setApplicationName("testapp2");
			target2.setPath("path");
			target2.setProtocol("https");
			
			targetAllInSpace.setOrgName("unittestorg");
			targetAllInSpace.setSpaceName("unittestspace");
			// application Name is missing
			targetAllInSpace.setPath("path");
			targetAllInSpace.setProtocol("https");
			
			rTarget1 = new ResolvedTarget(target1);
			rTarget2 = new ResolvedTarget(target2);
		}
		
		private Set<Target> targetsToReportAsApiLocked = Collections.emptySet();

		@Override
		public TargetResolutionResult resolveTargets(List<Target> configTarget) {
			List<ResolvedTarget> response = new LinkedList<>();

			for (Target t : configTarget) {
				if (t == target1) {
					this.requestForTarget1 = true;
				} else if (t == target2) {
					this.requestForTarget2 = true;
				} else if (t == targetAllInSpace) {
					this.requestForTargetAllInSpace = true;
				} else if (t == targetRegex) {
					this.requestForTargetWithRegex = true;
				}

				if (this.targetsToReportAsApiLocked.contains(t)) {
					continue;
				}

				if (t == target1) {
					response.add(rTarget1);
				} else if (t == target2) {
					response.add(rTarget2);
				} else if (t == targetAllInSpace) {
					response.add(rTarget1);
					response.add(rTarget2);
				} else if (t == targetRegex) {
					response.add(rTarget1);
					response.add(rTarget2);
				}
			}

			Set<Target> apiLocked = new HashSet<>(configTarget);
			apiLocked.retainAll(this.targetsToReportAsApiLocked);

			return new TargetResolutionResult(response, apiLocked);
		}

		/**
		 * Configures which of the well-known static targets should be reported as
		 * API-locked (rather than actually resolved) on the next {@link #resolveTargets(List)}
		 * call, simulating a Cloud Foundry API locked for a backup.
		 */
		public void setTargetsToReportAsApiLocked(Set<Target> targetsToReportAsApiLocked) {
			this.targetsToReportAsApiLocked = targetsToReportAsApiLocked;
		}

		public boolean isRequestForTarget1() {
			return requestForTarget1;
		}

		public boolean isRequestForTarget2() {
			return requestForTarget2;
		}

		public boolean isRequestForTargetAllInSpace() {
			return requestForTargetAllInSpace;
		}
		
		public boolean isRequestForTargetWithRegex() {
			return requestForTargetWithRegex;
		}
		
		public void resetRequestFlags() {
			this.requestForTarget1 = false;
			this.requestForTarget2 = false;
			this.requestForTargetAllInSpace = false;
			this.requestForTargetWithRegex = false;
		}
	}
	
	@Bean
	public CFAccessor cfAccessor() {
		return new CFAccessorMock();
	}

	@Bean
	public Clock clock() {
		return Clock.fixed(Instant.parse("2007-12-03T10:15:30.00Z"), ZoneId.of("UTC"));
	}

	@Bean
	public TargetResolver targetResolver() {
		return new MockedTargetResolver();
	}
	
	@Bean
	public CachingTargetResolver cachingTargetResolver(TargetResolver targetResolver) {
		return new CachingTargetResolver(targetResolver);
	}
}

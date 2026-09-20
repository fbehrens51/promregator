# Cache Types

Over the time, Promregator has used different cache types for caching metadata provided by the Cloud Foundry's Cloud Controller. These caches partly have different behaviors, which require explanation.

## Behavior Overview

The following table describes the major different bahaviors of the caches used:

| Property | Classical Cache (only V0.*) | Caffeine Cache |
|----------|-----------------|----------------|
| Timeout  | Enforced Eviction | No eviction  |
| Blocking on Timeout | Yes   | No |

The following sections will explain these differences in more detail.

### Timeout Behavior

When hitting the timeouts of cache values (cf. [configuration options](config.md) `cf.cache.timeout.*`), the classical cache will simply remove the value from the cache. The next consumer will then get a cache miss of the corresponding value, which in turn triggers a reload of that value. The requester then is blocked until the value is available.

The Caffeine Cache does not remove the value from the cache. Instead reloading of the value is triggered asynchronously. The requester is provided the old value from the cache. While the asynchronous request is still pending, other additional requesters will also still receive the old value. Once the asynchronous response is available, the cache is updated accordingly. Starting from there, requesters will get the new value. Additional information about Caffeine's Cache behavior can also be found at its [Eviction page](https://github.com/ben-manes/caffeine/wiki/Eviction). Promregator uses the concept of time-based eviction.

### Blocking on Timeout

As also described in the previous section, the classical cache will block the execution of the requester, if a value is timed out.

If the Caffeine Cache still has an old value for the key in the cache, it will return the old value immediately. If there is no such value available, the cache will block the requester until a value is available.

## Cache Invalidation

Independent of the type of cache, it can be invalidated manually. For more information refer to the [page "Cache Invalidation"](invalidate-cache.md).

## Resolver Cache and a Locked Cloud Foundry API

The Resolver Cache (cf. [page "Cache Invalidation"](invalidate-cache.md)) otherwise follows the
"Classical Cache" behavior described above (enforced eviction on timeout). It makes one exception:
if refreshing an expired entry fails specifically because the Cloud Foundry API reports itself as
locked (HTTP 503, returned by design while its backing database is being backed up), the previous
value is kept and served instead of being evicted. Since a locked API implies the underlying
org/space/app configuration cannot have changed, the previously resolved value is still accurate.
The affected entry is retried on every subsequent request while the API remains locked (no
backoff); as soon as a refresh succeeds again, the cache is updated normally. This exception does
not apply to any other kind of failure (e.g. network errors, timeouts, or any other CF API error),
for which the entry is still evicted as usual.

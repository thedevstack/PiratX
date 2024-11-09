/*
 * Copyright 2015-2024 the original author or authors
 *
 * This software is licensed under the Apache License, Version 2.0,
 * the GNU Lesser General Public License version 2 or later ("LGPL")
 * and the WTFPL.
 * You may choose either license to govern your use of this software only
 * upon the condition that you accept all of the terms of either
 * the Apache License 2.0, the LGPL 2.1+ or the WTFPL.
 */
package org.minidns.cache;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.minidns.DnsCache;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsname.DnsName;
import org.minidns.dnsqueryresult.CachedDnsQueryResult;
import org.minidns.dnsqueryresult.DirectCachedDnsQueryResult;
import org.minidns.dnsqueryresult.DnsQueryResult;

/**
 * LRU based DNSCache backed by a LinkedHashMap.
 */
public class LruCache extends DnsCache {

    /**
     * Internal miss count.
     */
    protected long missCount = 0L;

    /**
     * Internal expire count (subset of misses that was caused by expire).
     */
    protected long expireCount = 0L;

    /**
     * Internal hit count.
     */
    protected long hitCount = 0L;

    /**
     * The internal capacity of the backend cache.
     */
    protected int capacity;

    /**
     * The upper bound of the ttl. All longer TTLs will be capped by this ttl.
     */
    protected long maxTTL;

    /**
     * The backend cache.
     */
    protected LinkedHashMap<DnsMessage, CachedDnsQueryResult> backend;

    /**
     * Create a new LRUCache with given capacity and upper bound ttl.
     * @param capacity The internal capacity.
     * @param maxTTL The upper bound for any ttl.
     */
    @SuppressWarnings("serial")
    public LruCache(final int capacity, final long maxTTL) {
        this.capacity = capacity;
        this.maxTTL = maxTTL;
        backend = new LinkedHashMap<DnsMessage, CachedDnsQueryResult>(
                Math.min(capacity + (capacity + 3) / 4 + 2, 11), 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Entry<DnsMessage, CachedDnsQueryResult> eldest) {
                    return size() > capacity;
                }
            };
    }

    /**
     * Create a new LRUCache with given capacity.
     * @param capacity The capacity of this cache.
     */
    public LruCache(final int capacity) {
        this(capacity, Long.MAX_VALUE);
    }

    public LruCache() {
        this(DEFAULT_CACHE_SIZE);
    }

    @Override
    protected synchronized void putNormalized(DnsMessage q, DnsQueryResult result) {
        if (result.response.receiveTimestamp <= 0L) {
            return;
        }
        backend.put(q, new DirectCachedDnsQueryResult(q, result));
    }

    @Override
    protected synchronized CachedDnsQueryResult getNormalized(DnsMessage q) {
        CachedDnsQueryResult result = backend.get(q);
        if (result == null) {
            missCount++;
            return null;
        }

        DnsMessage message = result.response;

        // RFC 2181 § 5.2 says that all TTLs in a RRSet should be equal, if this isn't the case, then we assume the
        // shortest TTL to be the effective one.
        final long answersMinTtl = message.getAnswersMinTtl();
        final long ttl = Math.min(answersMinTtl, maxTTL);

        final long expiryDate = message.receiveTimestamp + (ttl * 1000);
        final long now = System.currentTimeMillis();
        if (expiryDate < now) {
            missCount++;
            expireCount++;
            backend.remove(q);
            return null;
        } else {
            hitCount++;
            return result;
        }
    }

    /**
     * Clear all entries in this cache.
     */
    public synchronized void clear() {
        backend.clear();
        missCount = 0L;
        hitCount = 0L;
        expireCount = 0L;
    }

    /**
     * Get the miss count of this cache which is the number of fruitless
     * get calls since this cache was last resetted.
     * @return The number of cache misses.
     */
    public long getMissCount() {
        return missCount;
    }

    /**
     * The number of expires (cache hits that have had a ttl to low to be
     * retrieved).
     * @return The expire count.
     */
    public long getExpireCount() {
        return expireCount;
    }

    /**
     * The cache hit count (all successful calls to get).
     * @return The hit count.
     */
    public long getHitCount() {
        return hitCount;
    }

    @Override
    public String toString() {
        return "LRUCache{usage=" + backend.size() + "/" + capacity + ", hits=" + hitCount + ", misses=" + missCount + ", expires=" + expireCount + "}";
    }

    @Override
    public void offer(DnsMessage query, DnsQueryResult result, DnsName knownAuthoritativeZone) {
    }
}

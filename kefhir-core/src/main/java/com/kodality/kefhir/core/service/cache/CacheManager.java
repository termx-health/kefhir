/*
 * MIT License
 *
 * Copyright (c) 2024 Kodality
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.kodality.kefhir.core.service.cache;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.function.Supplier;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.ehcache.Cache;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;

@Slf4j
@Singleton
public class CacheManager implements AutoCloseable {
  private final org.ehcache.CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder().build();

  public CacheManager() {
    manager.init();
  }

  public Cache<String, Object> registerCache(String name, int maxEntries, long ttlSeconds) {
    CacheConfigurationBuilder<String, Object> builder = CacheConfigurationBuilder
        .newCacheConfigurationBuilder(String.class, Object.class, ResourcePoolsBuilder.heap(maxEntries));
    builder.withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(ttlSeconds)));
    if (getCache(name) != null) {
      manager.removeCache(name);
    }
    return manager.createCache(name, builder.build());
  }

  public Cache<String, Object> getCache(String cacheName) {
    return manager.getCache(cacheName, String.class, Object.class);
  }

  @SuppressWarnings("unchecked")
  public <V> V get(String cacheName, String key, Supplier<V> computeFn) {
    Cache<String, Object> cache = getCache(cacheName);
    if (!cache.containsKey(key)) {
      V value = computeFn.get();
      if (value == null) {
        return null;
      }
      cache.put(key, value);
    }
    return (V) cache.get(key);
  }

  public void remove(String cacheName, String key) {
    getCache(cacheName).remove(key);
  }

  @PreDestroy
  @Override
  public void close() {
    log.info("Closing cache manager...");
    this.manager.close();
  }

}

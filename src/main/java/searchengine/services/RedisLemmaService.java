package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.properties.AppCacheProperties;

import java.util.concurrent.CompletableFuture;

@Service
public class RedisLemmaService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public RedisLemmaService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void clearAllCaches() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Async
    public void saveLemmaToCache(String key, String lemmaValue) {
        redisTemplate.opsForValue().set(AppCacheProperties.CacheNames.LEMMA_CACHE + "::" + key, lemmaValue);
    }

    @Async
    public CompletableFuture<String> getLemmaFromCache(String key) {
        return CompletableFuture.supplyAsync(() -> (String) redisTemplate.opsForValue().get(AppCacheProperties.CacheNames.LEMMA_CACHE + "::" + key));
    }
}

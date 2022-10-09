package com.markerhub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @author mingchiuli
 * @create 2021-11-20 10:03 PM
 */
@Configuration
public class RedisConfig {

    /**
     * 去掉key前面的乱码
     * @return
     */
    @Bean("redisTemplate")
    public RedisTemplate<String, Object> redisTemplateInit(RedisConnectionFactory redisConnectionFactory, ObjectMapper objectMapper) {
        //设置序列化Key的实例化对象
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setHashValueSerializer(serializer);
        redisTemplate.setValueSerializer(serializer);
        return redisTemplate;
    }

}

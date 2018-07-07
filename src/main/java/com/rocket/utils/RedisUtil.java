package com.rocket.utils;

import redis.clients.jedis.Jedis;

/**
 * Created by Rocket on 2018/7/7.
 */
public class RedisUtil {

    public static Jedis getJedisConnection(){

        return new Jedis("127.0.0.1",6379);
    }

    public static void main(String[] args) {
        Jedis jedis = getJedisConnection();
        jedis.setex("key",60,"value");
    }
}

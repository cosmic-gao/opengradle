package com.opengradle.constant;

/**
 * Redis key prefixes used by filters.
 */
public final class RedisConstant {

    private RedisConstant() {}

    /** {@code TOKENS:<token>} -&gt; userId */
    public static final String TOKENS = "TOKENS:";

    /** {@code USER:URL:<userId>} -&gt; Set&lt;String&gt; of authorized URI prefixes */
    public static final String USER_URL = "USER:URL:";
}

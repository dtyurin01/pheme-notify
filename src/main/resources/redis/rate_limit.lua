-- Sliding Window Rate Limiter
-- KEYS[1] — ratelimit:{userId}:{channel}
-- ARGV[1] — now (current timestamp in milliseconds)
-- ARGV[2] — window (window size in milliseconds, e.g. 3600000 = 1h)
-- ARGV[3] — max_requests (request limit within the window)
-- Returns: 0 = allowed, 1 = rate limit exceeded

local key          = KEYS[1]
local now          = tonumber(ARGV[1])
local window       = tonumber(ARGV[2])
local max_requests = tonumber(ARGV[3])

-- Remove entries outside the sliding window (older than now - window)
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- Count how many requests remain within the window
local count = redis.call('ZCARD', key)

-- Reject if limit is reached
if count >= max_requests then
    return 1
end

-- Record current request (score=timestamp, member is unique via random suffix)
local seq = redis.call('INCR', key .. ':seq')
redis.call('ZADD', key, now, now .. ':' .. seq)

-- Reset TTL so Redis auto-deletes the key when the window expires
redis.call('PEXPIRE', key, window)
redis.call('PEXPIRE', key .. ':seq', window)

return 0

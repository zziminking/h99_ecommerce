-- Redis Lua Script for First-Come-First-Served Coupon Issue
-- 선착순 쿠폰 발급을 위한 원자적 처리

local couponId = KEYS[1]
local userId = ARGV[1]
local timestamp = ARGV[2]

-- Redis Keys
local stockKey = "coupon:stock:" .. couponId
local issuedSetKey = "coupon:issued:" .. couponId

-- 1. 중복 발급 체크
if redis.call('SISMEMBER', issuedSetKey, userId) == 1 then
    return cjson.encode({
        success = false,
        error = "ALREADY_ISSUED",
        message = "이미 발급받은 쿠폰입니다"
    })
end

-- 2. 재고 확인 및 감소
local stock = redis.call('GET', stockKey)
if not stock then
    return cjson.encode({
        success = false,
        error = "COUPON_NOT_FOUND",
        message = "쿠폰 재고 정보를 찾을 수 없습니다"
    })
end

if tonumber(stock) <= 0 then
    return cjson.encode({
        success = false,
        error = "SOLD_OUT",
        message = "쿠폰이 모두 소진되었습니다"
    })
end

-- 재고 감소
redis.call('DECR', stockKey)

-- 3. 발급자 Set에 추가 (중복 방지)
redis.call('SADD', issuedSetKey, userId)

-- 4. 발급 이력 Hash에 저장 (선택적)
local issueKey = "coupon:issue:" .. couponId .. ":" .. userId
redis.call('HSET', issueKey,
    'couponId', couponId,
    'userId', userId,
    'issuedAt', timestamp,
    'status', 'PENDING'
)
redis.call('EXPIRE', issueKey, 86400) -- 24시간 후 자동 삭제

-- 5. 성공 응답
return cjson.encode({
    success = true,
    timestamp = timestamp,
    remainingStock = redis.call('GET', stockKey)
})

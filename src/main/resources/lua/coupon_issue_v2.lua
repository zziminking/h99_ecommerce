-- Redis Lua Script for First-Come-First-Served Coupon Issue V2
-- 선착순 쿠폰 발급을 위한 원자적 처리 (개선 버전)

-- KEYS[1]: couponId
-- ARGV[1]: userId  
-- ARGV[2]: timestamp (밀리초)

local couponId = KEYS[1]
local userId = ARGV[1]
local timestamp = tonumber(ARGV[2])

-- Redis Keys
local metaKey = "coupon:meta:" .. couponId
local stockKey = "coupon:stock:" .. couponId
local issuedSetKey = "coupon:issued:" .. couponId
local logKey = "coupon:issue:log:" .. couponId
local userCouponsKey = "user:coupons:" .. userId

-- ============ 1. 쿠폰 존재 여부 확인 ============
local exists = redis.call('EXISTS', metaKey)
if exists == 0 then
    return cjson.encode({
        success = false,
        error = "COUPON_NOT_FOUND",
        message = "쿠폰을 찾을 수 없습니다"
    })
end

-- ============ 2. 쿠폰 상태 확인 ============
local status = redis.call('HGET', metaKey, 'status')
if status ~= 'ACTIVE' then
    return cjson.encode({
        success = false,
        error = "COUPON_INACTIVE",
        message = "발급 가능한 쿠폰이 아닙니다"
    })
end

-- ============ 3. 유효 기간 확인 ============
local startAt = tonumber(redis.call('HGET', metaKey, 'startAt'))
local endAt = tonumber(redis.call('HGET', metaKey, 'endAt'))

if timestamp < startAt then
    return cjson.encode({
        success = false,
        error = "NOT_STARTED",
        message = "쿠폰 발급 기간이 아닙니다"
    })
end

if timestamp > endAt then
    return cjson.encode({
        success = false,
        error = "EXPIRED",
        message = "쿠폰 발급 기간이 종료되었습니다"
    })
end

-- ============ 4. 중복 발급 체크 ============
local userKey = "user:" .. userId
if redis.call('SISMEMBER', issuedSetKey, userKey) == 1 then
    return cjson.encode({
        success = false,
        error = "ALREADY_ISSUED",
        message = "이미 발급받은 쿠폰입니다"
    })
end

-- ============ 5. 재고 확인 및 차감 ============
local stock = redis.call('GET', stockKey)
if not stock then
    return cjson.encode({
        success = false,
        error = "STOCK_INFO_MISSING",
        message = "쿠폰 재고 정보가 없습니다"
    })
end

if tonumber(stock) <= 0 then
    return cjson.encode({
        success = false,
        error = "SOLD_OUT",
        message = "쿠폰이 모두 소진되었습니다"
    })
end

-- 재고 차감 (원자적)
local newStock = redis.call('DECR', stockKey)

-- 재고가 음수가 되었다면 롤백 (안전장치)
if newStock < 0 then
    redis.call('INCR', stockKey)
    return cjson.encode({
        success = false,
        error = "SOLD_OUT",
        message = "쿠폰이 모두 소진되었습니다"
    })
end

-- ============ 6. 발급 처리 (원자적) ============
-- 6-1. 발급자 Set에 추가 (중복 방지)
redis.call('SADD', issuedSetKey, userKey)

-- 6-2. 발급 순서 기록 (Sorted Set) - 원자적 카운터 사용
local counterKey = "coupon:issue:counter:" .. couponId
local issueSequence = redis.call('INCR', counterKey)
redis.call('ZADD', logKey, issueSequence, userKey)

-- 6-3. 유저별 쿠폰 목록에 추가
redis.call('SADD', userCouponsKey, couponId)

-- ============ 7. 발급 순위 계산 ============
local rank = redis.call('ZRANK', logKey, userKey)

-- ============ 8. 성공 응답 ============
return cjson.encode({
    success = true,
    data = {
        couponId = couponId,
        userId = userId,
        issuedAt = timestamp,
        remainingStock = newStock,
        rank = rank + 1  -- 1-based 순위
    }
})

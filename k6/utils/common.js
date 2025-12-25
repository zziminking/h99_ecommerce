import http from 'k6/http';
import { check } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// 커스텀 메트릭
export const couponIssueSuccess = new Counter('coupon_issue_success');
export const couponIssueFailed = new Counter('coupon_issue_failed');
export const duplicateIssuePrevented = new Counter('duplicate_issue_prevented');
export const soldOutResponses = new Counter('sold_out_responses');
export const couponIssueDuration = new Trend('coupon_issue_duration');

// 환경 설정
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const COUPON_ID = __ENV.COUPON_ID || '1';
export const MIN_USER_ID = parseInt(__ENV.MIN_USER_ID || '1');
export const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID || '1000');

/**
 * 랜덤 사용자 ID 생성
 */
export function getRandomUserId() {
  return Math.floor(Math.random() * (MAX_USER_ID - MIN_USER_ID + 1)) + MIN_USER_ID;
}

/**
 * 쿠폰 발급 요청
 */
export function issueCoupon(couponId, userId) {
  const url = `${BASE_URL}/api/coupons/${couponId}/issue?userId=${userId}`;
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { name: 'IssueCoupon' },
  };

  const startTime = Date.now();
  const response = http.post(url, null, params);
  const duration = Date.now() - startTime;

  // 메트릭 기록
  couponIssueDuration.add(duration);

  return response;
}

/**
 * 쿠폰 발급 응답 검증
 */
export function validateIssueResponse(response) {
  const isSuccess = check(response, {
    'status is 202': (r) => r.status === 202,
    'response has requestId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.requestId !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (isSuccess) {
    couponIssueSuccess.add(1);
  } else {
    couponIssueFailed.add(1);

    // 실패 원인 분석
    try {
      const body = JSON.parse(response.body);
      if (body.message && body.message.includes('이미 발급받은')) {
        duplicateIssuePrevented.add(1);
      } else if (body.message && body.message.includes('소진')) {
        soldOutResponses.add(1);
      }
    } catch (e) {
      // JSON 파싱 실패는 무시
    }
  }

  return isSuccess;
}

/**
 * 쿠폰 상태 조회
 */
export function getCouponIssueStatus(requestId) {
  const url = `${BASE_URL}/api/coupons/issue/status/${requestId}`;
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { name: 'GetCouponStatus' },
  };

  return http.get(url, params);
}

/**
 * 쿠폰 상세 조회
 */
export function getCoupon(couponId) {
  const url = `${BASE_URL}/api/coupons/${couponId}`;
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { name: 'GetCoupon' },
  };

  return http.get(url, params);
}

/**
 * 테스트 시작 전 헬스 체크
 */
export function healthCheck() {
  const response = http.get(`${BASE_URL}/api/coupons`);

  const isHealthy = check(response, {
    'health check status is 200': (r) => r.status === 200,
    'application is UP': (r) => {
      try {
        // 빈 배열이거나 쿠폰 목록이 반환되면 정상
        const body = JSON.parse(r.body);
        return Array.isArray(body);
      } catch (e) {
        return false;
      }
    },
  });

  if (!isHealthy) {
    console.error('Health check failed! Application may not be ready.');
  }

  return isHealthy;
}
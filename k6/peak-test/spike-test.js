/**
 * K6 Spike Test - 선착순 쿠폰 발급
 *
 * 목적: 선착순 이벤트 시작 시점의 급격한 트래픽 증가 대응 검증
 * 부하: VU 10명 → 500명 (10초 내 급증) → 10명, 5분간
 *
 * 실행 방법:
 * k6 run k6/peak-test/spike-test.js
 *
 * 환경변수 설정:
 * k6 run -e BASE_URL=http://localhost:8080 -e COUPON_ID=1 k6/peak-test/spike-test.js
 */

import { sleep } from 'k6';
import {
  BASE_URL,
  COUPON_ID,
  getRandomUserId,
  issueCoupon,
  validateIssueResponse,
  healthCheck
} from '../utils/common.js';

export const options = {
  stages: [
    { duration: '10s', target: 10 },    // 10초 동안 10명
    { duration: '10s', target: 500 },   // 10초 만에 500명으로 급증! (Spike!)
    { duration: '3m', target: 500 },    // 3분 동안 500명 유지
    { duration: '10s', target: 10 },    // 10초 만에 10명으로 감소
    { duration: '2m', target: 10 },     // 2분 동안 10명 유지 (복구 확인)
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000', 'p(99)<10000'], // Spike 상황이므로 임계값 완화
    http_req_failed: ['rate<0.05'],                    // 에러율 5% 미만
    coupon_issue_duration: ['p(95)<5000'],             // 쿠폰 발급 95% 5초 이내
  },
};

export function setup() {
  console.log('========================================');
  console.log('Spike Test 시작');
  console.log(`BASE_URL: ${BASE_URL}`);
  console.log(`COUPON_ID: ${COUPON_ID}`);
  console.log('목표: VU 10 → 500 (10초 급증), 5분간');
  console.log('주의: 선착순 이벤트 시작 시점을 시뮬레이션합니다');
  console.log('========================================');

  // 헬스 체크
  const isHealthy = healthCheck();
  if (!isHealthy) {
    throw new Error('Application is not healthy. Aborting test.');
  }

  return { startTime: new Date() };
}

export default function () {
  const userId = getRandomUserId();
  const response = issueCoupon(COUPON_ID, userId);

  validateIssueResponse(response);

  // Spike 상황이므로 대기 시간 최소화
  sleep(0.1 + Math.random() * 0.3);
}

export function teardown(data) {
  const endTime = new Date();
  const duration = (endTime - data.startTime) / 1000;

  console.log('========================================');
  console.log('Spike Test 완료');
  console.log(`시작 시간: ${data.startTime}`);
  console.log(`종료 시간: ${endTime}`);
  console.log(`실행 시간: ${duration.toFixed(2)}초`);
  console.log('재고 정확성 및 시스템 복구 확인이 필요합니다');
  console.log('========================================');
}
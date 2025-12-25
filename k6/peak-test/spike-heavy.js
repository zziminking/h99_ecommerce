/**
 * K6 Heavy Spike Test - 선착순 쿠폰 발급 (서버 한계점 테스트)
 *
 * 목적: 서버가 다운되는 시점 확인
 * 부하: VU 10명 → 1000명 (10초 내 급증) → 10명, 5분간
 *
 * 실행 방법:
 * k6 run k6/peak-test/spike-heavy.js
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
    { duration: '10s', target: 10 },     // 10초 동안 10명
    { duration: '10s', target: 1000 },   // 10초 만에 1000명으로 급증! (Heavy Spike!)
    { duration: '3m', target: 1000 },    // 3분 동안 1000명 유지
    { duration: '10s', target: 10 },     // 10초 만에 10명으로 감소
    { duration: '2m', target: 10 },      // 2분 동안 10명 유지 (복구 확인)
  ],
  thresholds: {
    http_req_duration: ['p(95)<10000', 'p(99)<15000'], // Spike 상황이므로 임계값 완화 (10초, 15초)
    http_req_failed: ['rate<0.1'],                      // 에러율 10% 미만
    coupon_issue_duration: ['p(95)<10000'],             // 쿠폰 발급 95% 10초 이내
  },
};

export function setup() {
  console.log('========================================');
  console.log('Heavy Spike Test 시작');
  console.log(`BASE_URL: ${BASE_URL}`);
  console.log(`COUPON_ID: ${COUPON_ID}`);
  console.log('목표: VU 10 → 1000 (10초 급증), 5분간');
  console.log('⚠️  주의: 서버 한계점을 테스트합니다!');
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
  sleep(0.05 + Math.random() * 0.1);
}

export function teardown(data) {
  const endTime = new Date();
  const duration = (endTime - data.startTime) / 1000;

  console.log('========================================');
  console.log('Heavy Spike Test 완료');
  console.log(`시작 시간: ${data.startTime}`);
  console.log(`종료 시간: ${endTime}`);
  console.log(`실행 시간: ${duration.toFixed(2)}초`);
  console.log('재고 정확성 및 시스템 복구 확인이 필요합니다');
  console.log('========================================');
}
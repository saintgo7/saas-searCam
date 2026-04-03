# SearCam 분석 및 메트릭 프레임워크

> 버전: v1.0
> 작성일: 2026-04-03
> 기반: project-plan.md v3.1

---

## 1. 핵심 지표 (North Star Metric)

### 주간 스캔 완료 수 (Weekly Completed Scans)

SearCam의 핵심 가치는 "사용자가 안전 점검을 완료하는 것"이다. 설치 수나 DAU보다 **실제로 스캔을 완료한 횟수**가 제품 가치를 가장 잘 나타낸다.

| 항목 | 정의 |
|------|------|
| 지표명 | Weekly Completed Scans (WCS) |
| 정의 | 7일간 스캔을 시작하여 결과 화면까지 도달한 고유 스캔 수 |
| 포함 | Quick Scan 완료, Full Scan 완료, 렌즈 찾기 세션 완료 (30초+) |
| 제외 | 스캔 시작 후 취소, 권한 거부로 중단, 5초 미만 EMF 세션 |
| 목표 | M3: 2,000/주, M6: 8,000/주, M12: 20,000/주 |

### North Star Metric과 하위 지표의 관계

```
주간 스캔 완료 수 (WCS)
├── 신규 사용자 스캔 = 신규 설치 x 온보딩 완료율 x 첫 스캔 시작율
├── 기존 사용자 스캔 = WAU x 주간 스캔 빈도
└── 스캔 완료율 = 스캔 시작 대비 결과 도달율
```

---

## 2. AARRR 프레임워크 적용

### 2.1 Acquisition (획득)

사용자가 앱을 발견하고 설치하는 단계.

| 지표 | 정의 | 목표 (M6) | 측정 방법 |
|------|------|----------|----------|
| 일간 설치 수 | Play Store에서 앱을 설치한 수 | 200/일 | Play Console |
| 설치 소스 분포 | 유기/유료/추천별 설치 비율 | 유기 60%+ | UTM + Play Console |
| ASO 키워드 순위 | 주요 키워드별 검색 순위 | 상위 5위 | ASO 도구 |
| Play Store 노출수 | 검색/탐색에서 앱 노출 횟수 | 100,000/월 | Play Console |
| Play Store CTR | 노출 대비 상세페이지 진입율 | 8%+ | Play Console |
| 설치 전환율 | 상세페이지 진입 대비 설치율 | 35%+ | Play Console |

#### 소스별 추적 (UTM 체계)

| 소스 | utm_source | utm_medium | utm_campaign |
|------|-----------|-----------|-------------|
| 인스타그램 | instagram | social | {campaign_name} |
| TikTok | tiktok | social | {campaign_name} |
| YouTube | youtube | social | {campaign_name} |
| Google UAC | google | cpc | {campaign_name} |
| 보도자료 | {매체명} | pr | launch_pr |
| 커뮤니티 | {커뮤니티명} | community | {campaign_name} |
| 인플루언서 | {인플루언서명} | influencer | {campaign_name} |
| 사용자 공유 | app_share | referral | {share_type} |

### 2.2 Activation (활성화)

설치 후 핵심 가치를 경험하는 단계.

| 지표 | 정의 | 목표 (M6) | 산식 |
|------|------|----------|------|
| 온보딩 완료율 | 온보딩 마지막 스텝 도달 비율 | 85%+ | 온보딩 완료 / 첫 실행 |
| 첫 스캔 시작율 | 설치 후 24시간 내 스캔 시작 비율 | 60%+ | 스캔 시작 / 설치 |
| 첫 스캔 완료율 | 첫 스캔을 결과까지 완료한 비율 | 50%+ | 첫 스캔 완료 / 설치 |
| Aha Moment 도달율 | 첫 스캔 결과에서 상세 정보 확인 비율 | 40%+ | 결과 상세 조회 / 첫 스캔 완료 |
| 권한 허용율 | Wi-Fi/위치/카메라 권한 모두 허용 비율 | 75%+ | 전체 권한 허용 / 권한 요청 |
| 시간 대 가치 (TTV) | 설치에서 첫 스캔 완료까지 시간 | 3분 이내 | 중앙값 측정 |

#### Activation 기준점 (Magic Number)

"설치 후 7일 이내에 2회 이상 스캔 완료한 사용자"는 30일 리텐션이 3배 높다는 가설을 검증한다.

### 2.3 Retention (유지)

사용자가 앱에 지속적으로 돌아오는 단계.

| 지표 | 정의 | 목표 (M6) |
|------|------|----------|
| D1 리텐션 | 설치 다음 날 앱 실행 비율 | 25%+ |
| D7 리텐션 | 설치 7일 후 앱 실행 비율 | 15%+ |
| D30 리텐션 | 설치 30일 후 앱 실행 비율 | 8%+ |
| WAU/MAU 비율 | 주간 활성 / 월간 활성 | 30%+ |
| 주간 스캔 빈도 | 활성 사용자의 주당 평균 스캔 횟수 | 1.5회 |
| 체크리스트 재사용율 | 체크리스트를 2회 이상 사용한 비율 | 20%+ |
| 리포트 조회율 | 과거 리포트를 다시 조회한 비율 | 15%+ |

#### 리텐션 트리거

| 트리거 | 시점 | 메시지 |
|--------|------|--------|
| 여행 시즌 알림 | 성수기 2주 전 | "여행 전 숙소 점검, 잊지 마세요" |
| 비활성 사용자 리텐션 | D7 미사용 시 | "마지막 스캔 이후 N일이 지났습니다" |
| 이사 시즌 알림 | 이사 성수기 전 | "새 집 입주 전 점검하세요" |
| 업데이트 알림 | 주요 업데이트 시 | "렌즈 감지 정확도가 향상되었습니다" |

### 2.4 Revenue (수익)

무료 사용자가 프리미엄으로 전환하는 단계.

| 지표 | 정의 | 목표 (M6) |
|------|------|----------|
| 프리미엄 전환율 | MAU 대비 유료 구독자 비율 | 2%+ |
| ARPU (전체) | 전체 사용자 평균 매출 | ₩60+/월 |
| ARPPU (유료) | 유료 사용자 평균 매출 | ₩2,900/월 |
| LTV | 사용자 생애 가치 | ₩768+ |
| 월간 MRR | 월간 반복 매출 | ₩100,000+ |
| 구독 유지율 | 월간 구독 갱신율 | 70%+ |
| 광고 eCPM | 광고 1,000회 노출당 수익 | $1.5+ |
| 광고 수익/DAU | DAU당 일일 광고 수익 | ₩7+ |

#### 전환 퍼널 주요 전환점

```
프리미엄 전환 트리거:
├── 리포트 11건째 저장 시도 → "무제한 저장은 프리미엄에서"
├── PDF 내보내기 시도 → "PDF 내보내기는 프리미엄에서"
├── 배너 광고 5회 노출 후 → "광고 제거하기"
└── Full Scan 3회 완료 후 → "고급 통계를 확인하세요"
```

### 2.5 Referral (추천)

사용자가 다른 사용자를 데려오는 단계.

| 지표 | 정의 | 목표 (M6) |
|------|------|----------|
| 공유율 | 스캔 완료 후 결과를 공유한 비율 | 5%+ |
| 공유당 설치 전환율 | 공유 링크를 통한 설치 비율 | 15%+ |
| K-factor (바이럴 계수) | 1명의 사용자가 유입시키는 신규 사용자 수 | 0.09+ |
| NPS (Net Promoter Score) | 추천 의향 점수 (-100~100) | 30+ |
| 앱 리뷰 작성율 | 활성 사용자 대비 리뷰 작성 비율 | 3%+ |
| 초대 코드 사용률 | (Phase 2) 초대 코드로 설치한 비율 | 5%+ |

---

## 3. 이벤트 택소노미 (Event Taxonomy)

### 3.1 설계 원칙

1. **네이밍 규칙**: `{object}_{action}` 형태 (예: `scan_started`, `report_saved`)
2. **공통 파라미터**: 모든 이벤트에 `user_id`, `session_id`, `timestamp`, `app_version` 포함
3. **PII 배제**: 개인 식별 정보는 수집하지 않음
4. **최소 수집**: 분석에 필요한 파라미터만 수집

### 3.2 이벤트 목록 (53개)

#### 앱 기본 (6개)

| # | 이벤트명 | 파라미터 | 트리거 | 분석 용도 |
|---|---------|---------|--------|----------|
| 1 | `app_opened` | `source`, `is_first_open` | 앱 실행 | DAU, 설치 소스 |
| 2 | `app_backgrounded` | `session_duration` | 백그라운드 전환 | 세션 길이 |
| 3 | `app_updated` | `from_version`, `to_version` | 앱 업데이트 | 업데이트 채택율 |
| 4 | `permission_requested` | `permission_type` | 권한 요청 시 | 권한 허용율 |
| 5 | `permission_granted` | `permission_type` | 권한 허용 시 | 권한 허용율 |
| 6 | `permission_denied` | `permission_type` | 권한 거부 시 | 이탈 원인 |

#### 온보딩 (5개)

| # | 이벤트명 | 파라미터 | 트리거 | 분석 용도 |
|---|---------|---------|--------|----------|
| 7 | `onboarding_started` | - | 온보딩 시작 | 진입율 |
| 8 | `onboarding_step_viewed` | `step_number`, `step_name` | 각 스텝 진입 | 이탈 스텝 |
| 9 | `onboarding_completed` | `total_duration` | 마지막 스텝 완료 | 완료율 |
| 10 | `onboarding_skipped` | `skipped_at_step` | 건너뛰기 탭 | 건너뛰기 지점 |
| 11 | `onboarding_disclaimer_accepted` | - | 면책조항 동의 | 동의율 |

#### 스캔 (15개)

| # | 이벤트명 | 파라미터 | 트리거 | 분석 용도 |
|---|---------|---------|--------|----------|
| 12 | `scan_started` | `scan_type`, `wifi_connected` | 스캔 버튼 탭 | 스캔 시작 수 |
| 13 | `scan_layer_started` | `layer_type` | 각 레이어 시작 | 레이어별 진행율 |
| 14 | `scan_layer_completed` | `layer_type`, `duration`, `result_score` | 각 레이어 완료 | 레이어별 성능 |
| 15 | `scan_layer_skipped` | `layer_type`, `reason` | 레이어 스킵 | 스킵 사유 분석 |
| 16 | `scan_completed` | `scan_type`, `total_duration`, `risk_level`, `risk_score` | 스캔 완료 | 핵심 지표 |
| 17 | `scan_cancelled` | `scan_type`, `cancelled_at_layer`, `elapsed_time` | 스캔 취소 | 이탈 지점 |
| 18 | `scan_error` | `scan_type`, `error_type`, `error_message` | 스캔 오류 | 오류 분석 |
| 19 | `device_found` | `device_type`, `risk_level`, `detection_method` | 기기 발견 | 탐지 현황 |
| 20 | `lens_point_detected` | `confidence`, `point_size`, `detection_stage` | 렌즈 의심 포인트 | 렌즈 감지 정확도 |
| 21 | `emf_anomaly_detected` | `magnitude_delta`, `duration` | 자기장 이상 감지 | EMF 감지 정확도 |
| 22 | `cross_validation_result` | `layers_positive`, `correction_factor`, `final_score` | 교차 검증 완료 | 교차검증 효과 |
| 23 | `scan_mode_selected` | `mode_type` | 스캔 모드 선택 | 모드 선호도 |
| 24 | `lens_finder_session_started` | - | 렌즈 찾기 진입 | 렌즈 찾기 사용량 |
| 25 | `lens_finder_session_ended` | `duration`, `points_found` | 렌즈 찾기 종료 | 렌즈 찾기 효과 |
| 26 | `ir_mode_session` | `duration`, `points_found`, `ambient_light` | IR 모드 사용 | IR 사용 현황 |

#### 결과 (6개)

| # | 이벤트명 | 파라미터 | 트리거 | 분석 용도 |
|---|---------|---------|--------|----------|
| 27 | `result_viewed` | `risk_level`, `risk_score` | 결과 화면 진입 | 결과 조회 |
| 28 | `result_detail_expanded` | `detail_type` (wifi/emf/ir/lens) | 상세 정보 펼침 | 관심 정보 |
| 29 | `result_shared` | `share_channel`, `risk_level` | 결과 공유 | 공유율 |
| 30 | `result_action_tapped` | `action_type` (report/rescan/call112) | 액션 버튼 탭 | 사후 행동 |
| 31 | `false_positive_reported` | `reported_layer`, `device_type` | 오탐 신고 | 오탐률 추적 |
| 32 | `emergency_call_initiated` | `risk_score` | 112 신고 버튼 탭 | 긴급 사용 현황 |

#### 리포트 (6개)

| # | 이벤트명 | 파라미터 | 트리거 | 분석 용도 |
|---|---------|---------|--------|----------|
| 33 | `report_saved` | `risk_level`, `scan_type` | 리포트 저장 | 저장 빈도 |
| 34 | `report_viewed` | `report_age_days` | 과거 리포트 열람 | 리포트 재조회 |
| 35 | `report_deleted` | `report_age_days` | 리포트 삭제 | 저장 패턴 |
| 36 | `report_pdf_exported` | `is_premium` | PDF 내보내기 | 프리미엄 전환 트리거 |
| 37 | `report_limit_reached` | `report_count` | 무료 저장 한도 도달 | 프리미엄 전환 트리거 |
| 38 | `report_list_viewed` | `report_count` | 리포트 목록 진입 | 리포트 사용 현황 |

#### 체크리스트 (5개)

| # | 이벤트명 | 파라미터 | 트리거 | 분석 용도 |
|---|---------|---------|--------|----------|
| 39 | `checklist_started` | `checklist_type` (accommodation/bathroom) | 체크리스트 시작 | 체크리스트 사용량 |
| 40 | `checklist_item_checked` | `item_index`, `item_name` | 항목 체크 | 항목별 완료율 |
| 41 | `checklist_completed` | `checklist_type`, `total_items`, `checked_items` | 모든 항목 체크 | 완료율 |
| 42 | `checklist_abandoned` | `checklist_type`, `checked_count`, `total_count` | 미완료 이탈 | 이탈 지점 |
| 43 | `checklist_shared` | `checklist_type` | 체크리스트 공유 | 공유 기여 |

#### 프리미엄 (5개)

| # | 이벤트명 | 파라미터 | 트리거 | 분석 용도 |
|---|---------|---------|--------|----------|
| 44 | `premium_prompt_shown` | `trigger_type`, `placement` | 프리미엄 안내 노출 | 전환 퍼널 |
| 45 | `premium_page_viewed` | `trigger_source` | 프리미엄 페이지 진입 | 관심도 |
| 46 | `premium_purchase_started` | `plan_type` (monthly/yearly) | 결제 시작 | 전환 퍼널 |
| 47 | `premium_purchase_completed` | `plan_type`, `price`, `currency` | 결제 완료 | 매출 |
| 48 | `premium_cancelled` | `subscription_duration_days`, `cancel_reason` | 구독 취소 | 이탈 원인 |

#### 설정/기타 (5개)

| # | 이벤트명 | 파라미터 | 트리거 | 분석 용도 |
|---|---------|---------|--------|----------|
| 49 | `settings_changed` | `setting_name`, `old_value`, `new_value` | 설정 변경 | 설정 선호도 |
| 50 | `emf_sensitivity_changed` | `sensitivity_level` | 감도 변경 | 감도 선호도 |
| 51 | `notification_received` | `notification_type` | 푸시 수신 | 알림 도달율 |
| 52 | `notification_opened` | `notification_type` | 푸시 탭 | 알림 효과 |
| 53 | `feedback_submitted` | `feedback_type`, `rating` | 인앱 피드백 | 만족도 |

---

## 4. 퍼널 분석 설계

### 4.1 온보딩 퍼널

```
앱 첫 실행 (app_opened, is_first_open=true)
  │ 목표: 100%
  ▼
온보딩 시작 (onboarding_started)
  │ 목표: 95%
  ▼
온보딩 스텝 1: 앱 소개 (onboarding_step_viewed, step=1)
  │ 목표: 90%
  ▼
온보딩 스텝 2: 기능 설명 (onboarding_step_viewed, step=2)
  │ 목표: 88%
  ▼
온보딩 스텝 3: 권한 안내 (onboarding_step_viewed, step=3)
  │ 목표: 85%
  ▼
면책조항 동의 (onboarding_disclaimer_accepted)
  │ 목표: 83%
  ▼
온보딩 완료 (onboarding_completed)
  │ 목표: 80%
  ▼
첫 스캔 시작 (scan_started, 24시간 내)
  │ 목표: 60%
  ▼
첫 스캔 완료 (scan_completed)
  │ 목표: 50%
```

**핵심 드롭오프 지점**: 온보딩 완료 -> 첫 스캔 시작 (20%p 이탈 예상)
**대응**: 온보딩 완료 직후 Quick Scan CTA 버튼 즉시 표시

### 4.2 스캔 퍼널

```
홈 화면 진입
  │
  ▼
스캔 모드 선택 (scan_mode_selected)
  │
  ▼
스캔 시작 (scan_started)
  │ 목표: 100%
  ▼
Layer 1 완료 (scan_layer_completed, layer=wifi)
  │ 목표: 95% (Quick Scan) / 90% (Full Scan)
  ▼
Layer 2 완료 (scan_layer_completed, layer=lens)
  │ 목표: N/A (Quick) / 85% (Full Scan)
  ▼
Layer 3 완료 (scan_layer_completed, layer=emf)
  │ 목표: N/A (Quick) / 80% (Full Scan)
  ▼
교차 검증 완료 (cross_validation_result)
  │ 목표: N/A (Quick) / 78% (Full Scan)
  ▼
결과 화면 (result_viewed)
  │ 목표: 92% (Quick) / 75% (Full)
  ▼
결과 상세 확인 (result_detail_expanded)
  │ 목표: 40%
  ▼
사후 행동 (result_action_tapped)
  │ 목표: 25%
```

### 4.3 프리미엄 전환 퍼널

```
프리미엄 트리거 발생 (report_limit_reached / report_pdf_exported)
  │ 목표: DAU의 15%가 트리거 경험
  ▼
프리미엄 안내 노출 (premium_prompt_shown)
  │ 목표: 100%
  ▼
프리미엄 페이지 진입 (premium_page_viewed)
  │ 목표: 20%
  ▼
결제 시작 (premium_purchase_started)
  │ 목표: 8%
  ▼
결제 완료 (premium_purchase_completed)
  │ 목표: 5%

전체 전환율: DAU의 15% x 5% = 0.75% → 목표 2%에 도달하려면 트리거 빈도 또는 전환율 개선 필요
```

---

## 5. 코호트 분석 설계

### 5.1 설치 주차별 코호트

| 분석 축 | 코호트 정의 | 관찰 기간 | 핵심 지표 |
|---------|-----------|----------|----------|
| 설치 주차 | 같은 주에 설치한 사용자 그룹 | 12주 | D1/D7/D30 리텐션 |
| 설치 소스 | 같은 채널에서 유입된 사용자 그룹 | 8주 | 리텐션, 전환율 |
| 첫 스캔 결과 | 첫 스캔 위험도별 그룹 (안전/위험) | 4주 | 재방문율 |
| 디바이스 | Android 버전/제조사별 그룹 | 12주 | 스캔 완료율, 오류율 |

### 5.2 행동 기반 코호트

| 코호트 | 정의 | 비교 대상 | 가설 |
|--------|------|----------|------|
| Power User | 주 3회+ 스캔 | 일반 사용자 | 프리미엄 전환율 높음 |
| 여행자 | 스캔 위치가 자주 변경 | 고정 위치 사용자 | 시즌별 사용 패턴 |
| Full Scan 선호 | Full Scan 50%+ | Quick Scan 선호 | 리텐션 높음 |
| 체크리스트 사용자 | 체크리스트 1회+ 사용 | 미사용자 | 앱 만족도 높음 |

### 5.3 코호트 리텐션 테이블 (목표)

| 코호트 | D1 | D7 | D14 | D30 | D60 | D90 |
|--------|-----|-----|------|------|------|------|
| 전체 | 25% | 15% | 10% | 8% | 5% | 4% |
| Power User | 60% | 45% | 35% | 30% | 25% | 22% |
| 인플루언서 유입 | 20% | 10% | 7% | 5% | 3% | 2% |
| 유기 검색 유입 | 30% | 20% | 14% | 10% | 7% | 6% |

---

## 6. A/B 테스트 프레임워크

### 6.1 테스트 프로세스

```
1. 가설 수립
   └─ "X를 Y로 변경하면 Z 지표가 N% 개선될 것이다"

2. 표본 크기 계산
   └─ 최소 검정력 80%, 유의수준 5% (p < 0.05)
   └─ 예: 전환율 2% → 3% 개선 검증에 약 4,000명/그룹 필요

3. 실험 설계
   └─ 대조군(A): 기존 버전
   └─ 실험군(B): 변경 버전
   └─ 무작위 배정 (user_id 해시 기반)

4. 실험 실행
   └─ 최소 2주 (요일 효과 제거)
   └─ 중간 분석 금지 (p-hacking 방지)

5. 결과 분석
   └─ 주 지표 + 가드레일 지표 확인
   └─ 통계적 유의성 확인
   └─ 실질적 효과 크기 판단

6. 의사결정
   └─ 유의미하고 실질적 → 롤아웃
   └─ 유의미하지만 실질적이지 않음 → 보류
   └─ 유의미하지 않음 → 원복
```

### 6.2 우선순위 테스트 목록

| 우선순위 | 테스트명 | 가설 | 주 지표 | 가드레일 |
|---------|---------|------|---------|---------|
| P0 | 온보딩 스텝 수 (3 vs 5) | 3스텝이 온보딩 완료율을 15%+ 개선 | 온보딩 완료율 | 첫 스캔 완료율 |
| P0 | Quick Scan CTA 문구 | "30초 점검" vs "안전 확인" | 스캔 시작율 | 스캔 완료율 |
| P1 | 결과 화면 레이아웃 | 점수 강조 vs 기기 목록 강조 | 결과 상세 확인율 | 공유율 |
| P1 | 프리미엄 트리거 시점 | 리포트 11건 vs 6건 | 프리미엄 전환율 | 리텐션 |
| P2 | 광고 위치 | 홈 하단 vs 결과 후 | 광고 eCPM | 리텐션, 평점 |
| P2 | EMF 감도 기본값 | 보통 vs 민감 | 스캔 만족도 | 오탐 신고율 |

---

## 7. 대시보드 설계

### 7.1 실시간 대시보드 (일간)

| 섹션 | 지표 | 시각화 |
|------|------|--------|
| 헤더 | DAU, 일간 스캔 수, 일간 설치 수 | 큰 숫자 + 전일 대비 변동 |
| 스캔 현황 | 스캔 유형별 분포, 완료율, 평균 시간 | 도넛 차트 + 바 차트 |
| 탐지 현황 | 위험도 분포 (안전/관심/주의/위험/매우위험) | 스택 바 차트 |
| 오류 | 스캔 오류율, 오류 유형 top 5 | 라인 차트 + 테이블 |
| 오탐 | 오탐 신고 수, 신고 레이어별 분포 | 바 차트 |

### 7.2 주간 리뷰 대시보드

| 섹션 | 지표 | 시각화 |
|------|------|--------|
| North Star | WCS (주간 스캔 완료 수) 추이 | 라인 차트 (12주) |
| Acquisition | 설치 수, 소스별 분포, ASO 순위 | 혼합 차트 |
| Activation | 온보딩 퍼널, 첫 스캔 완료율 | 퍼널 차트 |
| Retention | D1/D7/D30 추이, WAU/MAU | 라인 차트 |
| Revenue | MRR, 전환율, ARPU | 라인 차트 + KPI 카드 |
| Referral | 공유율, K-factor | 라인 차트 |

### 7.3 월간 비즈니스 대시보드

| 섹션 | 내용 |
|------|------|
| Executive Summary | 핵심 KPI 3~5개, 전월 대비 변동 |
| 성장 | MAU 추이, 설치 추이, 리텐션 코호트 |
| 수익 | MRR, LTV, CAC, LTV:CAC 비율 |
| 제품 | 스캔 완료율, 오탐률, NPS |
| 마케팅 | 채널별 ROI, CAC, 바이럴 계수 |

---

## 8. 데이터 수집 원칙

### 8.1 최소 수집 원칙

| 원칙 | 구현 |
|------|------|
| 최소 수집 | 분석에 직접 필요한 데이터만 수집 |
| 익명화 | user_id는 랜덤 UUID, 개인정보 미수집 |
| 로컬 우선 | 스캔 결과는 기기 로컬에만 저장 (기본) |
| 옵트인 | 분석 데이터 수집은 사용자 동의 후 |
| 투명성 | 수집하는 데이터 목록을 앱 내에서 조회 가능 |

### 8.2 수집하지 않는 데이터

| 데이터 | 이유 |
|--------|------|
| GPS 좌표 | 사용자 위치 프라이버시 |
| Wi-Fi SSID 이름 | 숙소 식별 가능 |
| MAC 주소 원본 | 네트워크 기기 개인정보 |
| 카메라 프레임 | 촬영 내용 프라이버시 |
| IP 주소 | 네트워크 식별 정보 |

### 8.3 데이터 보존 정책

| 데이터 유형 | 보존 기간 | 근거 |
|-----------|----------|------|
| 이벤트 로그 (원시) | 90일 | 분석 후 집계 데이터만 보존 |
| 집계 데이터 | 2년 | 트렌드 분석용 |
| 코호트 데이터 | 1년 | 리텐션 분석용 |
| A/B 테스트 결과 | 영구 | 의사결정 기록 |

### 8.4 분석 도구 스택

| 도구 | 용도 | 비용 |
|------|------|------|
| Firebase Analytics | 이벤트 수집 + 기본 분석 | 무료 |
| BigQuery | 원시 데이터 쿼리 + 코호트 분석 | 종량제 |
| Looker Studio | 대시보드 시각화 | 무료 |
| Firebase A/B Testing | A/B 테스트 실행 | 무료 |
| Firebase Crashlytics | 크래시 분석 | 무료 |

---

*본 프레임워크는 SearCam 프로젝트 계획서 v3.1을 기반으로 설계되었습니다.*
*이벤트 택소노미는 구현 시 Firebase Analytics SDK 기반으로 적용됩니다.*
*데이터 수집은 개인정보 보호법 및 Google Play 개인정보 정책을 준수합니다.*

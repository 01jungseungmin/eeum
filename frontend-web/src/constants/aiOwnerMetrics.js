// 운영 위험 조기경보 — 사장님이 직접 입력하는 실측값 항목 (백엔드 AiMetricType과 동일)
// integer: 정수만 받는 항목, max: 화면에서만 거는 상한
export const OWNER_METRIC_GROUPS = [
  {
    label: '전력',
    metrics: [
      { type: 'MONTHLY_POWER_KWH', label: '월 전력 사용량', unit: 'kWh' },
      {
        type: 'MONTHLY_POWER_BILL',
        label: '월 전기요금',
        unit: '원',
        integer: true,
      },
    ],
  },
  {
    label: '영업',
    metrics: [
      {
        type: 'OPEN_HOURS_PER_DAY',
        label: '하루 영업시간',
        unit: '시간',
        max: 24,
      },
      {
        type: 'BUSINESS_DAYS_PER_MONTH',
        label: '월 영업일수',
        unit: '일',
        integer: true,
        max: 31,
      },
    ],
  },
  {
    label: '설비',
    metrics: [
      {
        type: 'AIR_CONDITIONER_COUNT',
        label: '에어컨 대수',
        unit: '대',
        integer: true,
      },
      {
        type: 'REFRIGERATOR_COUNT',
        label: '냉장·냉동 설비 대수',
        unit: '대',
        integer: true,
      },
      {
        type: 'HEATING_EQUIPMENT_COUNT',
        label: '난방 설비 대수',
        unit: '대',
        integer: true,
      },
      {
        type: 'GAS_EQUIPMENT_COUNT',
        label: '가스 사용 설비 대수',
        unit: '대',
        integer: true,
      },
      {
        type: 'EQUIPMENT_COUNT',
        label: '전체 주요 설비 대수',
        unit: '대',
        integer: true,
      },
    ],
  },
  {
    label: '가스',
    metrics: [
      { type: 'MONTHLY_GAS_USAGE_M3', label: '월 가스 사용량', unit: 'm³' },
      {
        type: 'MONTHLY_GAS_BILL',
        label: '월 가스요금',
        unit: '원',
        integer: true,
      },
    ],
  },
];

export const OWNER_METRICS = OWNER_METRIC_GROUPS.flatMap(
  (group) => group.metrics,
);

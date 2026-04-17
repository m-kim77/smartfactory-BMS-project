import { useI18n } from 'vue-i18n';

const FACTORIES = {
  '청림공장': 'Cheongrim Plant',
  '은하공장': 'Eunha Plant',
  '백운공장': 'Baegun Plant',
  '단풍공장': 'Danpung Plant',
  '태양공장': 'Taeyang Plant',
  '한빛공장': 'Hanbit Plant',
};

const REGIONS = {
  '청림': 'Cheongrim',
  '은하': 'Eunha',
  '백운': 'Baegun',
  '단풍': 'Danpung',
  '태양': 'Taeyang',
  '한빛': 'Hanbit',
};

const COUNTRIES = {
  '에버랜드': 'EverLand',
  '미국': 'USA',
  '독일': 'Germany',
  '영국': 'UK',
  '프랑스': 'France',
  '일본': 'Japan',
  '중국': 'China',
  '인도': 'India',
  '호주': 'Australia',
  '캐나다': 'Canada',
};

const BRANDS = {
  '노바': 'Nova',
  '벡터': 'Vector',
};

const MODELS = {
  '볼트 S': 'Volt S',
  '노바 X5': 'Nova X5',
  '노바 X9': 'Nova X9',
  '노바 GT60': 'Nova GT60',
  '노바 GT70e': 'Nova GT70e',
  '시티버스 E': 'CityBus E',
  '벡터 E3': 'Vector E3',
  '벡터 E4': 'Vector E4',
  '벡터 E6': 'Vector E6',
  '벡터 E9': 'Vector E9',
  '벡터 V5': 'Vector V5',
  '벡터 밴 EV': 'Vector Van EV',
};

const USER_NAMES = {
  '관리자': 'Administrator',
  '운영자': 'Operator',
};

const STATUS_REASONS = {
  '최초 입고': 'Initial arrival',
  '검사 시작': 'Inspection started',
  '검사 실패 항목 발견': 'Failure items detected',
  '자동 QA 진입': 'Auto QA entry',
  '수리 완료 — 재검사 대기': 'Repair complete — awaiting re-inspection',
  '재검사 시작': 'Re-inspection started',
  '출고 대기': 'Awaiting shipment',
  '출고 완료': 'Shipment complete',
  '모든 검사 통과': 'All inspections passed',
  '재검사 통과': 'Re-inspection passed',
  '자동 발생': 'Auto-triggered',
  '운영자 해결': 'Resolved by operator',
  '자동 수리 완료': 'Auto-repair complete',
  '확인': 'Acknowledged',
};

const STEP_LABELS = {
  'SOC 검사': 'SOC check',
  'SOH 검사': 'SOH check',
  'SOP 검사': 'SOP check',
  '팩 전압 검사': 'Pack voltage check',
  '셀 온도 검사': 'Cell temperature check',
  '셀 전압 검사': 'Cell voltage check',
};

const MAPS = {
  factory: FACTORIES,
  region: REGIONS,
  country: COUNTRIES,
  brand: BRANDS,
  model: MODELS,
  userName: USER_NAMES,
  reason: STATUS_REASONS,
  step: STEP_LABELS,
};

function translateAlertMessage(s) {
  if (!s) return s;
  let out = s;
  // "{step} 실패: {note}" — translate step prefix
  out = out.replace(/^(SOC 검사|SOH 검사|SOP 검사|팩 전압 검사|셀 온도 검사|셀 전압 검사) 실패: /, (_, step) => `${STEP_LABELS[step] || step} failed: `);
  // "{step} 시작" — trailing standalone
  out = out.replace(/^(SOC 검사|SOH 검사|SOP 검사|팩 전압 검사|셀 온도 검사|셀 전압 검사) 시작$/, (_, step) => `${STEP_LABELS[step] || step} started`);
  // Metric anomaly patterns
  out = out.replace(/SOC 이상치 ([\d.]+)%/g, 'SOC anomaly $1%');
  out = out.replace(/SOH 이상치 ([\d.]+)%/g, 'SOH anomaly $1%');
  out = out.replace(/SOP 이상치 ([\d.]+)%/g, 'SOP anomaly $1%');
  out = out.replace(/팩 전압 이상치 ([\d.]+)V/g, 'Pack voltage anomaly $1V');
  out = out.replace(/셀 #(\d+) 온도 이상 ([\d.]+)℃/g, 'Cell #$1 temperature anomaly $2℃');
  out = out.replace(/셀 #(\d+) 전압 이상 ([\d.]+)V/g, 'Cell #$1 voltage anomaly $2V');
  return out;
}

function translateReason(s) {
  if (!s) return s;
  if (STATUS_REASONS[s]) return STATUS_REASONS[s];
  // "{step} 시작"
  const m = s.match(/^(SOC 검사|SOH 검사|SOP 검사|팩 전압 검사|셀 온도 검사|셀 전압 검사) 시작$/);
  if (m) return `${STEP_LABELS[m[1]]} started`;
  return translateAlertMessage(s);
}

export function useLabels() {
  const { locale } = useI18n();
  const isEn = () => locale.value === 'en';
  const lookup = (map) => (v) => (isEn() && v != null && map[v]) || v;
  return {
    factory: lookup(FACTORIES),
    region: lookup(REGIONS),
    country: lookup(COUNTRIES),
    brand: lookup(BRANDS),
    model: lookup(MODELS),
    userName: lookup(USER_NAMES),
    step: lookup(STEP_LABELS),
    reason: (v) => (isEn() ? translateReason(v) : v),
    alertMessage: (v) => (isEn() ? translateAlertMessage(v) : v),
  };
}

export { MAPS };

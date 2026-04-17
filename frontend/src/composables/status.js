export const STATUS_LABELS_KR = {
  ARRIVAL: '입고',
  BATTERY_INSPECTION: '배터리 검사중',
  CELL_INSPECTION: '셀 검사중',
  ANOMALY_DETECTED: '이상 발생',
  QA_MAINTENANCE: '정비중(QA)',
  RE_INSPECTION_WAITING: '재검사 대기',
  RE_INSPECTION: '재검사중',
  BATTERY_QC_COMPLETE: '배터리 품질검사 완료',
  SHIPMENT_WAITING: '출고대기',
  SHIPMENT_COMPLETE: '출고완료',
};

export const STATUS_ORDER = [
  'ARRIVAL', 'BATTERY_INSPECTION', 'CELL_INSPECTION',
  'ANOMALY_DETECTED', 'QA_MAINTENANCE', 'RE_INSPECTION_WAITING', 'RE_INSPECTION',
  'BATTERY_QC_COMPLETE', 'SHIPMENT_WAITING', 'SHIPMENT_COMPLETE',
];

export function statusColor(status) {
  switch (status) {
    case 'ARRIVAL': return 'badge-gray';
    case 'BATTERY_INSPECTION':
    case 'CELL_INSPECTION':
    case 'RE_INSPECTION':
      return 'badge-blue';
    case 'ANOMALY_DETECTED':
    case 'QA_MAINTENANCE':
      return 'badge-red';
    case 'RE_INSPECTION_WAITING':
      return 'badge-yellow';
    case 'BATTERY_QC_COMPLETE':
      return 'badge-green';
    case 'SHIPMENT_WAITING':
      return 'badge-yellow';
    case 'SHIPMENT_COMPLETE':
      return 'badge-green';
    default:
      return 'badge-gray';
  }
}

export function severityColor(s) {
  if (s === 'CRITICAL' || s === 'HIGH') return 'badge-red';
  if (s === 'MEDIUM') return 'badge-yellow';
  return 'badge-blue';
}

export function alertStatusColor(s) {
  if (s === 'OPEN') return 'badge-red';
  if (s === 'ACKNOWLEDGED') return 'badge-yellow';
  return 'badge-green';
}

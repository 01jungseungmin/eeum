package com.eeum.eeum.application.external.service;

import com.eeum.eeum.domain.external.entity.ExternalBuildingEnergyStat;
import com.eeum.eeum.domain.external.entity.ExternalDataImportHistory;
import com.eeum.eeum.domain.external.entity.ExternalEnergyUsageStat;
import com.eeum.eeum.domain.external.enums.ExternalImportStatus;
import com.eeum.eeum.domain.external.repository.ExternalBuildingEnergyStatRepository;
import com.eeum.eeum.domain.external.repository.ExternalDataImportHistoryRepository;
import com.eeum.eeum.domain.external.repository.ExternalEnergyUsageStatRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공공데이터 파일(CSV) 수동 Import — 관리자가 다운로드한 최신 파일을 내부 DB에 적재한다.
 * - 같은 (sourceId, 기준월) 재적재 시 삭제 후 삽입으로 멱등 처리
 * - 건물에너지(대용량)는 법정동/월 단위로 집계해서만 저장
 * - 원본 파일 내용은 로그에 남기지 않는다 (행 수/실패 수만 기록)
 * XLSX는 CSV로 변환 후 업로드 (자동 변환은 3차)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalDataImportService {

    private static final String ELECTRIC_USAGE_SOURCE_ID = "15101311";
    private static final String ELECTRIC_USAGE_NAME = "한국전력공사_용도별 전력사용량";
    private static final String BUILDING_ENERGY_SOURCE_ID = "15054214";
    private static final String BUILDING_ENERGY_NAME = "국토교통부_건물에너지 전기에너지";

    private static final List<String> PERIOD_KEYS = List.of("기준년월", "년월", "사용년월", "기준일자", "연월");
    private static final List<String> SIDO_KEYS = List.of("시도", "시도명");
    private static final List<String> SIGUNGU_KEYS = List.of("시군구", "시군구명");
    private static final List<String> DONG_KEYS = List.of("법정동", "법정동명", "행정동");
    private static final List<String> DONG_CODE_KEYS = List.of("법정동코드", "법정동시군구코드");
    private static final List<String> USAGE_KEYS = List.of("사용량", "판매량", "전력사용량", "전기사용량", "사용량(kWh)", "에너지사용량");
    private static final List<String> USAGE_TYPE_KEYS = List.of("용도", "계약종별", "계약구분", "용도별");
    private static final List<String> CHARGE_KEYS = List.of("판매요금", "요금", "전기요금");
    private static final List<String> CUSTOMER_KEYS = List.of("고객호수", "호수", "고객수");

    private final ExternalEnergyUsageStatRepository energyUsageStatRepository;
    private final ExternalBuildingEnergyStatRepository buildingEnergyStatRepository;
    private final ExternalDataImportHistoryRepository importHistoryRepository;
    private final ExternalImportFailureRecorder failureRecorder;

    @Transactional(readOnly = true)
    public Page<ExternalDataImportHistory> getImportHistory(Pageable pageable) {
        return importHistoryRepository.findAllByOrderByImportedAtDesc(pageable);
    }

    // 한국전력공사_용도별 전력사용량 Import
    @Transactional
    public ExternalDataImportHistory importElectricUsageType(MultipartFile file, LocalDate sourceUpdatedAt) {
        CsvContent csv = parse(file);
        int failedRows = 0;
        Map<String, List<ExternalEnergyUsageStat>> statsByPeriod = new LinkedHashMap<>();

        for (Map<String, String> row : csv.rows()) {
            String period = normalizePeriod(value(row, PERIOD_KEYS));
            BigDecimal usage = decimal(value(row, USAGE_KEYS));
            if (period == null || usage == null) {
                failedRows++;
                continue;
            }
            BigDecimal charge = decimal(value(row, CHARGE_KEYS));
            Long customers = longValue(value(row, CUSTOMER_KEYS));
            BigDecimal unitPrice = (charge != null && usage.compareTo(BigDecimal.ZERO) > 0)
                    ? charge.divide(usage, 2, java.math.RoundingMode.HALF_UP) : null;
            statsByPeriod.computeIfAbsent(period, key -> new ArrayList<>())
                    .add(ExternalEnergyUsageStat.create(
                            ELECTRIC_USAGE_NAME, ELECTRIC_USAGE_SOURCE_ID, period,
                            value(row, SIDO_KEYS), value(row, SIGUNGU_KEYS),
                            value(row, DONG_CODE_KEYS), value(row, DONG_KEYS),
                            value(row, USAGE_TYPE_KEYS), usage, charge, customers, unitPrice, sourceUpdatedAt));
        }

        int successRows = statsByPeriod.values().stream().mapToInt(List::size).sum();
        validateAnySuccess(csv, successRows, ELECTRIC_USAGE_NAME, file, sourceUpdatedAt, failedRows);

        // 멱등 — 같은 기준월 재적재 시 교체
        statsByPeriod.forEach((period, stats) -> {
            energyUsageStatRepository.deleteBySourceIdAndSourcePeriod(ELECTRIC_USAGE_SOURCE_ID, period);
            energyUsageStatRepository.saveAll(stats);
        });
        return saveHistory(ELECTRIC_USAGE_NAME, ELECTRIC_USAGE_SOURCE_ID, file, sourceUpdatedAt,
                csv.rows().size(), successRows, failedRows, null);
    }

    // 국토교통부_건물에너지 전기에너지 Import — 법정동/월 단위 집계 적재 (sigunguFilter로 서비스 지역만)
    @Transactional
    public ExternalDataImportHistory importBuildingElectricEnergy(
            MultipartFile file, LocalDate sourceUpdatedAt, String sigunguFilter) {
        CsvContent csv = parse(file);
        int failedRows = 0;

        // (기준월|시군구|법정동) 단위 집계 — 대용량 원본을 행 단위로 저장하지 않는다
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (Map<String, String> row : csv.rows()) {
            String period = normalizePeriod(value(row, PERIOD_KEYS));
            BigDecimal usage = decimal(value(row, USAGE_KEYS));
            if (period == null || usage == null) {
                failedRows++;
                continue;
            }
            String sigungu = value(row, SIGUNGU_KEYS);
            if (sigunguFilter != null && !sigunguFilter.isBlank()
                    && (sigungu == null || !sigungu.contains(sigunguFilter))) {
                continue; // 서비스 지역 외 데이터는 스킵
            }
            String dong = value(row, DONG_KEYS);
            String aggregateKey = period + "|" + sigungu + "|" + dong;
            Aggregate aggregate = aggregates.computeIfAbsent(aggregateKey, key -> new Aggregate(
                    period, value(row, SIDO_KEYS), sigungu, value(row, DONG_CODE_KEYS), dong));
            aggregate.usageKwh = aggregate.usageKwh.add(usage);
            aggregate.buildingCount++;
        }

        int successRows = aggregates.size();
        validateAnySuccess(csv, successRows, BUILDING_ENERGY_NAME, file, sourceUpdatedAt, failedRows);

        aggregates.values().stream()
                .map(Aggregate::period).distinct()
                .forEach(period -> buildingEnergyStatRepository
                        .deleteBySourceIdAndSourcePeriod(BUILDING_ENERGY_SOURCE_ID, period));
        buildingEnergyStatRepository.saveAll(aggregates.values().stream()
                .map(aggregate -> ExternalBuildingEnergyStat.create(
                        BUILDING_ENERGY_NAME, BUILDING_ENERGY_SOURCE_ID, aggregate.period,
                        aggregate.sido, aggregate.sigungu, aggregate.dongCode, aggregate.dongName,
                        aggregate.usageKwh, aggregate.buildingCount, null, sourceUpdatedAt))
                .toList());
        return saveHistory(BUILDING_ENERGY_NAME, BUILDING_ENERGY_SOURCE_ID, file, sourceUpdatedAt,
                csv.rows().size(), successRows, failedRows, null);
    }

    // ===================== 내부 유틸 =====================

    // 실패 이력은 REQUIRES_NEW로 즉시 커밋한다 — 이 메서드 직후 던지는 예외로 호출부 트랜잭션이 롤백되어도
    // "적재 실패했다"는 감사 이력 자체는 남아 있어야 한다.
    private void validateAnySuccess(CsvContent csv, int successRows, String dataName,
                                    MultipartFile file, LocalDate sourceUpdatedAt, int failedRows) {
        if (successRows == 0) {
            String failureReason = "필수 컬럼(기준년월/사용량)을 찾을 수 없거나 유효한 행이 없습니다";
            failureRecorder.recordFailure(
                    dataName, dataName.equals(ELECTRIC_USAGE_NAME) ? ELECTRIC_USAGE_SOURCE_ID : BUILDING_ENERGY_SOURCE_ID,
                    file, sourceUpdatedAt, csv.rows().size(), failedRows, failureReason);
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT, failureReason);
        }
    }

    private ExternalDataImportHistory saveHistory(String dataName, String sourceId, MultipartFile file,
                                                  LocalDate sourceUpdatedAt, int total, int success, int failed,
                                                  String failureReason) {
        ExternalImportStatus status = failureReason != null ? ExternalImportStatus.FAILED
                : failed > 0 ? ExternalImportStatus.PARTIAL_SUCCESS
                : ExternalImportStatus.SUCCESS;
        ExternalDataImportHistory history = importHistoryRepository.save(ExternalDataImportHistory.record(
                dataName, sourceId, file.getOriginalFilename(), sourceUpdatedAt,
                status, total, success, failed, failureReason));
        log.info("[EXTERNAL-IMPORT] {} 적재 완료: total={}, success={}, failed={}", dataName, total, success, failed);
        return history;
    }

    private CsvContent parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT, "업로드된 파일이 없습니다");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT,
                    "CSV 파일만 지원합니다. XLSX는 CSV로 변환 후 업로드해 주세요");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT, "파일에 헤더가 없습니다");
            }
            List<String> headers = splitCsvLine(stripBom(headerLine));
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = splitCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size() && i < cells.size(); i++) {
                    row.put(headers.get(i).trim(), cells.get(i).trim());
                }
                rows.add(row);
            }
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT, "데이터 행이 없습니다");
            }
            return new CsvContent(headers, rows);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_INPUT, "파일을 읽을 수 없습니다");
        }
    }

    // 따옴표 감싼 필드 내 콤마 처리
    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private String stripBom(String line) {
        return line.startsWith("﻿") ? line.substring(1) : line;
    }

    private String value(Map<String, String> row, List<String> candidateKeys) {
        for (String key : candidateKeys) {
            String value = row.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    // 202605 / 2026-05 / 2026.05 → 2026-05
    private String normalizePeriod(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 6) {
            return null;
        }
        return digits.substring(0, 4) + "-" + digits.substring(4, 6);
    }

    private BigDecimal decimal(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longValue(String raw) {
        BigDecimal value = decimal(raw);
        return value != null ? value.longValue() : null;
    }

    private record CsvContent(List<String> headers, List<Map<String, String>> rows) {
    }

    private static class Aggregate {
        final String period;
        final String sido;
        final String sigungu;
        final String dongCode;
        final String dongName;
        BigDecimal usageKwh = BigDecimal.ZERO;
        long buildingCount = 0;

        Aggregate(String period, String sido, String sigungu, String dongCode, String dongName) {
            this.period = period;
            this.sido = sido;
            this.sigungu = sigungu;
            this.dongCode = dongCode;
            this.dongName = dongName;
        }

        String period() {
            return period;
        }
    }
}

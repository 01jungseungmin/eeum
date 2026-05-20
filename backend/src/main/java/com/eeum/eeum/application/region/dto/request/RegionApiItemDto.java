package com.eeum.eeum.application.region.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegionApiItemDto {
    @JsonProperty("ctpv_cd")
    private String ctpvCd;

    @JsonProperty("ctpv_nm")
    private String ctpvNm;

    @JsonProperty("sgg_cd")
    private String sggCd;

    @JsonProperty("sgg_nm")
    private String sggNm;

    @JsonProperty("emd_cd")
    private String emdCd;

    @JsonProperty("emd_nm")
    private String emdNm;

    @JsonProperty("rgn_se")
    private String rgnSe;

    @JsonProperty("use_yn")
    private String useYn;
}

package com.eeum.eeum.application.region.dto.response;

import com.eeum.eeum.application.region.dto.request.RegionApiItemDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RegionApiResponseDto {
    @JsonProperty("Response")
    private Response response;

    @Getter
    @NoArgsConstructor
    public static class Response {

        private Body body;
    }

    @Getter
    @NoArgsConstructor
    public static class Body {
        private Items items;

        private Integer pageNo;

        private Integer numOfRows;

        private Integer totalCount;
    }

    @Getter
    @NoArgsConstructor
    public static class Items {

        private List<RegionApiItemDto> item;
    }
}

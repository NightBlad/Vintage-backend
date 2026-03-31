package com.example.vintage.dto.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DifyMessagesResponse {

    private Integer limit;
    private Boolean has_more;
    private List<DifyMessageItem> data;

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Boolean getHas_more() {
        return has_more;
    }

    public void setHas_more(Boolean has_more) {
        this.has_more = has_more;
    }

    public List<DifyMessageItem> getData() {
        return data;
    }

    public void setData(List<DifyMessageItem> data) {
        this.data = data;
    }
}


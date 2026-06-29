package com.divakarchowdary.pom.model;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class PRInfo {
    private String prUrl;
    private int prNumber;
    private String author;
}

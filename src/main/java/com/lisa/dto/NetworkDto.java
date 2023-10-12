package com.lisa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkDto implements Comparable<NetworkDto> {
	private String email;
    private String referrer;
    private int level;
    
    @Override
    public int compareTo(NetworkDto other) {
        return Integer.compare(this.level, other.level);
    }
}
